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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * {@link ThaiTCC} against the regular-expression implementation that the generated DFAs replaced.
 * The rules below are the plugin's own, so the test also catches DFA tables that are out of date.
 */
public class ThaiTCCTests extends LuceneTestCase {

    private static final Pattern TCC_GENERAL;
    private static final Pattern TCC_LOOKAHEAD;

    static {
        String c = "[ก-ฮ]";
        String t = "[่-๋]?";
        String d = "[ุู]";
        String k = "([ก-ฮ][ก-ฮ]?[ุูิ]?์)?";

        String[] generalRules = {
            // Mai Han-akat always has a final: -ัวะ, or a consonant (with or without a tone mark).
            "cั[่-๋]?วะ",
            "cั[่-๋]?ck",
            "cั[่-๋]?",
            "เc็ck",
            "เcctาะk",
            "เccีtยะk",
            "เ(?:c[รลว]|หc)็ck",
            "เcิc์ck",
            // A true cluster or ห-led onset between เ and the vowel belongs to the syllable (เครื่อง, เพลิง, เปล่า).
            "เ(?:[กขคตปพผบด][รล]|[กขค]ว|ทร|ห[งญนมยรลว])ิtck",
            "เcิtck",
            "เcีtยะ?k",
            "เ(?:[กขคตปพผบด][รล]|[กขค]ว|ทร|ห[งญนมยรลว])ืtอะ?k",
            "เcืtอะk",
            "เcืtอ?k",
            "เ(?:[กขคตปพผบด][รล]|[กขค]ว|ทร|ห[งญนมยรลว])tาk",
            "เctา?ะ?k",
            "c[ึื]tck",
            "c[ะ-ู]tk",
            "c[ิุู]์",
            "cรรc์",
            "c็",
            "ct[ะาำ]?k",
            "แc็ck",
            "แcc์k",
            "แctะk",
            "แ(?:c[รลว]|หc)็ck",
            "แccc์k",
            "โctะk",
            "[เ-ไ]ctk",
            "ก็",
            "อึ",
            "หึ",
        };

        String[] lookaheadRules = {
            "เccีtยk",
            "เc[ิีุู]tยk",
        };

        TCC_GENERAL  = buildPattern(generalRules, c, t, d, k);
        TCC_LOOKAHEAD = buildPattern(lookaheadRules, c, t, d, k);
    }

    private static Pattern buildPattern(String[] rules, String c, String t, String d, String k) {
        StringBuilder sb = new StringBuilder("^(");
        for (int i = 0; i < rules.length; i++) {
            if (i > 0) sb.append('|');
            String r = rules[i]
                .replace("k", k)
                .replace("c", c)
                .replace("t", t)
                .replace("d", d);
            sb.append(r);
        }
        sb.append(')');
        return Pattern.compile(sb.toString());
    }

