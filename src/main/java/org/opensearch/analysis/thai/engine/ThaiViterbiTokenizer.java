/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.analysis.thai.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Viterbi + TCC Thai word tokenizer.
 *
 * <p>Port of the Go {@code tokenizer.go} in thai-break. Segments Thai text
 * using a Viterbi shortest-path algorithm constrained by TCC valid positions
 * and driven by a weighted dictionary ({@link ThaiTrie}).
 *
 * <p>Algorithm summary:
 * <ol>
 *   <li>Compute valid TCC break positions ({@link ThaiTCC#validPositions}).</li>
 *   <li>Run single-pass Viterbi DP: for each valid position {@code i}, emit all
 *       dictionary words starting at {@code i} as edges, plus non-Thai token
 *       and abbreviation edges.</li>
 *   <li>Any unreachable valid position gets an <em>unknown-word</em> fallback
 *       edge with a higher cost.</li>
 *   <li>Traceback the path and reverse to get forward-ordered tokens.</li>
 *   <li>Consecutive unknown Thai tokens are merged into one OOV chunk.</li>
 * </ol>
 */
public final class ThaiViterbiTokenizer {

    // Cost multipliers (relative to rareCost = -log(1/totalWeight))
    /** Cost of a run of two or more spelled-out letters (เอทีเอ็ม, ยูเอ็น). */
    private static final double LETTER_RUN_COST_FACTOR  = 1.0;
    private static final double ABBR_COST_FACTOR        = 1.5;
    private static final double ABBR_LETTER_COST_FACTOR = 0.01;
    private static final double UNKNOWN_COST_FACTOR      = 2.0;
    private static final double TIE_EPSILON              = 1e-9;

    /**
     * An out-of-vocabulary word may span up to this many TCC clusters. Such an edge competes with
     * the dictionary words, so a long unknown word is kept whole instead of being cut into short words.
     */
    private static final int    OOV_MAX_CLUSTERS         = 6;
    /** Cost of an out-of-vocabulary edge of one cluster, relative to the cost of the rarest word. */
    private static final double OOV_COST_FACTOR          = 3.0;
    /**
     * Extra cost per further cluster of an out-of-vocabulary edge, relative to the cost of the
     * rarest word. Below about 0.5 an unknown word beats real words and F1 drops sharply.
     */
    private static final double OOV_CLUSTER_COST_FACTOR  = 0.8;
    /**
     * Frequent function words. An out-of-vocabulary edge may not start or end with one of them, nor
     * contain one at a cluster boundary, so an unknown word does not swallow its neighbours
     * (ฮิวจ์ส|ไม่|ได้, not ฮิวจ์สไม่ได้; ชวรัตน์|ที่|ลืม|ไว้, not ชวรัตน์ที่ลืมไว้): the same name
     * would otherwise be tokenized differently on its own and next to a function word, and a search
     * for it would miss the document.
     */
    private static final String[] OOV_BOUNDARY_WORDS = {
        "ที่", "และ", "ของ", "ใน", "ได้", "ให้", "ไม่", "ว่า", "เป็น",
        "มี", "จะ", "ไป", "มา", "ก็", "กับ", "แต่", "หรือ", "จาก",
        "โดย", "เพื่อ", "แล้ว", "อยู่", "นี้", "นั้น", "ซึ่ง", "การ", "ความ",
        "ต่อ", "ถึง", "ยัง", "เมื่อ", "ทั้ง", "ตาม", "ด้วย", "อีก", "คือ",
        "ไว้",
        // words that often come right before a name
        "นาย", "นาง", "พี่", "น้อง", "แพทย์", "ตัว",
    };
    /** Bit k of a function-word mask stands for OOV_BOUNDARY_WORDS[k]. */
    private static final int[][] FW_RUNES = Arrays.stream(OOV_BOUNDARY_WORDS)
        .map(f -> f.codePoints().toArray())
        .toArray(int[][]::new);
    /** Length of each function word in UTF-16 code units (Thai words are in the BMP). */
    private static final int[] FW_UTF16_LEN = Arrays.stream(OOV_BOUNDARY_WORDS).mapToInt(String::length).toArray();
    /**
     * The mask of the function words that may not occur inside an out-of-vocabulary word. มา and มี
     * may: they are frequent syllables of transliterated names (อาห์มาดิเนจัด, อะมีรุ้ลฮัจย์).
     */
    private static final long FW_INNER = innerMask();
    /** Indexes of the function words by first code point, as an offset from U+0E00. */
    private static final int[][] FW_BY_FIRST = byFirst();

    private static long innerMask() {
        long mask = 0;
        for (int k = 0; k < OOV_BOUNDARY_WORDS.length; k++) {
            String f = OOV_BOUNDARY_WORDS[k];
            if (!f.equals("มา") && !f.equals("มี")) mask |= 1L << k;
        }
        return mask;
    }

    private static int[][] byFirst() {
        int[][] out = new int[0x80][0];
        for (int k = 0; k < FW_RUNES.length; k++) {
            int c = FW_RUNES[k][0] - 0x0E00;
            out[c] = Arrays.copyOf(out[c], out[c].length + 1);
            out[c][out[c].length - 1] = k;
        }
        return out;
    }

    private static final Pattern PAT_NON_THAI = Pattern.compile(
        "^(?:[a-zA-Z]+(?:[-_'][a-zA-Z0-9]+)*|\\d+(?:,\\d+)*(?:\\.\\d+)?%?|[ \\t]+|\\r?\\n|[^\\x{0E00}-\\x{0E7F}a-zA-Z0-9 \\t\\r\\n])"
    );
    /**
     * Names of the letters of the Latin alphabet in Thai, as code points. A name that starts another
     * one comes first (เอ็ม before เอ), so the longest name wins.
     */
    private static final int[][] LETTER_NAMES = Arrays.stream(new String[] {
        "ดับเบิลยู", "เอ็กซ์", "แอล", "เอ็ม", "เอ็น", "เอฟ", "เอช", "เอส", "อาร์", "คิว", "แซด", "ซี", "ดี", "จี", "ไอ", "เจ", "เค",
        "โอ", "พี", "ยู", "วี", "วาย", "บี", "ที", "อี", "เอ",
    }).map(n -> n.codePoints().toArray()).toArray(int[][]::new);

    /** LETTER_NAMES by first code point, as an offset from U+0E00, in LETTER_NAMES order. */
    private static final int[][][] LETTER_NAMES_BY_FIRST = letterNamesByFirst();

    private static int[][][] letterNamesByFirst() {
        int[][][] out = new int[0x80][0][];
        for (int[] name : LETTER_NAMES) {
            int c = name[0] - 0x0E00;
            out[c] = Arrays.copyOf(out[c], out[c].length + 1);
            out[c][out[c].length - 1] = name;
        }
        return out;
    }

    private static final Pattern PAT_ABBR = Pattern.compile(
        "^(?:(?:[เแโใไ]?[ก-ฮ][ัิีึืุู็่้๊๋]?|[ก-ฮ]{1,4})\\.)++"
    );

    /**
     * Closed-class function words that never constitute a meaningful
     * decompound part on their own. A decomposition producing any of these
     * (e.g. กรมการ into กรม|การ) is rejected: it would flood the index with
     * ultra-frequent synonym tokens.
     */
    private static final java.util.Set<String> FUNCTION_PARTS = java.util.Set.of(
        "การ", "ความ", "ที่", "ใน", "ของ", "ไป", "มา", "และ", "หรือ",
        "แต่", "ถ้า", "เพราะ", "เมื่อ", "โดย", "จาก", "ถึง", "เพื่อ",
        "สำหรับ", "ซึ่ง", "อัน", "ผู้", "จะ", "ไม่", "ได้", "ให้",
        "กับ", "ว่า", "นี้", "แล้ว", "กัน", "ด้วย", "เลย", "ก็", "จึง"
    );

    /**
     * Single-morpheme words that must never be decompounded, loaded from the
     * {@code atomic-words.txt} resource. These split into dictionary words by
     * coincidence (e.g. ตาราง into ตา|ราง) and no length rule can tell them
     * apart from true compounds (e.g. คนไข้ into คน|ไข้), so they are curated.
     */
    private static final java.util.Set<String> ATOMIC_WORDS = loadAtomicWords();

    private static java.util.Set<String> loadAtomicWords() {
        java.util.Set<String> words = new java.util.HashSet<>();
        try (java.io.InputStream is = ThaiViterbiTokenizer.class.getResourceAsStream(
                "/org/opensearch/analysis/thai/atomic-words.txt")) {
            if (is == null) return java.util.Set.of();
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) words.add(line);
            }
        } catch (java.io.IOException e) {
            // Missing resource: behave as if the list were empty.
        }
        return java.util.Collections.unmodifiableSet(words);
    }

    private final ThaiTrie trie;

    /**
     * Creates a tokenizer backed by {@code trie}.
     */
    public ThaiViterbiTokenizer(ThaiTrie trie) {
        this.trie = trie;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Tokenizes {@code text} into word tokens, optionally retaining whitespace.
     *
     * @param text           input text (may be mixed Thai/non-Thai)
     * @param keepWhitespace if {@code false}, pure-whitespace tokens are dropped
     * @return list of word tokens in document order
     */
    public List<String> tokenize(String text, boolean keepWhitespace) {
        if (text == null || text.isEmpty()) return List.of();

        // Normalize for matching while tracking original positions
        int[] originalCPs = text.codePoints().toArray();
        NormResult norm = normalizeForMatching(originalCPs);

        List<String> tokens = segment(norm.runes);

        // If normalization changed anything, map back to original characters
        if (!Arrays.equals(norm.runes, originalCPs)) {
            List<String> original = new ArrayList<>(tokens.size());
            int pos = 0;
            for (String tok : tokens) {
                int cpLen = tok.codePointCount(0, tok.length());
                int end = pos + cpLen;
                int startOrig = norm.origPos[pos];
                int endOrig   = norm.origPos[end];
                original.add(new String(originalCPs, startOrig, endOrig - startOrig));
                pos = end;
            }
            tokens = original;
        }

        if (!keepWhitespace) {
            List<String> filtered = new ArrayList<>(tokens.size());
            for (String t : tokens) {
                if (!t.isBlank()) filtered.add(t);
            }
            return filtered;
        }
        return tokens;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Applies the normalization used for dictionary matching to a single word.
     * Dictionary words must go through this too, or a word stored as {@code นํ้าตาล}
     * could never match: the input text is matched as {@code น้ำตาล}.
     *
     * @param word word to normalize
     * @return the word as the tokenizer matches it
     */
    public static String normalizeForMatching(String word) {
        int[] runes = word.codePoints().toArray();
        NormResult norm = normalizeForMatching(runes);
        return Arrays.equals(norm.runes, runes) ? word : new String(norm.runes, 0, norm.runes.length);
    }

    /** Normalization: เ+เ → แ, ํ+า → ำ, ํ+tone+า → tone+ำ, ำ+tone → tone+ำ, ํ+า+tone → tone+ำ */
    private static NormResult normalizeForMatching(int[] runes) {
        int n = runes.length;
        int[] norm = new int[n];
        int[] orig = new int[n + 1];
        int ni = 0;
        for (int i = 0; i < n; i++) {
            int r = runes[i];
            int next = (i + 1 < n) ? runes[i + 1] : 0;
            int next2 = (i + 2 < n) ? runes[i + 2] : 0;

            if (r == 'เ' && next == 'เ') {
                norm[ni] = 'แ'; orig[ni] = i; ni++; i++;
            } else if (r == 'ำ' && isToneMark(next)) {
                norm[ni] = next; orig[ni] = i; ni++;
                norm[ni] = 'ำ'; orig[ni] = i; ni++;
                i += 1;
            } else if (r == 0x0E4D && next == 'า' && isToneMark(next2)) {
                norm[ni] = next2; orig[ni] = i; ni++;
                norm[ni] = 'ำ'; orig[ni] = i; ni++;
                i += 2;
            } else if (r == 0x0E4D && next == 'า') {
                norm[ni] = 'ำ'; orig[ni] = i; ni++; i++;
            } else if (r == 0x0E4D && isToneMark(next) && next2 == 'า') {
                norm[ni] = next; orig[ni] = i; ni++;
                norm[ni] = 'ำ'; orig[ni] = i; ni++;
                i += 2;
            } else {
                norm[ni] = r; orig[ni] = i; ni++;
            }
        }
        orig[ni] = n;
        return new NormResult(Arrays.copyOf(norm, ni), Arrays.copyOf(orig, ni + 1));
    }

    private static boolean isToneMark(int r) {
        return r >= '่' && r <= '๋';
    }

    private static final class NormResult {
        final int[] runes;
        final int[] origPos;
        NormResult(int[] runes, int[] origPos) {
            this.runes = runes;
            this.origPos = origPos;
        }
    }

    /**
     * Viterbi DP segmentation. Every edge into j covers {@code runes[from[j]..j)}, so the DP keeps no
     * word and the tokens are cut out at the end.
     */
    private List<String> segment(int[] runes) {
        int n = runes.length;
        if (n == 0) return List.of();

        boolean[] validPos = ThaiTCC.validPositions(runes);

        // Build string + per-rune start indices (for regex on a region of the text)
        StringBuilder sbFull = new StringBuilder();
        int[] runeStart = new int[n + 1]; // runeStart[i] = char offset of rune i
        for (int i = 0; i < n; i++) {
            runeStart[i] = sbFull.length();
            sbFull.appendCodePoint(runes[i]);
        }
        runeStart[n] = sbFull.length();
        String text = sbFull.toString();

        // Bit k of fwStarts[p] is set when OOV_BOUNDARY_WORDS[k] begins at rune p, and bit k of
        // fwEnds[p] when it ends there
        long[] fwStarts = new long[n + 1];
        long[] fwEnds = new long[n + 1];
        for (int p = 0; p < n; p++) {
            int r = runes[p];
            if (r < 0x0E00 || r > 0x0E7F) continue;
            for (int k : FW_BY_FIRST[r - 0x0E00]) {
                int[] f = FW_RUNES[k];
                int e = p + f.length;
                if (e <= n && Arrays.equals(runes, p, e, f, 0, f.length)) {
                    fwStarts[p] |= 1L << k;
                    fwEnds[e] |= 1L << k;
                }
            }
        }

        double normalizer = trie.totalWeight() + 1.0;
        double rareCost   = Math.log(normalizer);

        double[] dp    = new double[n + 1];
        int[]    from  = new int[n + 1];
        boolean[] isUnk = new boolean[n + 1];
        Arrays.fill(dp, Double.POSITIVE_INFINITY);
        Arrays.fill(from, -1);
        dp[0] = 0.0;

        Matcher abbrM = PAT_ABBR.matcher(text);
        Matcher nonThaiM = PAT_NON_THAI.matcher(text);

        for (int i = 0; i <= n; i++) {
            if (!validPos[i]) continue;

            // Unknown-word fallback: find nearest reachable predecessor
            if (Double.isInfinite(dp[i])) {
                for (int k = i - 1; k >= 0; k--) {
                    if (!Double.isInfinite(dp[k]) && validPos[k]) {
                        dp[i]   = dp[k] + UNKNOWN_COST_FACTOR * rareCost;
                        from[i] = k;
                        isUnk[i] = true;
                        break;
                    }
                }
            }

            if (i == n) break;

            // ---- Collect outgoing edges ----
            int rune0 = runes[i];

            if (isThaiRune(rune0)) {
                // 1. Dictionary words (scan window follows the longest entry,
                //    so long user-dictionary words stay matchable)
                for (ThaiTrie.PrefixMatch m : trie.prefixes(runes, i, trie.maxWordLength())) {
                    int j = m.end;
                    if (j <= n && validPos[j]) {
                        double cost = Math.log(normalizer / m.weight);
                        relax(dp, from, isUnk, i, j, cost, false);
                    }
                }

                // 2. Abbreviation patterns (the pattern needs a full stop within four runes)
                boolean dot = false;
                for (int t = i + 1, tEnd = Math.min(i + 4, n - 1); t <= tEnd; t++) {
                    if (runes[t] == '.') {
                        dot = true;
                        break;
                    }
                }
                if (dot && abbrM.region(runeStart[i], text.length()).lookingAt()) {
                    String mStr = abbrM.group();
                    int abbrCPs = mStr.codePointCount(0, mStr.length());
                    int j = i + abbrCPs;
                    if (j <= n && validPos[j] && !abbreviationIsWords(runes, j, mStr)) {
                        int dots   = countChar(mStr, '.');
                        int letters = abbrCPs - dots;
                        double cost = (ABBR_COST_FACTOR + ABBR_LETTER_COST_FACTOR * letters) * rareCost;
                        relax(dp, from, isUnk, i, j, cost, false);
                    }
                }

                // 3. Runs of two or more spelled-out letters (เอทีเอ็ม, ยูเอ็น, ดีเอสไอ)
                for (int end = i, letters = 0; ; ) {
                    int next = 0;
                    if (end < n && runes[end] >= 0x0E00 && runes[end] <= 0x0E7F) {
                        for (int[] name : LETTER_NAMES_BY_FIRST[runes[end] - 0x0E00]) {
                            int e = end + name.length;
                            if (e <= n && validPos[e] && Arrays.equals(runes, end, e, name, 0, name.length)) {
                                next = e;
                                break;
                            }
                        }
                    }
                    if (next == 0) break;
                    end = next;
                    if (++letters >= 2) {
                        relax(dp, from, isUnk, i, end, LETTER_RUN_COST_FACTOR * rareCost, false);
                    }
                }

                // 4. Out-of-vocabulary words of 1..OOV_MAX_CLUSTERS TCC clusters
                int clusters = 0;
                long startsHere = fwStarts[i];
                oov:
                for (int j = i + 1; j <= n && isOovRune(runes[j - 1]); j++) {
                    if (!validPos[j]) continue;
                    if (++clusters > OOV_MAX_CLUSTERS) break;
                    long hits = startsHere | fwEnds[j];
                    if (hits != 0) {
                        // The edge borders a function word that is shorter than the edge
                        int edgeLen = runeStart[j] - runeStart[i];
                        boolean borders = false;
                        for (long h = hits; h != 0; h &= h - 1) {
                            if (edgeLen > FW_UTF16_LEN[Long.numberOfTrailingZeros(h)]) {
                                borders = true;
                                break;
                            }
                        }
                        if (borders) {
                            // Every longer edge from i would contain a function word that ends here
                            for (long h = fwEnds[j] & FW_INNER; h != 0; h &= h - 1) {
                                int len = FW_RUNES[Long.numberOfTrailingZeros(h)].length;
                                if (j - i > len && validPos[j - len]) break oov;
                            }
                            continue;
                        }
                    }
                    double cost = (OOV_COST_FACTOR + OOV_CLUSTER_COST_FACTOR * (clusters - 1)) * rareCost;
                    relax(dp, from, isUnk, i, j, cost, true);
                }
            } else {
                // 5. Non-Thai token
                if (nonThaiM.region(runeStart[i], text.length()).lookingAt()) {
                    String mStr = nonThaiM.group();
                    int j = i + mStr.codePointCount(0, mStr.length());
                    if (j <= n && validPos[j]) {
                        relax(dp, from, isUnk, i, j, rareCost, false);
                    }
                }
            }
        }

        // Fallback: return entire text as one token
        if (Double.isInfinite(dp[n])) {
            return List.of(text);
        }

        // Traceback
        List<int[]> spans = new ArrayList<>();
        int pos = n;
        while (pos > 0) {
            spans.add(new int[] {from[pos], pos, isUnk[pos] ? 1 : 0});
            pos = from[pos];
            if (pos < 0) break;
        }
        Collections.reverse(spans);

        // Merge consecutive unknown Thai tokens into OOV chunks
        List<String> result = new ArrayList<>();
        StringBuilder curChunk = new StringBuilder();
        for (int[] span : spans) {
            String tok = new String(runes, span[0], span[1] - span[0]);
            if (span[2] == 1 && isThaiString(tok)) {
                curChunk.append(tok);
            } else {
                if (curChunk.length() > 0) {
                    result.add(curChunk.toString());
                    curChunk.setLength(0);
                }
                result.add(tok);
            }
        }
        if (curChunk.length() > 0) result.add(curChunk.toString());

        return result;
    }

    private static void relax(double[] dp, int[] from, boolean[] isUnk,
                               int src, int dst, double cost, boolean unk) {
        double newCost = dp[src] + cost;
        if (newCost <= dp[dst] + TIE_EPSILON) {
            dp[dst]   = newCost;
            from[dst] = src;
            isUnk[dst] = unk;
        }
    }

    private static boolean isThaiRune(int r) {
        return r >= 0x0E00 && r <= 0x0E7F;
    }

    /**
     * Whether what the abbreviation pattern matched is really words and a full stop. The pattern
     * accepts any one to four consonant letters before a full stop, and อ ว ย count as consonants,
     * so it matches "ไป." in "ต่อไป." and "ของส." in "ของส.ส.". It is not an abbreviation when it
     * starts with a function word followed by more letters ("ของส.ส." is ของ + ส.ส.), or when it is
     * one dictionary word with a vowel or tone mark and a final full stop that is not followed by a
     * letter ("ต่อไป."), unless the dictionary has the abbreviation itself. A string of consonants only
     * (มทส. นพ.) stays an abbreviation even when the dictionary has it without the full stop.
     *
     * @param end index of the code point after the match
     */
    private boolean abbreviationIsWords(int[] runes, int end, String abbr) {
        String first = abbr.substring(0, abbr.indexOf('.'));
        for (String f : OOV_BOUNDARY_WORDS) {
            if (first.length() > f.length() && first.startsWith(f)) return true;
        }
        boolean nextIsThai = end < runes.length && runes[end] >= 0x0E01 && runes[end] <= 0x0E5B;
        return countChar(abbr, '.') == 1
            && abbr.endsWith(".")
            && first.codePointCount(0, first.length()) >= 2
            && first.codePoints().anyMatch(c -> c < 'ก' || c > 'ฮ')
            && !nextIsThai
            && trie.contains(first)
            && !trie.contains(abbr);
    }

    /**
     * Characters an out-of-vocabulary edge may cover: Thai letters, vowels and marks, but not ๆ, ฯ,
     * digits or other symbols, which are tokens of their own.
     */
    private static boolean isOovRune(int r) {
        return (r >= 0x0E01 && r <= 0x0E2E) || (r >= 0x0E30 && r <= 0x0E3A)
            || (r >= 0x0E40 && r <= 0x0E45) || (r >= 0x0E47 && r <= 0x0E4E);
    }

    private static boolean isThaiString(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.codePoints().allMatch(ThaiViterbiTokenizer::isThaiRune);
    }

    /**
     * Attempts to decompose a compound word into two or more dictionary words.
     *
     * <p>Decomposition is refused (returns {@code null}) when:
     * <ul>
     *   <li>the word is listed in {@code atomic-words.txt} (single morphemes
     *       that split by coincidence, e.g. ตาราง), or</li>
     *   <li>any resulting part is a closed-class function word
     *       (e.g. กรมการ into กรม|การ), which would pollute the index.</li>
     * </ul>
     *
     * @param word the compound word to decompose
     * @return a list of sub-words if the word can be decomposed into two or more
     *         dictionary words; {@code null} if the word is not a compound word.
     */
    public List<String> decompose(String word) {
        if (word == null || word.length() < 3) return null;
        if (ATOMIC_WORDS.contains(word)) return null;
        int[] runes = word.codePoints().toArray();
        int n = runes.length;
        if (n < 3) return null;
        if (!isThaiRune(runes[0])) return null;

        boolean[] validPos = ThaiTCC.validPositions(runes);

        double normalizer = trie.totalWeight() + 1.0;
        double[] dp = new double[n + 1];
        int[] from = new int[n + 1];
        String[] partWord = new String[n + 1];
        Arrays.fill(dp, Double.POSITIVE_INFINITY);
        Arrays.fill(from, -1);
        dp[0] = 0.0;

        for (int i = 0; i < n; i++) {
            if (!validPos[i] || Double.isInfinite(dp[i])) continue;

            for (ThaiTrie.PrefixMatch m : trie.prefixes(runes, i, trie.maxWordLength())) {
                int j = m.end;
                // Disallow the single edge covering the entire word from 0 to n!
                if (i == 0 && j == n) {
                    continue;
                }
                if (j <= n && validPos[j]) {
                    double cost = Math.log(normalizer / m.weight);
                    double newCost = dp[i] + cost;
                    if (newCost < dp[j] - TIE_EPSILON) {
                        dp[j] = newCost;
                        from[j] = i;
                        partWord[j] = new String(runes, i, j - i);
                    }
                }
            }
        }

        if (Double.isInfinite(dp[n])) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        int curr = n;
        while (curr > 0) {
            int prev = from[curr];
            if (prev < 0) return null;
            parts.add(partWord[curr]);
            curr = prev;
        }

        if (parts.size() <= 1) {
            return null;
        }

        java.util.Collections.reverse(parts);

        for (String part : parts) {
            if (FUNCTION_PARTS.contains(part)) {
                return null;
            }
        }
        return parts;
    }

    private static int countChar(String s, char c) {
        int cnt = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) cnt++;
        }
        return cnt;
    }
}
