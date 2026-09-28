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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prefix trie / minimal automaton for fast Thai dictionary lookups.
 *
 * <p>Supports an ultra-compact binary {@link CompactDawg} as the read-only base
 * dictionary (zero memory overhead, instant load), backed by an overlay map
 * for user-supplied custom words. Falls back to a flat prefix hash-map when DAWG
 * is not supplied.
 */
public final class ThaiTrie {

    private final CompactDawg dawg;
    private final Map<String, Double> customWeights;
    private final Map<String, Double> overlay;

    /** Fallback flat hash-map for standalone usage without DAWG. */
    private final Map<String, Double> prefixMap;

    private double maxWeight = 1.0;
    private double totalWeight = 0.0;

    /**
     * Longest dictionary word in code points. Drives the prefix-scan window
     * so arbitrarily long entries (e.g. a 44-char office name from a user
     * dictionary, or 53-char bundled names) stay matchable. Zero means the
     * trie holds no words yet (callers then scan unbounded, which is safe
     * because scans stop at the first dead trie transition).
     */
    private int maxWordLength = 0;

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
     * Constructs a fallback flat Trie without DAWG backing.
     */
    public ThaiTrie() {
        this.dawg = null;
        this.customWeights = Collections.emptyMap();
        this.overlay = null;
        this.prefixMap = new HashMap<>();
        this.maxWeight = 1.0;
        this.totalWeight = 0.0;
    }

    /**
     * Constructs a {@link ThaiTrie} backed by a {@link CompactDawg} minimal automaton
     * and optional custom word frequency weights.
     *
     * @param dawg          read-only binary DAWG
     * @param customWeights map of word to custom weight (words with weight 1.0 may be omitted)
     */
    public ThaiTrie(CompactDawg dawg, Map<String, Double> customWeights) {
        this.dawg = dawg;
        this.customWeights = (customWeights != null && !customWeights.isEmpty())
                ? Collections.unmodifiableMap(new HashMap<>(customWeights))
                : Collections.emptyMap();
        this.overlay = new HashMap<>();
        this.prefixMap = Collections.emptyMap();

        double tot = dawg.numWords();
        double maxW = 1.0;
        for (Double w : this.customWeights.values()) {
            tot += (w - 1.0);
            if (w > maxW) maxW = w;
        }
        this.totalWeight = tot;
        this.maxWeight = maxW;
        int maxLen = 0;
        for (String w : this.customWeights.keySet()) {
            int len = w.codePointCount(0, w.length());
            if (len > maxLen) maxLen = len;
        }
        this.maxWordLength = maxLen;
    }

    private ThaiTrie(CompactDawg dawg, Map<String, Double> customWeights,
                     Map<String, Double> overlay, double maxWeight, double totalWeight) {
        this.dawg = dawg;
        this.customWeights = customWeights;
        this.overlay = overlay;
        this.prefixMap = Collections.emptyMap();
        this.maxWeight = maxWeight;
        this.totalWeight = totalWeight;
        this.maxWordLength = 0;
        for (String w : customWeights.keySet()) {
            int len = w.codePointCount(0, w.length());
            if (len > maxWordLength) maxWordLength = len;
        }
        if (overlay != null) {
            for (Map.Entry<String, Double> e : overlay.entrySet()) {
                if (e.getValue() > 0.0) {
                    int len = e.getKey().codePointCount(0, e.getKey().length());
                    if (len > maxWordLength) maxWordLength = len;
                }
            }
        }
    }

    private ThaiTrie(Map<String, Double> prefixMap, double maxWeight, double totalWeight) {
        this.dawg = null;
        this.customWeights = Collections.emptyMap();
        this.overlay = null;
        this.prefixMap = prefixMap;
        this.maxWeight = maxWeight;
        this.totalWeight = totalWeight;
        this.maxWordLength = 0;
        for (Map.Entry<String, Double> e : prefixMap.entrySet()) {
            if (e.getValue() > 0.0) {
                int len = e.getKey().codePointCount(0, e.getKey().length());
                if (len > maxWordLength) maxWordLength = len;
            }
        }
    }

