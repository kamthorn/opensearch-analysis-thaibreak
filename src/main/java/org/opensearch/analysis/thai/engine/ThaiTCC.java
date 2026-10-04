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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thai Character Cluster (TCC) segmenter.
 *
 * <p>Port of the Go {@code tcc.go} in thai-break. Computes valid TCC break
 * positions (boundaries that never fall in the middle of a Thai syllable
 * cluster). The Viterbi tokenizer uses these positions as a hard constraint:
 * it only places word boundaries at TCC-valid positions.
 *
 * <p>Rules implement Theeramunkong's 30-rule TCC definition.
 */
public final class ThaiTCC {

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

        while (bytePos < textLen) {
            String sub = text.substring(bytePos);

            // 1. Try lookahead rules first
            Matcher lookahead = TCC_LOOKAHEAD.matcher(sub);
            if (lookahead.find()) {
                int matchLen = lookahead.end();
                String rest = sub.substring(matchLen);
                if (isFollowedByLookaheadChar(rest)) {
                    bytePos += matchLen;
                    valid[byteToRune[bytePos]] = true;
                    continue;
                }
            }

            // 2. Try general rules
            Matcher general = TCC_GENERAL.matcher(sub);
            if (general.find()) {
                String matched = sub.substring(0, general.end());
                String rest = sub.substring(general.end());
                int clusterBytes = clusterLen(matched, rest);
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
            if (isDependentThai(runes[i])) {
                valid[i] = false;
            }
        }

        return valid;
    }

    /** Whether the first rune of {@code rest} can follow a diphthong ending. */
    private static boolean isFollowedByLookaheadChar(String rest) {
        if (rest.isEmpty()) return true;
        int r = rest.codePointAt(0);
        // Base consonants ก-ฮ or leading vowels เ-ไ
        if ((r >= 0x0E01 && r <= 0x0E2E) || (r >= 0x0E40 && r <= 0x0E44)) return true;
        // Non-Thai
        if (r < 0x0E00 || r > 0x0E7F) return true;
        return false;
    }

    /**
     * Trims the final consonant from a matched cluster if followed by a
     * dependent vowel/mark (that consonant begins the next cluster).
     * Exception: ว before ะ stays (it's part of -ัวะ).
     */
    private static int clusterLen(String matched, String rest) {
        if (matched.length() <= 1) return matched.length();
        int lastCp = matched.codePointBefore(matched.length());
        int lastSize = Character.charCount(lastCp);
        if (rest.isEmpty()) return matched.length();
        int nextCp = rest.codePointAt(0);
        // final consonant (ก-ฮ) followed by dependent mark → back off
        if (lastCp >= 'ก' && lastCp <= 'ฮ'
                && Character.codePointCount(matched, 0, matched.length()) > 1
                && isDependentThai(nextCp)
                && !(lastCp == 'ว' && nextCp == 'ะ')) {
            return matched.length() - lastSize;
        }
        return matched.length();
    }

    /**
     * Returns {@code true} if {@code r} is a dependent Thai vowel or diacritic
     * that can never start a cluster: ะ ั า ำ ิ–ฺ ๅ ็–๎
     */
    static boolean isDependentThai(int r) {
        return (r >= 0x0E30 && r <= 0x0E3A) || r == 0x0E45 || (r >= 0x0E47 && r <= 0x0E4E);
    }
}
