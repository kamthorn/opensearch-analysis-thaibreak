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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * High-performance Thai Romanization engine providing Royal Thai General System
 * of Transcription (RTGS) transliteration with dictionary lookup fallback.
 *
 * <p>Supports:
 * <ul>
 *   <li>Fast O(1) dictionary lookup for common Thai words, 77 provinces, landmarks, and irregular spellings.</li>
 *   <li>Algorithmic RTGS transliteration based on the Royal Institute of Thailand standard for arbitrary tokens.</li>
 *   <li>Custom user-defined romanization mappings.</li>
 * </ul>
 */
public final class ThaiRomanizer {

    private static final String DEFAULT_ROMANIZATIONS_RESOURCE =
            "/org/opensearch/analysis/thai/romanizations.txt";

    private static final String THAI_CONSONANTS = "กขฃคฅฆงจฉชซฌญฎฏฐฑฒณดตถทธนบปผฝพฟภมยรลวศษสหฬอฮ";

    private static final class VowelRule {
        final Pattern pattern;
        final String replacement;

        VowelRule(String patternStr, String replacement) {
            this.pattern = Pattern.compile(patternStr);
            this.replacement = replacement;
        }
    }

    private static final List<VowelRule> VOWEL_RULES = new ArrayList<>();
    private static final Map<Character, String[]> CONSONANTS = new HashMap<>();
    private static final Pattern RE_NORMALIZE;
    private static final Set<Character> CLUSTER_SECOND = Set.of('ร', 'ล', 'ว');

    static {
        String raw = """
เ*ียว,$1iao
แ*็ว,$1aeo
เ*ือย,$1ueai
แ*ว,$1aeo
เ*็ว,$1eo
เ*ว,$1eo
*ิว,$1io
*วย,$1uai
เ*ย,$1oei
*อย,$1oi
โ*ย,$1oi
*ุย,$1ui
*าย,$1ai
ไ*ย,$1ai
*ัย,$1ai
ไ**,$1$2ai
ไ*,$1ai
ใ*,$1ai
*ว*,$1ua$2
*ัวะ,$1ua
*ัว,$1ua
เ*ือะ,$1uea
เ*ือ,$1uea
เ*ียะ,$1ia
เ*ีย,$1ia
เ*อะ,$1oe
เ*อ,$1oe
เ*ิ,$1oe
*อ,$1o
เ*าะ,$1o
เ*็,$1e
โ*ะ,$1o
โ*,$1o
แ*ะ,$1ae
แ*,$1ae
เ*าะ,$1e
*าว,$1ao
เ*า,$1ao
เ*,$1e
*ู,$1u
*ุ,$1u
*ื,$1ue
*ึ,$1ue
*ี,$1i
*ิ,$1i
*ำ,$1am
*า,$1a
*ั,$1a
*ะ,$1a
#ฤ,$1rue
$ฤ,$1ri
""";
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            String pat = parts[0]
                    .replace("*", "([" + THAI_CONSONANTS + "])")
                    .replace("#", "([คนพมห])")
                    .replace("$", "([กตทปศส])");
            VOWEL_RULES.add(new VowelRule(pat, parts[1]));
        }

        addCons("ก", "k", "k");
        addCons("ข", "kh", "k"); addCons("ฃ", "kh", "k"); addCons("ค", "kh", "k"); addCons("ฅ", "kh", "k"); addCons("ฆ", "kh", "k");
        addCons("ง", "ng", "ng");
        addCons("จ", "ch", "t"); addCons("ฉ", "ch", "t"); addCons("ช", "ch", "t"); addCons("ฌ", "ch", "t");
        addCons("ซ", "s", "t"); addCons("ศ", "s", "t"); addCons("ษ", "s", "t"); addCons("ส", "s", "t");
        addCons("ญ", "y", "n");
        addCons("ฎ", "d", "t"); addCons("ด", "d", "t");
        addCons("ฏ", "t", "t"); addCons("ต", "t", "t");
        addCons("ฐ", "th", "t"); addCons("ฑ", "th", "t"); addCons("ฒ", "th", "t");
        addCons("ถ", "th", "t"); addCons("ท", "th", "t"); addCons("ธ", "th", "t");
        addCons("ณ", "n", "n"); addCons("น", "n", "n");
        addCons("บ", "b", "p");
        addCons("ป", "p", "p");
        addCons("ผ", "ph", "p"); addCons("พ", "ph", "p"); addCons("ภ", "ph", "p");
        addCons("ฝ", "f", "p"); addCons("ฟ", "f", "p");
        addCons("ม", "m", "m");
        addCons("ย", "y", "");
        addCons("ร", "r", "n");
        addCons("ฤ", "rue", "");
        addCons("ล", "l", "n"); addCons("ฬ", "l", "n");
        addCons("ว", "w", "");
        addCons("ห", "h", ""); addCons("ฮ", "h", "");
        addCons("อ", "", "");

