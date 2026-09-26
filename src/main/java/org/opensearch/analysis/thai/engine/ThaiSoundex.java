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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Implementation of the standard Thai phonetic soundex algorithm (Udom83).
 *
 * <p>Reference:
 * Wannee Udompanich. "String searching for Thai alphabet using Soundex compression technique."
 * Master's Thesis, Chulalongkorn University, 1983.
 *
 * <p>Produces a 7-character phonetic signature (1 Thai initial consonant + 6 digits),
 * mapping homophones (คำพ้องเสียง) and common spelling variants to the same soundex code.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code "ลัก"} &rarr; {@code "ร100000"}</li>
 *   <li>{@code "รัก"} &rarr; {@code "ร100000"}</li>
 *   <li>{@code "รักษ์"} &rarr; {@code "ร100000"}</li>
 *   <li>{@code "กาล"} &rarr; {@code "ก900000"}</li>
 *   <li>{@code "การ"} &rarr; {@code "ก900000"}</li>
 *   <li>{@code "การณ์"} &rarr; {@code "ก900000"}</li>
 * </ul>
 */
public final class ThaiSoundex {

    private static final String THAI_CONSONANTS = "กขฃคฅฆงจฉชฌญฎฏฐฑฒณดตถทธนบปผฝพฟภมยรลวศษสหฬอฮ";
    private static final String THANTHAKHAT = "\u0E4C";

    private static final Pattern RE_1 = Pattern.compile("รร([\\u0E40-\\u0E44])");
    private static final Pattern RE_2 = Pattern.compile("รร([" + THAI_CONSONANTS + "][" + THAI_CONSONANTS + "\\u0E40-\\u0E44])");
    private static final Pattern RE_3 = Pattern.compile("รร([" + THAI_CONSONANTS + "][\\u0E30-\\u0E39\\u0E48-\\u0E4C])");
    private static final Pattern RE_4 = Pattern.compile("รร");
    private static final Pattern RE_5 = Pattern.compile("ไ([" + THAI_CONSONANTS + "]ย)");
    private static final Pattern RE_6 = Pattern.compile("[ไใ]([" + THAI_CONSONANTS + "])");
    private static final Pattern RE_7 = Pattern.compile("\\u0E33(ม[\\u0E30-\\u0E39])");
    private static final Pattern RE_8 = Pattern.compile("\\u0E33ม");
    private static final Pattern RE_9 = Pattern.compile("\\u0E33");
    private static final Pattern RE_10 = Pattern.compile(
        "จน์|มณ์|ณฑ์|ทร์|ตร์|[" + THAI_CONSONANTS + "]" + THANTHAKHAT + "|[" + THAI_CONSONANTS + "][\\u0E30-\\u0E39]" + THANTHAKHAT
    );
    private static final Pattern RE_11 = Pattern.compile("[\\u0E30-\\u0E4C]");

    private static final Map<Character, Character> TRANS1 = new HashMap<>();
    private static final Map<Character, Character> TRANS2 = new HashMap<>();

    static {
        String src1 = "กขฃคฅฆงจฉชฌซศษสฎดฏตฐฑฒถทธณนบปผพภฝฟมญยรลฬฤฦวอหฮ";
        String dst1 = "กขขขขขงจชชชสสสสดดตตททททททนนบปพพพฟฟมยยรรรรรวอฮฮ";
        for (int i = 0; i < src1.length(); i++) {
            TRANS1.put(src1.charAt(i), dst1.charAt(i));
        }

        String src2 = "มวำกขฃคฅฆงยญณนฎฏดตศษสบปพภผฝฟหอฮจฉชซฌฐฑฒถทธรฤลฦ";
        String dst2 = "0001111112233344444445555666666777778888889999";
        for (int i = 0; i < src2.length(); i++) {
            TRANS2.put(src2.charAt(i), dst2.charAt(i));
        }
    }

    private ThaiSoundex() {}

    /**
     * Converts a Thai word into its Udom83 phonetic soundex code.
     *
     * @param text input Thai word
     * @return 7-character phonetic soundex code, or empty string if input is null, empty, or non-Thai
     */
    public static String udom83(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String s = text;
        s = RE_1.matcher(s).replaceAll("ัน$1");
        s = RE_2.matcher(s).replaceAll("ั$1");
        s = RE_3.matcher(s).replaceAll("ัน$1");
        s = RE_4.matcher(s).replaceAll("ัน");
        s = RE_5.matcher(s).replaceAll("$1");
        s = RE_6.matcher(s).replaceAll("$1ย");
        s = RE_7.matcher(s).replaceAll("ม$1");
        s = RE_8.matcher(s).replaceAll("ม");
        s = RE_9.matcher(s).replaceAll("ม");
        s = RE_10.matcher(s).replaceAll("");
        s = RE_11.matcher(s).replaceAll("");

        if (s.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        // First char
        char c0 = s.charAt(0);
        sb.append(TRANS1.getOrDefault(c0, c0));

        // Subsequent chars
        for (int i = 1; i < s.length(); i++) {
            char ci = s.charAt(i);
            sb.append(TRANS2.getOrDefault(ci, ci));
        }

        // Pad with zeros to at least 7 chars
        sb.append("000000");

        return sb.substring(0, 7);
    }
}
