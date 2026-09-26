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
import org.opensearch.analysis.thai.engine.ThaiKeyboardConverter;

import java.io.IOException;

/**
 * Token filter that converts tokens mis-typed in US-QWERTY or Thai Kedmanee layout.
 *
 * <p>When {@code keep_original} is {@code true} (default), emits the converted term
 * as a synonym at position increment 0.
 */
public final class ThaiKeyboardTokenFilter extends TokenFilter {

    /** Token type assigned to converted keyboard tokens. */
    public static final String TOKEN_TYPE_KEYBOARD = "<KEYBOARD_CORRECTED>";

    private final ThaiKeyboardConverter.Direction direction;
    private final boolean keepOriginal;
    private final int minTermLength;

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

    private String pendingConverted = null;
    private int pendingStartOffset = 0;
    private int pendingEndOffset = 0;
    private int pendingPosLen = 1;

    /**
     * Creates a keyboard token filter with default settings (QWERTY &rarr; Kedmanee, keep original).
     *
     * @param input token stream
     */
    public ThaiKeyboardTokenFilter(TokenStream input) {
        this(input, ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE, true, 1);
    }

    /**
     * Creates a keyboard token filter with custom settings.
     *
     * @param input         token stream
     * @param direction     layout conversion direction
     * @param keepOriginal  whether to emit original token alongside converted token
     * @param minTermLength minimum term length to convert
     */
    public ThaiKeyboardTokenFilter(
        TokenStream input,
        ThaiKeyboardConverter.Direction direction,
        boolean keepOriginal,
        int minTermLength
    ) {
        super(input);
        this.direction = direction == null ? ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE : direction;
        this.keepOriginal = keepOriginal;
        this.minTermLength = Math.max(1, minTermLength);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (pendingConverted != null) {
            clearAttributes();
            termAtt.append(pendingConverted);
            posIncAtt.setPositionIncrement(0);
            posLenAtt.setPositionLength(pendingPosLen);
            offsetAtt.setOffset(pendingStartOffset, pendingEndOffset);
            typeAtt.setType(TOKEN_TYPE_KEYBOARD);
            pendingConverted = null;
            return true;
        }

        if (!input.incrementToken()) {
            return false;
        }

        if (termAtt.length() < minTermLength) {
            return true;
        }

        String currentTerm = termAtt.toString();
        String converted = convertTerm(currentTerm);

        if (converted.equals(currentTerm)) {
            return true;
        }

        if (keepOriginal) {
            pendingConverted = converted;
            pendingStartOffset = offsetAtt.startOffset();
            pendingEndOffset = offsetAtt.endOffset();
            pendingPosLen = posLenAtt.getPositionLength();
            return true;
        } else {
            termAtt.setEmpty().append(converted);
            typeAtt.setType(TOKEN_TYPE_KEYBOARD);
            return true;
        }
    }

    private String convertTerm(String term) {
        return switch (direction) {
            case KEDMANEE_TO_QWERTY -> ThaiKeyboardConverter.toQwerty(term);
            case QWERTY_TO_KEDMANEE -> ThaiKeyboardConverter.toKedmanee(term);
            case BOTH -> {
                if (ThaiKeyboardConverter.containsThai(term)) {
                    yield ThaiKeyboardConverter.toQwerty(term);
                } else {
                    yield ThaiKeyboardConverter.toKedmanee(term);
                }
            }
        };
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pendingConverted = null;
    }
}
