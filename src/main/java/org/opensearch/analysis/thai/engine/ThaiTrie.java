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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flat prefix hash-map trie for fast Thai dictionary lookups.
 *
 * <p>Port of the Go {@code ThaiTrie} in thai-break. Maps all prefixes of every
 * stored word to 0.0 and full words to their frequency weight. This lets
 * {@link #prefixes} enumerate all dictionary words that start at a given rune
 * position in O(maxWordLen) hash lookups.
 */
public final class ThaiTrie {

    /** Maps a string key to its weight: >0 for full words, 0 for prefix-only. */
    private final Map<String, Double> prefixMap = new HashMap<>();
    private double maxWeight = 1.0;
    private double totalWeight = 0.0;

    /** Result of a single prefix-match scan. */
    public static final class PrefixMatch {
        /** Rune index where the matched word ends (exclusive). */
        public final int end;
        /** Frequency weight of the matched word. */
        public final double weight;

        public PrefixMatch(int end, double weight) {
            this.end = end;
            this.weight = weight;
        }
    }

    /**
     * Adds a word with its frequency weight. Thread-unsafe; call before any
     * concurrent access.
     */
    public void add(String word, double weight) {
        if (word == null || word.isEmpty()) return;

        if (weight > maxWeight) maxWeight = weight;

        int[] codePoints = word.codePoints().toArray();
        int n = codePoints.length;

        // Register all strict prefixes (weight = 0 = "prefix only")
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n - 1; i++) {
            sb.appendCodePoint(codePoints[i]);
            String prefix = sb.toString();
            prefixMap.putIfAbsent(prefix, 0.0);
        }

        // Register the full word
        double existing = prefixMap.getOrDefault(word, 0.0);
        if (weight > existing) {
            prefixMap.put(word, weight);
            totalWeight += weight - existing;
        } else {
            prefixMap.putIfAbsent(word, weight);
        }
    }

    /** Adds many words at once. */
    public void addAll(Map<String, Double> words) {
        for (Map.Entry<String, Double> e : words.entrySet()) {
            add(e.getKey(), e.getValue());
        }
    }

    /**
     * Returns an independent copy of this trie. Mutating the copy (e.g. via
     * {@link #add}) never affects the original — use this before merging a
     * user dictionary into a trie that may be shared by other callers.
     */
    public ThaiTrie copy() {
        ThaiTrie clone = new ThaiTrie();
        clone.prefixMap.putAll(this.prefixMap);
        clone.maxWeight = this.maxWeight;
        clone.totalWeight = this.totalWeight;
        return clone;
    }

    /** Total weight (sum of all full-word weights); used as the Viterbi normalizer. */
    public double totalWeight() {
        return totalWeight;
    }

    /** Maximum single-word weight stored. */
    public double maxWeight() {
        return maxWeight;
    }

    /**
     * Finds all dictionary words whose start is at rune position {@code start}
     * in {@code runes}, up to {@code maxLen} runes long.
     *
     * @param runes   the full rune array
     * @param start   rune index to start matching from
     * @param maxLen  maximum word length in runes (0 = unlimited)
     * @return list of prefix matches, ordered shortest-to-longest
     */
    public List<PrefixMatch> prefixes(int[] runes, int start, int maxLen) {
        List<PrefixMatch> matches = new ArrayList<>();
        int limit = runes.length;
        if (maxLen > 0 && start + maxLen < limit) {
            limit = start + maxLen;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < limit; i++) {
            sb.appendCodePoint(runes[i]);
            String sub = sb.toString();
            Double w = prefixMap.get(sub);
            if (w == null) break;           // no word or prefix starts here further
            if (w > 0.0) {
                matches.add(new PrefixMatch(i + 1, w));
            }
        }
        return matches;
    }

    /** Number of full words stored. */
    public int size() {
        long count = prefixMap.values().stream().filter(v -> v > 0.0).count();
        return (int) count;
    }

    /**
     * Checks if the exact word exists in the dictionary.
     *
     * @param word word to check
     * @return {@code true} if word exists
     */
    public boolean contains(String word) {
        if (word == null || word.isEmpty()) return false;
        Double w = prefixMap.get(word);
        return w != null && w > 0.0;
    }
}
