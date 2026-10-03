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

/**
 * Loader for the bundled Thai dictionary and for user-supplied dictionaries.
 *
 * <p>The default dictionary is the {@code words.txt} resource bundled inside
 * this JAR (51,347 words with category weights, Apache-2.0, from the {@code thai-break}
 * and {@code thai-break-dict-extra} projects). User dictionaries follow the same
 * TSV format: one word per line, with an optional tab-separated weight.
 * Words are stored as the tokenizer matches them
 * ({@link ThaiViterbiTokenizer#normalizeForMatching(String)}), e.g. {@code นํ้า} as {@code น้ำ}.
 */
public final class ThaiDictionaryLoader {

    /** Classpath location of the bundled binary DAWG. */
    static final String DEFAULT_DAWG_RESOURCE = "/org/opensearch/analysis/thai/words.dawg";

    /** Classpath location of the bundled word list. */
    static final String DEFAULT_DICT_RESOURCE = "/org/opensearch/analysis/thai/words.txt";

    private ThaiDictionaryLoader() {}

    /** Lazily parses the bundled dictionary once per JVM/classloader (static holder idiom). */
    private static final class Holder {
        static final ThaiTrie DEFAULT = build();

        private static ThaiTrie build() {
            // 1. Try loading binary DAWG first (faster, sub-millisecond, minimal memory)
            try (InputStream is = ThaiDictionaryLoader.class.getResourceAsStream(DEFAULT_DAWG_RESOURCE)) {
                if (is != null) {
                    CompactDawg dawg = CompactDawg.loadFromStream(is);
                    java.util.Map<String, Double> weights = loadCustomWeights(DEFAULT_DICT_RESOURCE);
                    return new ThaiTrie(dawg, weights);
                }
            } catch (Exception ignored) {
                // Fall back to TSV text parsing if DAWG binary reading fails
            }

            // 2. Fallback to bundled TSV dictionary
            try (InputStream is = ThaiDictionaryLoader.class.getResourceAsStream(DEFAULT_DICT_RESOURCE)) {
                if (is == null) {
                    throw new IOException("Bundled Thai dictionary not found: " + DEFAULT_DICT_RESOURCE);
                }
                return loadFromStream(is, new ThaiTrie());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load bundled Thai dictionary", e);
            }
        }

        private static java.util.Map<String, Double> loadCustomWeights(String resourcePath) {
            java.util.Map<String, Double> weights = new java.util.HashMap<>();
            try (InputStream is = ThaiDictionaryLoader.class.getResourceAsStream(resourcePath)) {
                if (is == null) return weights;
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.strip();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int tabIdx = line.indexOf('\t');
                    if (tabIdx > 0) {
                        String word = line.substring(0, tabIdx).strip();
                        String weightStr = line.substring(tabIdx + 1).strip();
                        try {
                            double w = Double.parseDouble(weightStr);
                            if (w > 0) {
                                weights.put(word, w);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (IOException ignored) {}
            return weights;
        }
    }

    /**
     * Returns the bundled default dictionary as a shared, process-wide {@link ThaiTrie}.
     *
     * <p>Parsed once and cached: repeated calls return the <em>same instance</em>.
     * Callers must treat it as read-only — call {@link ThaiTrie#copy()} first if
     * words need to be added (e.g. for a per-index {@code user_dictionary}),
     * otherwise those additions would leak into every other caller sharing this
     * instance.
     */
    public static ThaiTrie loadDefault() {
        return Holder.DEFAULT;
    }

    /**
     * Loads additional words from {@code stream} into an existing (or new) trie.
     * Lines starting with {@code #} and blank lines are ignored.
     * Each line is: {@code word} or {@code word\tweight}.
     *
     * @param stream  dictionary input stream (UTF-8)
     * @param trie    target trie to populate (may already contain words)
     * @return the same trie instance
     * @throws IOException on read errors
     */
    public static ThaiTrie loadFromStream(InputStream stream, ThaiTrie trie) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\t", 2);
            String word = parts[0].strip();
            if (word.isEmpty()) continue;
            double weight = 1.0;
            if (parts.length >= 2) {
                try {
                    double w = Double.parseDouble(parts[1].strip());
                    if (w > 0) weight = w;
                } catch (NumberFormatException ignored) { /* keep default */ }
            }
            trie.add(ThaiViterbiTokenizer.normalizeForMatching(word), weight);
        }
        return trie;
    }

    /**
     * Loads additional words from an iterable of lines into an existing (or new) trie.
     * Useful for inline {@code user_dictionary_rules}.
     * Lines starting with {@code #} and blank lines are ignored.
     * Each line is: {@code word} or {@code word\tweight}.
     *
     * @param lines  iterable of dictionary lines
     * @param trie   target trie to populate
     * @return the same trie instance
     */
    public static ThaiTrie loadFromLines(Iterable<String> lines, ThaiTrie trie) {
        if (lines == null) return trie;
        for (String line : lines) {
            if (line == null) continue;
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\t", 2);
            String word = parts[0].strip();
            if (word.isEmpty()) continue;
            double weight = 1.0;
            if (parts.length >= 2) {
                try {
                    double w = Double.parseDouble(parts[1].strip());
                    if (w > 0) weight = w;
                } catch (NumberFormatException ignored) { /* keep default */ }
            }
            trie.add(ThaiViterbiTokenizer.normalizeForMatching(word), weight);
        }
        return trie;
    }
}
