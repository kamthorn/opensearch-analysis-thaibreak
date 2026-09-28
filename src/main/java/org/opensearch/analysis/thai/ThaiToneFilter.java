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

/**
 * Token filter that strips Thai tone marks (ไม้เอก, ไม้โท, ไม้ตรี, ไม้จัตวา) and diacritics
 * (ไม้ไต่คู้, ทัณฑฆาต/การันต์) from Thai tokens, similar to Lucene's {@code ASCIIFoldingFilter}.
 *
 * <p>Enables loose/fuzzy matching for common Thai spelling mistakes and colloquialisms:
 * <ul>
 *   <li>{@code "นะคะ"} vs {@code "นะค่ะ"} &rarr; both match {@code "นะคะ"}</li>
 *   <li>{@code "กะเพรา"} vs {@code "กระเพรา"} &rarr; loose alignment</li>
 *   <li>{@code "มงคล"} vs {@code "มงค็ล"} &rarr; both match {@code "มงคล"}</li>
 * </ul>
 *
 * <p>When {@code keep_original} is {@code true} (default), emits the stripped token
 * as a synonym at position increment 0.
 *
 * <p>Decompound fragments (tokens strictly inside a compound token's span,
 * identified via {@code posLen > 1}) keep their surface form only — no stripped
 * synonym is emitted for them, mirroring {@link ThaiSoundexTokenFilter}.
 */
public final class ThaiToneFilter extends TokenFilter {

    /** Token type assigned to tone-stripped tokens. */
    public static final String TOKEN_TYPE_TONE_STRIPPED = "<TONE_STRIPPED>";

    private final boolean keepOriginal;
    private final boolean stripTones;
    private final boolean stripThanthakhat;

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

    private String pendingStripped = null;
    private int pendingStartOffset = 0;
    private int pendingEndOffset = 0;
    private int pendingPosLen = 1;

    /** End offset of the enclosing compound span, or -1 when outside one. */
    private int graphEndOffset = -1;

    /**
     * Creates a new tone filter with default settings (keep_original=true, strip all).
     *
     * @param input token stream
     */
    public ThaiToneFilter(TokenStream input) {
        this(input, true, true, true);
    }

    /**
     * Creates a new tone filter with custom settings.
     *
     * @param input            token stream
     * @param keepOriginal     whether to emit original token alongside stripped token
     * @param stripTones       whether to strip tone marks (0x0E48 - 0x0E4B) and mai tai khu (0x0E47)
     * @param stripThanthakhat whether to strip thanthakhat / garun (0x0E4C)
     */
    public ThaiToneFilter(TokenStream input, boolean keepOriginal, boolean stripTones, boolean stripThanthakhat) {
        super(input);
        this.keepOriginal = keepOriginal;
        this.stripTones = stripTones;
        this.stripThanthakhat = stripThanthakhat;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (pendingStripped != null) {
            clearAttributes();
            termAtt.append(pendingStripped);
            posIncAtt.setPositionIncrement(0);
            posLenAtt.setPositionLength(pendingPosLen);
            offsetAtt.setOffset(pendingStartOffset, pendingEndOffset);
            typeAtt.setType(TOKEN_TYPE_TONE_STRIPPED);
            pendingStripped = null;
            return true;
        }

        if (!input.incrementToken()) {
            return false;
        }

        int posLen = posLenAtt.getPositionLength();
        int start = offsetAtt.startOffset();
        int end = offsetAtt.endOffset();
        if (posLen > 1) {
            graphEndOffset = end;
        } else {
            if (start >= graphEndOffset) graphEndOffset = -1;
            if (start < graphEndOffset) {
                return true;
            }
        }

        String current = termAtt.toString();
        String stripped = strip(current);

        if (stripped.equals(current)) {
            return true;
        }

        if (keepOriginal) {
            pendingStripped = stripped;
            pendingStartOffset = offsetAtt.startOffset();
            pendingEndOffset = offsetAtt.endOffset();
            pendingPosLen = posLenAtt.getPositionLength();
            return true;
        } else {
            termAtt.setEmpty().append(stripped);
            typeAtt.setType(TOKEN_TYPE_TONE_STRIPPED);
            return true;
        }
    }

    /**
     * Strips tone marks and diacritics according to configured settings.
     *
     * @param text input text
     * @return stripped text
     */
    public String strip(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (stripTones && isToneMark(c)) {
                continue;
            }
            if (stripThanthakhat && (c == 0x0E4C || c == 0x0E4E)) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isToneMark(char c) {
        // ไม้ไต่คู้ (0x0E47), ไม้เอก-ไม้จัตวา (0x0E48 - 0x0E4B)
        return (c >= 0x0E48 && c <= 0x0E4B) || c == 0x0E47;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pendingStripped = null;
        graphEndOffset = -1;
    }
}