    /**
     * Adds a word with its frequency weight. Thread-unsafe; call before any
     * concurrent access.
     */
    public void add(String word, double weight) {
        if (word == null || word.isEmpty()) return;

        if (weight > maxWeight) maxWeight = weight;
        int wordLen = word.codePointCount(0, word.length());
        if (wordLen > maxWordLength) maxWordLength = wordLen;

        int[] codePoints = word.codePoints().toArray();
        int n = codePoints.length;

        if (dawg != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n - 1; i++) {
                sb.appendCodePoint(codePoints[i]);
                overlay.putIfAbsent(sb.toString(), 0.0);
            }

            double existing = overlay.getOrDefault(word, 0.0);
            if (existing == 0.0 && dawg.contains(word)) {
                existing = customWeights.getOrDefault(word, 1.0);
            }

            if (weight > existing) {
                overlay.put(word, weight);
                totalWeight += weight - existing;
            } else {
                overlay.putIfAbsent(word, weight);
            }
            return;
        }

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
        if (dawg != null) {
            return new ThaiTrie(dawg, customWeights, new HashMap<>(this.overlay), this.maxWeight, this.totalWeight);
        }
        return new ThaiTrie(new HashMap<>(this.prefixMap), this.maxWeight, this.totalWeight);
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
     * Longest dictionary word in code points (0 when the trie is empty, in
     * which case prefix scans run unbounded and stop at dead transitions).
     */
    public int maxWordLength() {
        return maxWordLength;
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

        if (dawg != null) {
            int state = 0;
            for (int i = start; i < limit; i++) {
                int r = runes[i];
                if (r > 0xFFFF) break;
                state = dawg.transition(state, (char) r);
                if (state == -1) break;
                if (dawg.isFinal(state)) {
                    String sub = null;
                    double w = 1.0;
                    if (!customWeights.isEmpty()) {
                        sub = new String(runes, start, i + 1 - start);
                        Double cw = customWeights.get(sub);
                        if (cw != null) w = cw;
                    }
                    if (overlay != null && !overlay.isEmpty()) {
                        if (sub == null) {
                            sub = new String(runes, start, i + 1 - start);
                        }
                        Double ow = overlay.get(sub);
                        if (ow != null && ow > 0.0) {
                            w = ow;
                        }
                    }
                    matches.add(new PrefixMatch(i + 1, w));
                }
            }

            if (overlay != null && !overlay.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = start; i < limit; i++) {
                    sb.appendCodePoint(runes[i]);
                    String sub = sb.toString();
                    Double ow = overlay.get(sub);
                    if (ow == null) break;
                    if (ow > 0.0) {
                        boolean exists = false;
                        for (PrefixMatch m : matches) {
                            if (m.end == i + 1) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            int insertIdx = 0;
                            while (insertIdx < matches.size() && matches.get(insertIdx).end < i + 1) {
                                insertIdx++;
                            }
                            matches.add(insertIdx, new PrefixMatch(i + 1, ow));
                        }
                    }
                }
            }

            return matches;
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
        if (dawg != null) {
            int count = dawg.numWords();
            if (overlay != null && !overlay.isEmpty()) {
                for (Map.Entry<String, Double> e : overlay.entrySet()) {
                    if (e.getValue() > 0.0 && !dawg.contains(e.getKey())) {
                        count++;
                    }
                }
            }
            return count;
        }
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
        if (overlay != null && !overlay.isEmpty()) {
            Double w = overlay.get(word);
            if (w != null) return w > 0.0;
        }
        if (dawg != null) {
            return dawg.contains(word);
        }
        Double w = prefixMap.get(word);
        return w != null && w > 0.0;
    }
}
