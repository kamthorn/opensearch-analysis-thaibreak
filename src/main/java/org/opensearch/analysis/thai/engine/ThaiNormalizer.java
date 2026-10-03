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

import java.nio.CharBuffer;

/**
 * Normalizes the spelling variants of a Thai token so that they index to the same term.
 *
 * <p>Same rules as Apache Lucene's {@code org.apache.lucene.analysis.th.ThaiNormalizer}
 * (Lucene 10.6, apache/lucene#16717), which this plugin cannot use yet because it is built
 * against older Lucene versions:
 * <ul>
 *   <li>Removes zero-width characters (ZWSP U+200B, ZWNJ U+200C).</li>
 *   <li>Removes combining marks left dangling at the start of the token.</li>
 *   <li>{@code เเ} (Sara E twice) &rarr; {@code แ} (Sara Ae).</li>
 *   <li>Decomposed Sara Am &rarr; {@code ำ}: {@code ํา} &rarr; {@code ำ}, and with a tone mark
 *       on either side of Nikhahit, {@code นํ้า} / {@code น้ํา} &rarr; {@code น้ำ}.</li>
 *   <li>Lakkhangyao {@code ๅ} &rarr; {@code า}, except after {@code ฤ} / {@code ฦ}.</li>
 *   <li>Canonical order: above/below vowel before tone mark ({@code ก่ี} &rarr; {@code กี่}),
 *       tone mark before a following vowel ({@code นำ้} &rarr; {@code น้ำ}).</li>
 *   <li>Repeated vowels and marks are collapsed ({@code น้้ำ} &rarr; {@code น้ำ}); of two
 *       different tone marks in a row, the last one is kept.</li>
 * </ul>
 */
public final class ThaiNormalizer {

    private ThaiNormalizer() {}

    /**
     * Normalizes {@code s} and returns it, or the same instance when nothing changed.
     *
     * @param s text to normalize
     * @return the normalized text
     */
    public static String normalize(String s) {
        if (s == null || !needsNormalization(s)) {
            return s;
        }
        char[] buf = s.toCharArray();
        int len = normalize(buf, buf.length);
        return s.contentEquals(CharBuffer.wrap(buf, 0, len)) ? s : new String(buf, 0, len);
    }

    /**
     * Normalizes the first {@code len} chars of {@code s} in place.
     *
     * @param s   input buffer
     * @param len number of chars to normalize
     * @return the length after normalization (never longer than {@code len})
     */
    public static int normalize(char[] s, int len) {
        if (len == 0) {
            return 0;
        }

        // Step 1: Remove zero-width characters
        for (int i = 0; i < len; i++) {
            if (s[i] == '​' || s[i] == '‌') {
                len = delete(s, i, len);
                i--;
            }
        }

        // Remove leading dangling non-base marks at token start
        while (len > 0 && isDanglingMark(s[0])) {
            len = delete(s, 0, len);
        }

        // Step 2: Double Sara E, Sara Am recomposition, Lakkhangyao
        for (int i = 0; i < len; i++) {
            char c = s[i];

            // Double Sara E -> Sara Ae
            if (c == 'เ' && i + 1 < len && s[i + 1] == 'เ') {
                s[i] = 'แ';
                len = delete(s, i + 1, len);
                continue;
            }

            // Nikhahit + optional tone mark + Sara Aa -> optional tone mark + Sara Am
            if (c == 'ํ') {
                if (i + 1 < len && s[i + 1] == 'า') {
                    s[i] = 'ำ';
                    len = delete(s, i + 1, len);
                    continue;
                } else if (i + 2 < len && isToneMark(s[i + 1]) && s[i + 2] == 'า') {
                    s[i] = s[i + 1];
                    s[i + 1] = 'ำ';
                    len = delete(s, i + 2, len);
                    continue;
                }
            }

            // Tone mark + Nikhahit + Sara Aa -> tone mark + Sara Am
            if (isToneMark(c) && i + 2 < len && s[i + 1] == 'ํ' && s[i + 2] == 'า') {
                s[i + 1] = 'ำ';
                len = delete(s, i + 2, len);
                continue;
            }

            // Lakkhangyao -> Sara Aa unless preceded by Ru or Lu
            if (c == 'ๅ' && (i == 0 || (s[i - 1] != 'ฤ' && s[i - 1] != 'ฦ'))) {
                s[i] = 'า';
            }
        }

        // Step 3: Canonical reordering
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < len - 1; i++) {
                if ((isToneMarkOrThanthakhat(s[i]) && isAboveOrBelowVowel(s[i + 1]))
                        || (isFollowVowel(s[i]) && isToneMark(s[i + 1]))) {
                    char tmp = s[i];
                    s[i] = s[i + 1];
                    s[i + 1] = tmp;
                    changed = true;
                }
            }
        }

        // Step 4: Deduplicate repeated vowels, tone marks and diacritics
        for (int i = 0; i < len - 1; i++) {
            if (isDeduplicable(s[i]) && s[i] == s[i + 1]) {
                len = delete(s, i + 1, len);
                i--;
            } else if (isToneMark(s[i]) && isToneMark(s[i + 1])) {
                len = delete(s, i, len);
                i--;
            }
        }

        return len;
    }

    /** Only text with a Thai vowel/mark or a zero-width char can change. */
    private static boolean needsNormalization(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'ะ' && c <= '๎') || c == '​' || c == '‌') {
                return true;
            }
        }
        return false;
    }

    private static int delete(char[] s, int pos, int len) {
        if (pos < len - 1) {
            System.arraycopy(s, pos + 1, s, pos, len - pos - 1);
        }
        return len - 1;
    }

    private static boolean isToneMark(char c) {
        return c >= '่' && c <= '๋';
    }

    private static boolean isToneMarkOrThanthakhat(char c) {
        return isToneMark(c) || c == '์';
    }

    private static boolean isAboveOrBelowVowel(char c) {
        return c == 'ั' || (c >= 'ิ' && c <= 'ฺ') || c == '็';
    }

    private static boolean isFollowVowel(char c) {
        return c == 'ะ' || c == 'า' || c == 'ำ' || c == 'ๅ';
    }

    private static boolean isDanglingMark(char c) {
        return isAboveOrBelowVowel(c) || isToneMarkOrThanthakhat(c) || c == 'ํ' || c == '๎';
    }

    private static boolean isDeduplicable(char c) {
        return (c >= 'ะ' && c <= 'ฺ') || c == 'ๅ' || (c >= '็' && c <= '๎');
    }
}
