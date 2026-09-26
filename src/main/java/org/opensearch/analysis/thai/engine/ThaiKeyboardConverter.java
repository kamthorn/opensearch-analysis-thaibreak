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

import java.util.Arrays;

/**
 * Bi-directional keyboard layout converter between US-QWERTY and Thai Kedmanee (TIS 820-2531).
 *
 * <p>Handles common mis-typing when a user enters queries without switching keyboard layouts:
 * <ul>
 *   <li>US-QWERTY to Thai Kedmanee: {@code "l;ylfu"} &rarr; {@code "สวัสดี"}</li>
 *   <li>Thai Kedmanee to US-QWERTY: {@code "สวัสดี"} &rarr; {@code "l;ylfu"}</li>
 * </ul>
 */
public final class ThaiKeyboardConverter {

    /** Direction mode for keyboard layout conversion. */
    public enum Direction {
        /** Convert from US-QWERTY layout to Thai Kedmanee layout. */
        QWERTY_TO_KEDMANEE,
        /** Convert from Thai Kedmanee layout to US-QWERTY layout. */
        KEDMANEE_TO_QWERTY,
        /** Detect and convert in either direction. */
        BOTH;

        /**
         * Parses direction from string.
         *
         * @param name configuration string value
         * @return parsed {@link Direction}, defaults to {@link #QWERTY_TO_KEDMANEE} if unknown
         */
        public static Direction fromString(String name) {
            if (name == null) return QWERTY_TO_KEDMANEE;
            String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "kedmanee_to_qwerty", "th_to_en", "thai_to_eng" -> KEDMANEE_TO_QWERTY;
                case "both", "bidirectional" -> BOTH;
                default -> QWERTY_TO_KEDMANEE;
            };
        }
    }

    private static final char[] EN_TO_TH = new char[128];
    private static final char[] TH_TO_EN = new char[0x0E80 - 0x0E00];

    static {
        Arrays.fill(EN_TO_TH, (char) 0);
        Arrays.fill(TH_TO_EN, (char) 0);

        Object[][] pairs = {
            {'q', 'ๆ'}, {'Q', '๐'},
            {'w', 'ไ'}, {'W', '"'},
            {'e', 'ำ'}, {'E', 'ฎ'},
            {'r', 'พ'}, {'R', 'ฑ'},
            {'t', 'ะ'}, {'T', 'ธ'},
            {'y', 'ั'}, {'Y', 'ํ'},
            {'u', 'ี'}, {'U', '๊'},
            {'i', 'ร'}, {'I', 'ณ'},
            {'o', 'น'}, {'O', 'ฯ'},
            {'p', 'ย'}, {'P', 'ญ'},
            {'[', 'บ'}, {'{', 'ฐ'},
            {']', 'ล'}, {'}', ','},
            {'\\', 'ฃ'}, {'|', 'ฅ'},
            {'a', 'ฟ'}, {'A', 'ฤ'},
            {'s', 'ห'}, {'S', 'ฆ'},
            {'d', 'ก'}, {'D', 'ฏ'},
            {'f', 'ด'}, {'F', 'โ'},
            {'g', 'เ'}, {'G', 'ฌ'},
            {'h', '้'}, {'H', '็'},
            {'j', '่'}, {'J', '๋'},
            {'k', 'า'}, {'K', 'ษ'},
            {'l', 'ส'}, {'L', 'ศ'},
            {';', 'ว'}, {':', 'ซ'},
            {'\'', 'ง'}, {'"', '.'},
            {'z', 'ผ'}, {'Z', '('},
            {'x', 'ป'}, {'X', ')'},
            {'c', 'แ'}, {'C', 'ฉ'},
            {'v', 'อ'}, {'V', 'ฮ'},
            {'b', 'ิ'}, {'B', 'ฺ'},
            {'n', 'ื'}, {'N', '์'},
            {'m', 'ท'}, {'M', '?'},
            {',', 'ม'}, {'<', 'ฒ'},
            {'.', 'ใ'}, {'>', 'ฬ'},
            {'/', 'ฝ'}, {'?', 'ฦ'},
            {'`', '_'}, {'~', '%'},
            {'1', 'ๅ'}, {'!', '+'},
            {'2', '/'}, {'@', '๑'},
            {'3', '-'}, {'#', '๒'},
            {'4', 'ภ'}, {'$', '๓'},
            {'5', 'ถ'}, {'%', '๔'},
            {'6', 'ุ'}, {'^', 'ู'},
            {'7', 'ึ'}, {'&', '฿'},
            {'8', 'ค'}, {'*', '๕'},
            {'9', 'ต'}, {'(', '๖'},
            {'0', 'จ'}, {')', '๗'},
            {'-', 'ข'}, {'_', '๘'},
            {'=', 'ช'}, {'+', '๙'}
        };

        for (Object[] pair : pairs) {
            char en = (Character) pair[0];
            char th = (Character) pair[1];
            if (en < 128) {
                EN_TO_TH[en] = th;
            }
            if (th >= 0x0E00 && th < 0x0E80) {
                TH_TO_EN[th - 0x0E00] = en;
            }
        }
    }

    private ThaiKeyboardConverter() {}

    /**
     * Converts a single character from US-QWERTY layout to Thai Kedmanee layout.
     *
     * @param c input character
     * @return converted Thai Kedmanee character, or original character if unmapped
     */
    public static char toKedmanee(char c) {
        if (c < 128) {
            char mapped = EN_TO_TH[c];
            if (mapped != 0) return mapped;
        }
        return c;
    }

    /**
     * Converts a single character from Thai Kedmanee layout to US-QWERTY layout.
     *
     * @param c input character
     * @return converted US-QWERTY character, or original character if unmapped
     */
    public static char toQwerty(char c) {
        if (c >= 0x0E00 && c < 0x0E80) {
            char mapped = TH_TO_EN[c - 0x0E00];
            if (mapped != 0) return mapped;
        }
        return c;
    }

    /**
     * Converts a string from US-QWERTY layout to Thai Kedmanee layout.
     *
     * @param text input text
     * @return converted string
     */
    public static String toKedmanee(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            sb.append(toKedmanee(text.charAt(i)));
        }
        return sb.toString();
    }

    /**
     * Converts a string from Thai Kedmanee layout to US-QWERTY layout.
     *
     * @param text input text
     * @return converted string
     */
    public static String toQwerty(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            sb.append(toQwerty(text.charAt(i)));
        }
        return sb.toString();
    }

    /**
     * Checks if the character is in the Thai Unicode block (U+0E00 to U+0E7F).
     *
     * @param c character to check
     * @return {@code true} if Thai character
     */
    public static boolean isThai(char c) {
        return c >= 0x0E00 && c <= 0x0E7F;
    }

    /**
     * Checks if the character sequence contains at least one Thai character.
     *
     * @param text text to check
     * @return {@code true} if text contains Thai
     */
    public static boolean containsThai(CharSequence text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            if (isThai(text.charAt(i))) return true;
        }
        return false;
    }
}
