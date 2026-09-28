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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast lookup map for Thai acronyms, abbreviations, and their expanded full forms.
 */
public final class ThaiAcronymMap {

    private static final String DEFAULT_ACRONYMS_RESOURCE =
            "/org/opensearch/analysis/thai/acronyms.txt";

    private final Map<String, String> exactMap;
    private final Map<String, String> cleanMap;
    private final Map<String, String> reverseMap;

    private ThaiAcronymMap(Map<String, String> exactMap, Map<String, String> cleanMap, Map<String, String> reverseMap) {
        this.exactMap = exactMap;
        this.cleanMap = cleanMap;
        this.reverseMap = reverseMap;
    }

    private static final class Holder {
        static final ThaiAcronymMap DEFAULT = buildDefault();

        private static ThaiAcronymMap buildDefault() {
            try (InputStream is = ThaiAcronymMap.class.getResourceAsStream(DEFAULT_ACRONYMS_RESOURCE)) {
                if (is == null) {
                    throw new IOException("Bundled acronyms dictionary not found: " + DEFAULT_ACRONYMS_RESOURCE);
                }
                return loadFromStream(is);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load bundled Thai acronyms", e);
            }
        }
    }

    /**
     * Returns the process-wide cached default acronym map.
     */
    public static ThaiAcronymMap loadDefault() {
        return Holder.DEFAULT;
    }

    /**
     * Loads an acronym map from an {@link InputStream} (UTF-8 TSV format).
     *
     * @param stream input stream
     * @return loaded {@link ThaiAcronymMap}
     * @throws IOException on read failure
     */
    public static ThaiAcronymMap loadFromStream(InputStream stream) throws IOException {
        Map<String, String> exact = new HashMap<>();
        Map<String, String> clean = new HashMap<>();
        Map<String, String> reverse = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", 2);
                if (parts.length >= 2) {
                    String acronym = parts[0].strip();
                    String fullWord = parts[1].strip();
                    if (!acronym.isEmpty() && !fullWord.isEmpty()) {
                        addEntry(exact, clean, reverse, acronym, fullWord);
                    }
                }
            }
        }
        return new ThaiAcronymMap(
                Collections.unmodifiableMap(exact),
                Collections.unmodifiableMap(clean),
                Collections.unmodifiableMap(reverse)
        );
    }

    /**
     * Builds an acronym map by merging custom rules on top of an existing base map.
     *
     * @param base  base acronym map (e.g. {@link #loadDefault()})
     * @param rules list of custom rules (either "acronym\tfull" or "acronym=>full")
     * @return new merged {@link ThaiAcronymMap}
     */
    public static ThaiAcronymMap mergeWithRules(ThaiAcronymMap base, List<String> rules) {
        Map<String, String> exact = new HashMap<>(base.exactMap);
        Map<String, String> clean = new HashMap<>(base.cleanMap);
        Map<String, String> reverse = new HashMap<>(base.reverseMap);

        if (rules != null) {
            for (String rule : rules) {
                if (rule == null) continue;
                String r = rule.strip();
                if (r.isEmpty() || r.startsWith("#")) continue;
                String acronym = null;
                String fullWord = null;
                if (r.contains("=>")) {
                    String[] parts = r.split("=>", 2);
                    acronym = parts[0].strip();
                    fullWord = parts[1].strip();
                } else if (r.contains("\t")) {
                    String[] parts = r.split("\t", 2);
                    acronym = parts[0].strip();
                    fullWord = parts[1].strip();
                }
                if (acronym != null && fullWord != null && !acronym.isEmpty() && !fullWord.isEmpty()) {
                    addEntry(exact, clean, reverse, acronym, fullWord);
                }
            }
        }

        return new ThaiAcronymMap(
                Collections.unmodifiableMap(exact),
                Collections.unmodifiableMap(clean),
                Collections.unmodifiableMap(reverse)
        );
    }

    private static void addEntry(
            Map<String, String> exact,
            Map<String, String> clean,
            Map<String, String> reverse,
            String acronym,
            String fullWord
    ) {
        exact.put(acronym, fullWord);
        String stripped = stripDots(acronym);
        if (!stripped.isEmpty()) {
            clean.put(stripped, fullWord);
        }
        reverse.putIfAbsent(fullWord, acronym);
    }

    private static String stripDots(String s) {
        return s.replace(".", "").replace("ฯ", "");
    }

    /**
     * Expands an acronym into its full word representation.
     *
     * @param term          input token
     * @param normalizeDots whether to match both with and without dots
     * @return expanded full word, or {@code null} if not found
     */
    public String expand(String term, boolean normalizeDots) {
        if (term == null || term.isEmpty()) return null;
        String match = exactMap.get(term);
        if (match != null) return match;

        if (normalizeDots) {
            String stripped = stripDots(term);
            return cleanMap.get(stripped);
        }
        return null;
    }

    /**
     * Reversely looks up the acronym of a full word.
     *
     * @param fullWord full phrase / word
     * @return corresponding acronym, or {@code null} if not found
     */
    public String abbreviate(String fullWord) {
        if (fullWord == null || fullWord.isEmpty()) return null;
        return reverseMap.get(fullWord);
    }

    /** Number of exact acronym entries stored. */
    public int size() {
        return exactMap.size();
    }
}