        RE_NORMALIZE = Pattern.compile("จน์|มณ์|ณฑ์|ทร์|ตร์|[" + THAI_CONSONANTS + "]\u0E4C|[" + THAI_CONSONANTS + "][\u0E30-\u0E39]\u0E4C|[\u0E2F\u0E46\u0E48-\u0E4F\u0E5A\u0E5B]");
    }

    private static void addCons(String c, String init, String fin) {
        CONSONANTS.put(c.charAt(0), new String[]{init, fin});
    }

    private final Map<String, List<String>> dictionary;

    /**
     * Constructs a {@link ThaiRomanizer} with the given dictionary map.
     *
     * @param dictionary mapping from Thai word to list of romanized synonyms
     */
    public ThaiRomanizer(Map<String, List<String>> dictionary) {
        this.dictionary = dictionary != null ? dictionary : Collections.emptyMap();
    }

    private static final class Holder {
        static final ThaiRomanizer DEFAULT = buildDefault();

        private static ThaiRomanizer buildDefault() {
            try (InputStream is = ThaiRomanizer.class.getResourceAsStream(DEFAULT_ROMANIZATIONS_RESOURCE)) {
                if (is == null) {
                    throw new IOException("Bundled romanizations dictionary not found: " + DEFAULT_ROMANIZATIONS_RESOURCE);
                }
                return loadFromStream(is);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load bundled Thai romanizations", e);
            }
        }
    }

    /**
     * Returns the process-wide cached default romanizer instance.
     *
     * @return default {@link ThaiRomanizer}
     */
    public static ThaiRomanizer loadDefault() {
        return Holder.DEFAULT;
    }

    /**
     * Loads a romanizer from an {@link InputStream} (UTF-8 TSV format).
     *
     * @param stream input stream
     * @return loaded {@link ThaiRomanizer}
     * @throws IOException on read failure
     */
    public static ThaiRomanizer loadFromStream(InputStream stream) throws IOException {
        Map<String, List<String>> dict = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", 2);
                if (parts.length >= 2) {
                    String thai = parts[0].strip();
                    String rom = parts[1].strip();
                    if (!thai.isEmpty() && !rom.isEmpty()) {
                        String[] targets = rom.split(",");
                        List<String> list = new ArrayList<>();
                        for (String t : targets) {
                            String trimmed = t.strip().toLowerCase(Locale.ROOT);
                            if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                                list.add(trimmed);
                            }
                        }
                        dict.put(thai, Collections.unmodifiableList(list));
                    }
                }
            }
        }
        return new ThaiRomanizer(Collections.unmodifiableMap(dict));
    }

    /**
     * Creates a new romanizer combining custom rules with a fallback romanizer.
     *
     * @param rules    inline rules in format {@code "thai=>roman1,roman2"}
     * @param fallback base romanizer
     * @return combined {@link ThaiRomanizer}
     */
    public static ThaiRomanizer fromRules(List<String> rules, ThaiRomanizer fallback) {
        Map<String, List<String>> dict = new HashMap<>();
        if (fallback != null && fallback.dictionary != null) {
            dict.putAll(fallback.dictionary);
        }
        if (rules != null) {
            for (String rule : rules) {
                if (rule == null) continue;
                String[] parts = rule.split("=>", 2);
                if (parts.length == 2) {
                    String thai = parts[0].strip();
                    String rom = parts[1].strip();
                    if (!thai.isEmpty() && !rom.isEmpty()) {
                        String[] targets = rom.split(",");
                        List<String> list = new ArrayList<>();
                        for (String t : targets) {
                            String trimmed = t.strip().toLowerCase(Locale.ROOT);
                            if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                                list.add(trimmed);
                            }
                        }
                        dict.put(thai, Collections.unmodifiableList(list));
                    }
                }
            }
        }
        return new ThaiRomanizer(Collections.unmodifiableMap(dict));
    }

    /**
     * Checks if the given character sequence contains any Thai characters.
     *
     * @param s text sequence
     * @return {@code true} if contains Thai script
     */
    public static boolean hasThai(CharSequence s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0E01' && c <= '\u0E5B') {
                return true;
            }
        }
        return false;
    }

    /**
     * Romanizes the given word. Checks the dictionary first; if not found, applies
     * algorithmic RTGS rules.
     *
     * @param word Thai word/token
     * @return list of romanized representations, or empty list if word contains no Thai
     */
    public List<String> romanize(String word) {
        if (word == null || word.isEmpty() || !hasThai(word)) {
            return Collections.emptyList();
        }

        // 1. Dictionary lookup
        List<String> dictMatch = dictionary.get(word);
        if (dictMatch != null && !dictMatch.isEmpty()) {
            return dictMatch;
        }

        // 2. Algorithmic RTGS transliteration
        String algorithmic = romanizeAlgorithm(word);
        if (algorithmic != null && !algorithmic.isEmpty()) {
            return List.of(algorithmic.toLowerCase(Locale.ROOT));
        }

        return Collections.emptyList();
    }

    /**
     * Algorithmic RTGS romanization of a single Thai word or cluster.
     *
     * @param word Thai word
     * @return RTGS transliteration
     */
    public static String romanizeAlgorithm(String word) {
        if (word == null || word.isEmpty()) return "";
        if ("ห".equals(word)) return "";

        String norm = RE_NORMALIZE.matcher(word).replaceAll("");
        for (VowelRule rule : VOWEL_RULES) {
            norm = rule.pattern.matcher(norm).replaceAll(rule.replacement);
        }

        StringBuilder consonants = new StringBuilder();
        for (int i = 0; i < norm.length(); i++) {
            char ch = norm.charAt(i);
            if (CONSONANTS.containsKey(ch)) {
                consonants.append(ch);
            }
        }

        if (norm.length() == 2 && consonants.length() == 2) {
            norm = "" + norm.charAt(0) + "o" + norm.charAt(1);
        }

        return replaceConsonants(norm, consonants.toString());
    }

    private static String replaceConsonants(String word, String consonants) {
        if (consonants.isEmpty()) return word;
        StringBuilder sb = new StringBuilder();
        int j = 0;
        boolean skip = false;
        boolean vowelSeen = false;

        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (skip) {
                skip = false;
                j++;
            } else if (!CONSONANTS.containsKey(ch)) {
                vowelSeen = true;
                sb.append(ch);
            } else if (sb.length() == 0 && ch == 'ห' && consonants.length() != 1) {
                j++;
            } else if (word.substring(i).equals("รร")) {
                skip = true;
                sb.append("an");
                vowelSeen = true;
                j++;
            } else if (i + 2 <= word.length() && word.substring(i, i + 2).equals("รร")) {
                skip = true;
                sb.append("a");
                vowelSeen = true;
                j++;
            } else if (!vowelSeen) {
                boolean hasInitial = false;
                for (int k = 0; k < sb.length(); k++) {
                    char sc = sb.charAt(k);
                    if ("aeiou".indexOf(sc) < 0) {
                        hasInitial = true;
                        break;
                    }
                }
                char cj = consonants.charAt(j);
                String[] cjMap = CONSONANTS.get(cj);
                if (!hasInitial) {
                    if (!cjMap[0].isEmpty()) sb.append(cjMap[0]);
                    j++;
                } else {
                    boolean isCluster = CLUSTER_SECOND.contains(ch);
                    boolean isLast = (i + 1 >= word.length());
                    boolean hasVowelNext = (!isLast && !CONSONANTS.containsKey(word.charAt(i + 1)));

                    if (isCluster && (hasVowelNext || !isLast)) {
                        sb.append(cjMap[0]);
                        j++;
                    } else if (!isCluster && !isLast) {
                        sb.append("a");
                        vowelSeen = true;
                        if (!cjMap[0].isEmpty()) sb.append(cjMap[0]);
                        vowelSeen = false;
                        j++;
                    } else if (hasVowelNext) {
                        sb.append(cjMap[0]);
                        j++;
                    } else {
                        sb.append("o");
                        sb.append(cjMap[1]);
                        vowelSeen = true;
                        j++;
                    }
                }
            } else {
                boolean hasVowelNext = (i + 1 < word.length() && !CONSONANTS.containsKey(word.charAt(i + 1)));
                char cj = consonants.charAt(j);
                String[] cjMap = CONSONANTS.get(cj);
                if (hasVowelNext) {
                    sb.append(cjMap[0]);
                    vowelSeen = false;
                    j++;
                } else {
                    sb.append(cjMap[1]);
                    j++;
                }
            }
        }
        return sb.toString();
    }
}