    /** The regular-expression implementation of {@link ThaiTCC#validPositions}. */
    private static boolean[] referenceValidPositions(int[] runes) {
        int n = runes.length;
        boolean[] valid = new boolean[n + 1];
        valid[0] = true;
        valid[n] = true;
        if (n == 0) return valid;

        // Build the full string for regex matching
        String text = new String(runes, 0, n);

        // Map byte offset → rune index
        int[] byteToRune = new int[text.length() + 1];
        int ri = 0;
        for (int bi = 0; bi < text.length(); ) {
            char ch = text.charAt(bi);
            byteToRune[bi] = ri;
            if (Character.isHighSurrogate(ch) && bi + 1 < text.length()) {
                bi += 2;
            } else {
                bi++;
            }
            ri++;
        }
        byteToRune[text.length()] = n;

        int bytePos = 0;
        int textLen = text.length();

        // The patterns are anchored, so matching a region of the text finds what a match on the rest of
        // the text would, without copying it
        Matcher lookahead = TCC_LOOKAHEAD.matcher(text);
        Matcher general = TCC_GENERAL.matcher(text);

        while (bytePos < textLen) {
            // 1. Try lookahead rules first (all of them start with เ)
            if (text.charAt(bytePos) == 'เ' && lookahead.region(bytePos, textLen).lookingAt()) {
                int next = lookahead.end();
                if (ThaiTCC.isFollowedByLookaheadChar(next < textLen ? text.codePointAt(next) : -1)) {
                    bytePos = next;
                    valid[byteToRune[bytePos]] = true;
                    continue;
                }
            }

            // 2. Try general rules
            if (general.region(bytePos, textLen).lookingAt()) {
                int next = general.end();
                String matched = text.substring(bytePos, next);
                int clusterBytes = clusterLen(matched, next < textLen ? text.codePointAt(next) : -1);
                bytePos += clusterBytes;
                valid[byteToRune[bytePos]] = true;
            } else {
                // Advance one character
                char ch = text.charAt(bytePos);
                bytePos += Character.isHighSurrogate(ch) ? 2 : 1;
                if (bytePos <= textLen) {
                    valid[byteToRune[bytePos]] = true;
                }
            }
        }

        // Non-Thai characters are always valid boundaries
        for (int i = 0; i < n; i++) {
            int r = runes[i];
            if (r < 0x0E00 || r > 0x0E7F) {
                valid[i] = true;
                valid[i + 1] = true;
            }
        }

        // Never a boundary before a dependent vowel or diacritic
        for (int i = 1; i < n; i++) {
            if (ThaiTCC.isDependentThai(runes[i])) {
                valid[i] = false;
            }
        }

        return valid;
    }

    private static int clusterLen(String matched, int nextCp) {
        if (matched.length() <= 1) return matched.length();
        int lastCp = matched.codePointBefore(matched.length());
        int lastSize = Character.charCount(lastCp);
        if (nextCp < 0) return matched.length();
        // final consonant (ก-ฮ) followed by dependent mark → back off
        if (lastCp >= 'ก' && lastCp <= 'ฮ'
                && Character.codePointCount(matched, 0, matched.length()) > 1
                && ThaiTCC.isDependentThai(nextCp)
                && !(lastCp == 'ว' && nextCp == 'ะ')) {
            return matched.length() - lastSize;
        }
        return matched.length();
    }

    private static void check(int[] runes) {
        boolean[] expected = referenceValidPositions(runes);
        boolean[] actual = ThaiTCC.validPositions(runes);
        if (!Arrays.equals(expected, actual)) {
            fail("validPositions(\"" + new String(runes, 0, runes.length) + "\") = " + Arrays.toString(actual)
                + ", expected " + Arrays.toString(expected));
        }
    }

    public void testExamples() {
        for (String text : new String[] {
            "", "ก", "เครื่อง", "เพลิง", "เปล่า", "ผัวะ", "รึยัง", "เสียง", "เขียน ", "เสีย)", "เกี่ยว",
            "กรรม์", "แข็ง", "ก็", "น้ำ", "ไทยabc๑๒๓", "ั่", "เ", "สวัสดีครับ ภาษาไทย", "😀เสีย" }) {
            check(text.codePoints().toArray());
        }
    }

    public void testRandomStrings() {
        // Consonants and vowels are more likely than the other characters, so the rules often match
        StringBuilder pool = new StringBuilder();
        for (int c = 0x0E01; c <= 0x0E5B; c++) pool.appendCodePoint(c);
        for (int c = 0x0E01; c <= 0x0E2E; c++) pool.appendCodePoint(c).appendCodePoint(c);
        for (int i = 0; i < 3; i++) pool.append("เแโใไะาำิีึืุู็่้๊๋์ๆ");
        pool.append("a 1.)");
        int[] alphabet = pool.codePoints().toArray();
        for (int i = 0; i < 100_000; i++) {
            int[] runes = new int[1 + random().nextInt(40)];
            for (int j = 0; j < runes.length; j++) {
                runes[j] = alphabet[random().nextInt(alphabet.length)];
            }
            check(runes);
        }
    }
}
