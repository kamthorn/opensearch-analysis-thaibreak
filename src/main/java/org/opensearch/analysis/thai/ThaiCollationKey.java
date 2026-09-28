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

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, ICU-free collation key generator for Thai text following the
 * Royal Institute of Thailand (ราชบัณฑิตยสภา) dictionary ordering rules.
 *
 * <p>Unlike {@link java.text.Collator}, whose Thai rules are supplied by the
 * pluggable locale provider ({@code COMPAT} vs {@code CLDR}) and therefore
 * differ between JDK versions, this class derives its ordering purely from the
 * Unicode code points of the Thai block. The generated key is stable across
 * every JVM, locale and {@code java.locale.providers} configuration.
 *
 * <p>Two behaviours are modelled:
 * <ul>
 *   <li><b>Leading-vowel reordering</b>: the leading vowels {@code เ แ โ ใ ไ}
 *       (U+0E40..U+0E44) are emitted <em>after</em> the consonant they precede,
 *       so {@code "เกาะ"} sorts between {@code "กบ"} and {@code "ขวด"} instead of
 *       after {@code "ฮูก"}.</li>
 *   <li><b>Strength levels</b>: {@link Strength#PRIMARY} compares base letters
 *       only (tones ignored), {@link Strength#SECONDARY} adds tone marks,
 *       {@link Strength#TERTIARY} (default) adds the remaining diacritics, and
 *       {@link Strength#IDENTICAL} additionally distinguishes raw code-point
 *       sequences.</li>
 * </ul>
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class ThaiCollationKey {

    /** Comparison strength, mirroring {@code java.text.Collator} semantics. */
    public enum Strength {
        /** Base letters only (consonants and vowels); tone marks are ignored. */
        PRIMARY,
        /** Base letters plus tone marks. */
        SECONDARY,
        /** Base letters, tone marks and other diacritics (default). */
        TERTIARY,
        /** Full code-point sequence, including mark ordering. */
        IDENTICAL
    }

    /** Canonical decomposition behaviour for Thai combining marks. */
    public enum Decomposition {
        /** Keep marks exactly as written (default). */
        NONE,
        /** Decompose Sara Am (U+0E33) into Nikhahit (U+0E4D) + Sara Aa (U+0E32). */
        CANONICAL,
        /** Identical to {@link #CANONICAL} for the Thai script. */
        FULL
    }

    private static final char SARA_AM = '\u0E33';
    private static final char NIKHAHIT = '\u0E4D';
    private static final char SARA_AA = '\u0E32';

    private static final char CONSONANT_START = '\u0E01';
    private static final char CONSONANT_END = '\u0E2E';
    private static final char PAIYANNOI = '\u0E2F';
    private static final char LEADING_VOWEL_START = '\u0E40';
    private static final char LEADING_VOWEL_END = '\u0E44';
    private static final char TONE_START = '\u0E48';
    private static final char TONE_END = '\u0E4B';
    private static final char MARK_START = '\u0E47';
    private static final char MARK_END = '\u0E4E';
    private static final char DIGIT_START = '\u0E50';
    private static final char DIGIT_END = '\u0E59';
    private static final char REPETITION_MARK = '\u0E46';

    private static final int CONSONANT_BASE = 0x010000;
    private static final int LEADING_VOWEL_BASE = 0x020000;
    private static final int FOLLOWING_VOWEL_BASE = 0x030000;
    private static final int TONE_BASE = 0x040000;
    private static final int MARK_BASE = 0x050000;
    private static final int DIGIT_BASE = 0x060000;
    private static final int OTHER_BASE = 0x100000;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final Strength strength;
    private final Decomposition decomposition;

    /**
     * Creates a collation key generator.
     *
     * @param strength      comparison strength (must not be {@code null})
     * @param decomposition canonical decomposition mode (must not be {@code null})
     */
    public ThaiCollationKey(Strength strength, Decomposition decomposition) {
        if (strength == null) {
            throw new IllegalArgumentException("strength must not be null");
        }
        if (decomposition == null) {
            throw new IllegalArgumentException("decomposition must not be null");
        }
        this.strength = strength;
        this.decomposition = decomposition;
    }

    /**
     * Returns the raw collation key for {@code text}.
     *
     * <p>The key is a big-endian sequence of 24-bit weights; lexicographic
     * comparison of the returned bytes yields the Royal Institute ordering.
     *
     * @param text input text (may be {@code null}, treated as empty)
     * @return collation key bytes
     */
    public byte[] key(String text) {
        int[] weights = weights(text);
        byte[] out = new byte[weights.length * 3];
        int p = 0;
        for (int w : weights) {
            out[p++] = (byte) ((w >>> 16) & 0xFF);
            out[p++] = (byte) ((w >>> 8) & 0xFF);
            out[p++] = (byte) (w & 0xFF);
        }
        return out;
    }

    /**
     * Returns the collation key as a lower-case hexadecimal string.
     *
     * <p>Because every weight is encoded as a fixed-width 6-digit group, plain
     * lexicographic {@link String#compareTo(String)} on the result reproduces
     * the collation order — suitable for {@code keyword} sorting/aggregations.
     *
     * @param text input text (may be {@code null}, treated as empty)
     * @return hexadecimal collation key
     */
    public String hexKey(String text) {
        byte[] bytes = key(text);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX[(b >>> 4) & 0x0F]).append(HEX[b & 0x0F]);
        }
        return sb.toString();
    }

    private int[] weights(String text) {
        String normalized = decompose(text);
        List<Integer> out = new ArrayList<>(normalized.length() + 4);
        int i = 0;
        int n = normalized.length();
        while (i < n) {
            char ch = normalized.charAt(i);
            if (isLeadingVowel(ch) && i + 1 < n && isConsonant(normalized.charAt(i + 1))) {
                char consonant = normalized.charAt(i + 1);
                addWeight(out, consonant);
                addWeight(out, ch);
                i += 2;
            } else {
                addWeight(out, ch);
                i++;
            }
        }
        if (strength == Strength.IDENTICAL) {
            // Final tiebreaker: distinguish raw code-point sequences (e.g. mark order).
            for (int j = 0; j < normalized.length(); j++) {
                out.add(OTHER_BASE + normalized.charAt(j));
            }
        }
        int[] result = new int[out.size()];
        for (int k = 0; k < result.length; k++) {
            result[k] = out.get(k);
        }
        return result;
    }

    private void addWeight(List<Integer> out, char ch) {
        if (isSkipped(ch)) {
            return;
        }
        out.add(weight(ch));
    }

    private boolean isSkipped(char ch) {
        switch (strength) {
            case PRIMARY:
                return isToneMark(ch) || isOtherMark(ch);
            case SECONDARY:
                return isOtherMark(ch);
            case TERTIARY:
            case IDENTICAL:
            default:
                return false;
        }
    }

    private static boolean isToneMark(char ch) {
        return ch >= TONE_START && ch <= TONE_END;
    }

    private static boolean isOtherMark(char ch) {
        return ch >= MARK_START && ch <= MARK_END;
    }

    private static boolean isLeadingVowel(char ch) {
        return ch >= LEADING_VOWEL_START && ch <= LEADING_VOWEL_END;
    }

    private static boolean isConsonant(char ch) {
        return (ch >= CONSONANT_START && ch <= CONSONANT_END) || ch == PAIYANNOI;
    }

    private static int weight(char ch) {
        if (ch >= CONSONANT_START && ch <= CONSONANT_END) {
            return CONSONANT_BASE + (ch - CONSONANT_START);
        }
        if (ch == PAIYANNOI) {
            return CONSONANT_BASE + (PAIYANNOI - CONSONANT_START);
        }
        if (isLeadingVowel(ch)) {
            return LEADING_VOWEL_BASE + (ch - LEADING_VOWEL_START);
        }
        switch (ch) {
            case '\u0E30': // Sara A
            case '\u0E31': // Mai Han Akat
            case '\u0E32': // Sara Aa
            case '\u0E33': // Sara Am
            case '\u0E34': // Sara I
            case '\u0E35': // Sara Ii
            case '\u0E36': // Sara Ue
            case '\u0E37': // Sara Uee
            case '\u0E38': // Sara U
            case '\u0E39': // Sara Uu
            case '\u0E45': // Lakkhang Yao
                return FOLLOWING_VOWEL_BASE + followingVowelIndex(ch);
            case '\u0E46': // Mai Yamok
                return OTHER_BASE + REPETITION_MARK;
            default:
                break;
        }
        if (isToneMark(ch)) {
            return TONE_BASE + (ch - TONE_START);
        }
        if (isOtherMark(ch)) {
            return MARK_BASE + (ch - MARK_START);
        }
        if (ch >= DIGIT_START && ch <= DIGIT_END) {
            return DIGIT_BASE + (ch - DIGIT_START);
        }
        return OTHER_BASE + ch;
    }

    private static int followingVowelIndex(char ch) {
        if (ch <= '\u0E39') {
            return ch - '\u0E30';
        }
        return 10;
    }

    private String decompose(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (decomposition == Decomposition.NONE || text.indexOf(SARA_AM) < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 4);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == SARA_AM) {
                sb.append(NIKHAHIT).append(SARA_AA);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
