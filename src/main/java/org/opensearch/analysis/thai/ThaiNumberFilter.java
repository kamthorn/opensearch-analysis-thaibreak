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
package org.opensearch.analysis.thai;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token filter that converts Thai digits (๐-๙) and Thai spelled-out number words
 * (e.g. "หนึ่งแสนสองหมื่น", "ห้าหมื่น", "สามร้อย", "สิบสอง", "ยี่สิบเอ็ด")
 * into Arabic numerals ("120000", "50000", "300", "12", "21").
 *
 * <p>Supports both single-token numbers and multi-token adjacent numeral sequences,
 * emitting converted numerals as graph synonyms with {@code posInc = 0} and
 * multi-position span ({@code posLen}) when {@code keep_original} is {@code true}.
 */
public final class ThaiNumberFilter extends TokenFilter {

    /** Token type assigned to converted Thai number tokens. */
    public static final String TOKEN_TYPE_THAI_NUMBER = "<THAI_NUMBER>";

    private static final Map<String, Integer> DIGITS;
    static {
        Map<String, Integer> map = new HashMap<>();
        map.put("ศูนย์", 0);
        map.put("หนึ่ง", 1);
        map.put("เอ็ด", 1);
        map.put("สอง", 2);
        map.put("ยี่", 2);
        map.put("สาม", 3);
        map.put("สี่", 4);
        map.put("ห้า", 5);
        map.put("หก", 6);
        map.put("เจ็ด", 7);
        map.put("แปด", 8);
        map.put("เก้า", 9);
        DIGITS = Collections.unmodifiableMap(map);
    }

    private static final Map<String, Long> POWERS_OF_10;
    static {
        Map<String, Long> map = new HashMap<>();
        map.put("สิบ", 10L);
        map.put("ร้อย", 100L);
        map.put("พัน", 1000L);
        map.put("หมื่น", 10000L);
        map.put("แสน", 100000L);
        POWERS_OF_10 = Collections.unmodifiableMap(map);
    }

    private static final String[] UNITS = {
        "หนึ่ง", "หมื่น", "ศูนย์",
        "เจ็ด", "แปด", "เก้า", "แสน", "ล้าน", "ร้อย", "เอ็ด",
        "สอง", "สาม", "สี่", "ห้า", "หก", "สิบ", "พัน", "ยี่"
    };

    private final boolean keepOriginal;
    private final boolean convertDigits;
    private final boolean convertWords;
    private final long minWordValue;

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

    private final Deque<TokenData> outputQueue = new ArrayDeque<>();
    private TokenData pendingToken = null;

    /**
     * Creates a new filter with default settings (keep_original=true, convert_digits=true, convert_words=true, min_val=0).
     *
     * @param input token stream
     */
    public ThaiNumberFilter(TokenStream input) {
        this(input, true, true, true, 0L);
    }

    /**
     * Creates a new filter with custom configuration.
     *
     * @param input         token stream
     * @param keepOriginal  whether to retain original tokens alongside converted numbers
     * @param convertDigits whether to convert Thai digits (๐-๙) to Arabic digits (0-9)
     * @param convertWords  whether to convert spelled-out Thai words to Arabic numbers
     * @param minWordValue  minimum numerical value for word conversion (e.g. 0 to convert all)
     */
    public ThaiNumberFilter(
        TokenStream input,
        boolean keepOriginal,
        boolean convertDigits,
        boolean convertWords,
        long minWordValue
    ) {
        super(input);
        this.keepOriginal = keepOriginal;
        this.convertDigits = convertDigits;
        this.convertWords = convertWords;
        this.minWordValue = minWordValue;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!outputQueue.isEmpty()) {
            applyTokenData(outputQueue.poll());
            return true;
        }

        TokenData current;
        if (pendingToken != null) {
            current = pendingToken;
            pendingToken = null;
        } else {
            if (!input.incrementToken()) {
                return false;
            }
            current = captureCurrentToken();
        }

        // 1. Spelled-out Thai numeral words
        if (convertWords && isThaiNumeral(current.term)) {
            List<TokenData> numSequence = new ArrayList<>();
            numSequence.add(current);

            while (input.incrementToken()) {
                TokenData next = captureCurrentToken();
                if (next.startOffset == numSequence.get(numSequence.size() - 1).endOffset
                        && isThaiNumeral(next.term)) {
                    numSequence.add(next);
                } else {
                    pendingToken = next;
                    break;
                }
            }

            StringBuilder sb = new StringBuilder();
            for (TokenData td : numSequence) {
                sb.append(td.term);
            }
            Long val = parseThaiWordNumber(sb.toString());
            if (val != null && val >= minWordValue) {
                String numStr = Long.toString(val);
                int startOffset = numSequence.get(0).startOffset;
                int endOffset = numSequence.get(numSequence.size() - 1).endOffset;
                int totalPosLen = numSequence.size();

                if (keepOriginal) {
                    outputQueue.add(numSequence.get(0));
                    outputQueue.add(new TokenData(
                        numStr, startOffset, endOffset, 0, totalPosLen, TOKEN_TYPE_THAI_NUMBER
                    ));
                    for (int i = 1; i < numSequence.size(); i++) {
                        outputQueue.add(numSequence.get(i));
                    }
                } else {
                    outputQueue.add(new TokenData(
                        numStr, startOffset, endOffset, numSequence.get(0).posInc, 1, TOKEN_TYPE_THAI_NUMBER
                    ));
                }
                applyTokenData(outputQueue.poll());
                return true;
            } else {
                for (TokenData td : numSequence) {
                    outputQueue.add(td);
                }
                applyTokenData(outputQueue.poll());
                return true;
            }
        }

