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

/**
 * Thai Character Cluster (TCC) segmenter.
 *
 * <p>Port of the Go {@code tcc.go} in thai-break. Computes valid TCC break
 * positions (boundaries that never fall in the middle of a Thai syllable
 * cluster). The Viterbi tokenizer uses these positions as a hard constraint:
 * it only places word boundaries at TCC-valid positions.
 *
 * <p>Rules implement Theeramunkong's 30-rule TCC definition. They are matched with DFAs generated
 * from the rules of the thai-break Rust core ({@link ThaiTccDfa}); a match is the match of the first
 * rule that matches, as with a regular-expression alternation of the rules.
 */
public final class ThaiTCC {

    private ThaiTCC() {}

    /**
     * Computes valid TCC break positions for the given rune array.
     *
     * @param runes  code-points of the text
     * @return boolean array of length {@code runes.length + 1} where {@code true}
     *         means a word boundary may be placed at that position
     */
    public static boolean[] validPositions(int[] runes) {
        int n = runes.length;
        boolean[] valid = new boolean[n + 1];
        valid[0] = true;
        valid[n] = true;

        int pos = 0;
        while (pos < n) {
            // 1. Try lookahead rules first (all of them start with เ)
            if (runes[pos] == 'เ') {
                int end = ThaiTccDfa.LOOKAHEAD.find(runes, pos);
                if (end >= 0 && isFollowedByLookaheadChar(end < n ? runes[end] : -1)) {
                    pos = end;
                    valid[pos] = true;
                    continue;
                }
            }

            // 2. Try general rules; otherwise advance one character
            int end = ThaiTccDfa.GENERAL.find(runes, pos);
            pos = end >= 0 ? clusterEnd(runes, pos, end) : pos + 1;
            valid[pos] = true;
        }

        for (int i = 0; i < n; i++) {
            int r = runes[i];
            if (r < 0x0E00 || r > 0x0E7F) {
                // Non-Thai characters are always valid boundaries
                valid[i] = true;
                valid[i + 1] = true;
            } else if (i > 0 && isDependentThai(r)) {
                // Never a boundary before a dependent vowel or diacritic
                valid[i] = false;
            }
        }

        return valid;
    }

    /** Whether the code point {@code r} (-1 at the end of the text) can follow a diphthong ending. */
    static boolean isFollowedByLookaheadChar(int r) {
        if (r < 0) return true;
        // Base consonants ก-ฮ or leading vowels เ-ไ
        if ((r >= 0x0E01 && r <= 0x0E2E) || (r >= 0x0E40 && r <= 0x0E44)) return true;
        // Non-Thai
        if (r < 0x0E00 || r > 0x0E7F) return true;
        return false;
    }

    /**
     * End of the matched cluster {@code runes[start, end)}: the final consonant is left out if a
     * dependent vowel/mark follows (that consonant begins the next cluster).
     * Exception: ว before ะ stays (it's part of -ัวะ).
     */
    static int clusterEnd(int[] runes, int start, int end) {
        if (end < runes.length && end - start > 1) {
            int last = runes[end - 1];
            int next = runes[end];
            if (last >= 'ก' && last <= 'ฮ' && isDependentThai(next) && !(last == 'ว' && next == 'ะ')) {
                return end - 1;
            }
        }
        return end;
    }

    /**
     * Returns {@code true} if {@code r} is a dependent Thai vowel or diacritic
     * that can never start a cluster: ะ ั า ำ ิ–ฺ ๅ ็–๎
     */
    static boolean isDependentThai(int r) {
        return (r >= 0x0E30 && r <= 0x0E3A) || r == 0x0E45 || (r >= 0x0E47 && r <= 0x0E4E);
    }
}
