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
    private static final double ABBR_COST_FACTOR        = 1.5;
    private static final double ABBR_LETTER_COST_FACTOR = 0.01;
    private static final double UNKNOWN_COST_FACTOR      = 2.0;
    private static final double TIE_EPSILON              = 1e-9;

    private static final Pattern PAT_NON_THAI = Pattern.compile(
        "^(?:[a-zA-Z]+(?:[-_'][a-zA-Z0-9]+)*|\\d+(?:,\\d+)*(?:\\.\\d+)?%?|[ \\t]+|\\r?\\n|[^\\x{0E00}-\\x{0E7F}a-zA-Z0-9 \\t\\r\\n])"
    );
    private static final Pattern PAT_ABBR = Pattern.compile(
        "^(?:(?:[เแโใไ]?[ก-ฮ][ัิีึืุู็่้๊๋]?|[ก-ฮ]{1,4})\\.)++"
    );

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

    /** Normalization: เ+เ → แ, ํ+า → ำ, ํ+tone+า → tone+ำ */
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
            } else if (r == 0x0E4D && next == 'า') {
                norm[ni] = 'ำ'; orig[ni] = i; ni++; i++;
            } else if (r == 0x0E4D && next >= '่' && next <= '๋' && next2 == 'า') {
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

    private static final class NormResult {
        final int[] runes;
        final int[] origPos;
        NormResult(int[] runes, int[] origPos) {
            this.runes = runes;
            this.origPos = origPos;
        }
    }

    /** Viterbi DP segmentation. */
    private List<String> segment(int[] runes) {
        int n = runes.length;
        if (n == 0) return List.of();

        boolean[] validPos = ThaiTCC.validPositions(runes);

        // Build string + per-rune start indices (for regex on substring)
        StringBuilder sbFull = new StringBuilder();
        int[] runeStart = new int[n + 1]; // runeStart[i] = char offset of rune i
        for (int i = 0; i < n; i++) {
            runeStart[i] = sbFull.length();
            sbFull.appendCodePoint(runes[i]);
        }
        runeStart[n] = sbFull.length();
        String text = sbFull.toString();

        double normalizer = trie.totalWeight() + 1.0;
        double rareCost   = Math.log(normalizer);

        double[] dp    = new double[n + 1];
        int[]    from  = new int[n + 1];
        String[] word  = new String[n + 1];
        boolean[] isUnk = new boolean[n + 1];
        Arrays.fill(dp, Double.POSITIVE_INFINITY);
        Arrays.fill(from, -1);
        dp[0] = 0.0;

        for (int i = 0; i <= n; i++) {
            if (!validPos[i]) continue;

            // Unknown-word fallback: find nearest reachable predecessor
            if (Double.isInfinite(dp[i])) {
                for (int k = i - 1; k >= 0; k--) {
                    if (!Double.isInfinite(dp[k]) && validPos[k]) {
                        dp[i]   = dp[k] + UNKNOWN_COST_FACTOR * rareCost;
                        from[i] = k;
                        word[i] = new String(runes, k, i - k);
                        isUnk[i] = true;
                        break;
                    }
                }
            }

            if (i == n) break;

            // ---- Collect outgoing edges ----
            int rune0 = runes[i];
            String subText = text.substring(runeStart[i]);

            if (isThaiRune(rune0)) {
                // 1. Dictionary words
                for (ThaiTrie.PrefixMatch m : trie.prefixes(runes, i, 25)) {
                    int j = m.end;
                    if (j <= n && validPos[j]) {
                        double cost = Math.log(normalizer / m.weight);
                        relax(dp, from, word, isUnk, i, j,
                              new String(runes, i, j - i), cost, false);
                    }
                }

                // 2. Abbreviation patterns
                Matcher abbrM = PAT_ABBR.matcher(subText);
                if (abbrM.find()) {
                    String mStr = abbrM.group();
                    int abbrCPs = mStr.codePointCount(0, mStr.length());
                    int j = i + abbrCPs;
                    if (j <= n && validPos[j]) {
                        int dots   = countChar(mStr, '.');
                        int letters = abbrCPs - dots;
                        double cost = (ABBR_COST_FACTOR + ABBR_LETTER_COST_FACTOR * letters) * rareCost;
                        relax(dp, from, word, isUnk, i, j, mStr, cost, false);
                    }
                }
            } else {
                // 3. Non-Thai token
                Matcher nonThaiM = PAT_NON_THAI.matcher(subText);
                if (nonThaiM.find()) {
                    String mStr = nonThaiM.group();
                    int j = i + mStr.codePointCount(0, mStr.length());
                    if (j <= n && validPos[j]) {
                        relax(dp, from, word, isUnk, i, j, mStr, rareCost, false);
                    }
                }
            }
        }

        // Fallback: return entire text as one token
        if (Double.isInfinite(dp[n])) {
            return List.of(text);
        }

        // Traceback
        List<String> rawTokens = new ArrayList<>();
        List<Boolean> rawIsUnk = new ArrayList<>();
        int pos = n;
        while (pos > 0) {
            rawTokens.add(word[pos]);
            rawIsUnk.add(isUnk[pos]);
            pos = from[pos];
            if (pos < 0) break;
        }

        // Reverse
        int lo = 0, hi = rawTokens.size() - 1;
        while (lo < hi) {
            String tmp = rawTokens.get(lo); rawTokens.set(lo, rawTokens.get(hi)); rawTokens.set(hi, tmp);
            boolean bmp = rawIsUnk.get(lo); rawIsUnk.set(lo, rawIsUnk.get(hi)); rawIsUnk.set(hi, bmp);
            lo++; hi--;
        }

        // Merge consecutive unknown Thai tokens into OOV chunks
        List<String> result = new ArrayList<>();
        StringBuilder curChunk = new StringBuilder();
        for (int idx = 0; idx < rawTokens.size(); idx++) {
            String tok = rawTokens.get(idx);
            if (rawIsUnk.get(idx) && isThaiString(tok)) {
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

    private static void relax(double[] dp, int[] from, String[] word, boolean[] isUnk,
                               int src, int dst, String w, double cost, boolean unk) {
        double newCost = dp[src] + cost;
        if (newCost <= dp[dst] + TIE_EPSILON) {
            dp[dst]   = newCost;
            from[dst] = src;
            word[dst] = w;
            isUnk[dst] = unk;
        }
    }

    private static boolean isThaiRune(int r) {
        return r >= 0x0E00 && r <= 0x0E7F;
    }

    private static boolean isThaiString(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.codePoints().allMatch(ThaiViterbiTokenizer::isThaiRune);
    }

    private static int countChar(String s, char c) {
        int cnt = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) cnt++;
        }
        return cnt;
    }
}