        // 2. Thai digits (๐-๙)
        if (convertDigits && containsThaiDigit(current.term)) {
            String converted = convertThaiDigits(current.term);
            if (keepOriginal) {
                outputQueue.add(current);
                outputQueue.add(new TokenData(
                    converted, current.startOffset, current.endOffset, 0, current.posLen, TOKEN_TYPE_THAI_NUMBER
                ));
            } else {
                outputQueue.add(new TokenData(
                    converted, current.startOffset, current.endOffset, current.posInc, current.posLen, TOKEN_TYPE_THAI_NUMBER
                ));
            }
            applyTokenData(outputQueue.poll());
            return true;
        }

        applyTokenData(current);
        return true;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        outputQueue.clear();
        pendingToken = null;
    }

    private TokenData captureCurrentToken() {
        return new TokenData(
            termAtt.toString(),
            offsetAtt.startOffset(),
            offsetAtt.endOffset(),
            posIncAtt.getPositionIncrement(),
            posLenAtt.getPositionLength(),
            typeAtt.type()
        );
    }

    private void applyTokenData(TokenData data) {
        clearAttributes();
        termAtt.append(data.term);
        offsetAtt.setOffset(data.startOffset, data.endOffset);
        posIncAtt.setPositionIncrement(data.posInc);
        posLenAtt.setPositionLength(data.posLen);
        typeAtt.setType(data.type);
    }

    /**
     * Checks if the character sequence contains at least one Thai digit.
     *
     * @param s character sequence
     * @return true if contains Thai digit
     */
    public static boolean containsThaiDigit(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0E50' && c <= '\u0E59') {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts Thai digits (๐-๙) in the input to Arabic digits (0-9).
     *
     * @param s input character sequence
     * @return string with Thai digits converted
     */
    public static String convertThaiDigits(CharSequence s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0E50' && c <= '\u0E59') {
                sb.append((char) ('0' + (c - '\u0E50')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Checks if a string is a valid Thai numeral expression.
     *
     * @param text string to check
     * @return true if valid Thai numeral
     */
    public static boolean isThaiNumeral(String text) {
        return parseThaiWordNumber(text) != null;
    }

    /**
     * Parses a spelled-out Thai numeral expression into a Long value.
     *
     * @param text Thai numeral text
     * @return numerical value, or null if not a valid Thai numeral expression
     */
    public static Long parseThaiWordNumber(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        List<String> units = tokenizeUnits(text);
        if (units == null || units.isEmpty()) {
            return null;
        }

        if (units.size() == 1 && "ศูนย์".equals(units.get(0))) {
            return 0L;
        }

        long accumulated = 0;
        long nextDigit = 1;
        boolean hasDigit = false;

        for (String token : units) {
            if (DIGITS.containsKey(token)) {
                nextDigit = DIGITS.get(token);
                hasDigit = true;
            } else if (POWERS_OF_10.containsKey(token)) {
                long power = POWERS_OF_10.get(token);
                long factor = hasDigit ? nextDigit : 1;
                accumulated += factor * power;
                nextDigit = 0;
                hasDigit = false;
            } else if ("ล้าน".equals(token)) {
                long factor = hasDigit ? nextDigit : (accumulated == 0 ? 1 : 0);
                accumulated = (accumulated + factor) * 1000000L;
                nextDigit = 0;
                hasDigit = false;
            }
        }

        accumulated += nextDigit;
        return accumulated;
    }

    private static List<String> tokenizeUnits(String text) {
        List<String> list = new ArrayList<>();
        int idx = 0;
        int len = text.length();

        while (idx < len) {
            String matched = null;
            for (String u : UNITS) {
                if (text.startsWith(u, idx)) {
                    matched = u;
                    break;
                }
            }
            if (matched == null) {
                return null;
            }
            list.add(matched);
            idx += matched.length();
        }
        return list;
    }

    private static final class TokenData {
        final String term;
        final int startOffset;
        final int endOffset;
        final int posInc;
        final int posLen;
        final String type;

        TokenData(String term, int startOffset, int endOffset, int posInc, int posLen, String type) {
            this.term = term;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.posInc = posInc;
            this.posLen = posLen;
            this.type = type;
        }
    }
}
