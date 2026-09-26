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
import java.nio.charset.StandardCharsets;

/**
 * Loader for the bundled Thai dictionary and for user-supplied dictionaries.
 *
 * <p>The default dictionary is the {@code words.txt} resource bundled inside
 * this JAR (25 907 words, Apache-2.0, from the {@code thai-break} project).
 * User dictionaries follow the same TSV format: one word per line, with an
 * optional tab-separated weight.
 */
public final class ThaiDictionaryLoader {

    /** Classpath location of the bundled word list. */
    static final String DEFAULT_DICT_RESOURCE = "/org/opensearch/analysis/thai/words.txt";

    private ThaiDictionaryLoader() {}

    /**
     * Loads the bundled default dictionary into a new {@link ThaiTrie}.
     *
     * @throws IOException if the embedded resource cannot be read
     */
    public static ThaiTrie loadDefault() throws IOException {
        try (InputStream is = ThaiDictionaryLoader.class.getResourceAsStream(DEFAULT_DICT_RESOURCE)) {
            if (is == null) {
                throw new IOException("Bundled Thai dictionary not found: " + DEFAULT_DICT_RESOURCE);
            }
            return loadFromStream(is, new ThaiTrie());
        }
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
            trie.add(word, weight);
        }
        return trie;
    }
}
