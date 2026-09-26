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
import org.opensearch.analysis.thai.engine.ThaiSoundex;

import java.io.IOException;

/**
 * Token filter that computes Thai phonetic signatures using the Udom83 soundex algorithm.
 *
 * <p>When {@code keep_original} is {@code true} (default), emits the soundex code
 * as a synonym at position increment 0.
 */
public final class ThaiSoundexTokenFilter extends TokenFilter {

    /** Token type assigned to soundex/phonetic tokens. */
    public static final String TOKEN_TYPE_PHONETIC = "<PHONETIC>";

    private final boolean keepOriginal;

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

    private String pendingSoundex = null;
    private int pendingStartOffset = 0;
    private int pendingEndOffset = 0;
    private int pendingPosLen = 1;

    /**
     * Creates a soundex token filter with default settings (keep original = true).
     *
     * @param input token stream
     */
    public ThaiSoundexTokenFilter(TokenStream input) {
        this(input, true);
    }

    /**
     * Creates a soundex token filter with configurable keep_original behavior.
     *
     * @param input        token stream
     * @param keepOriginal whether to retain original token alongside phonetic code
     */
    public ThaiSoundexTokenFilter(TokenStream input, boolean keepOriginal) {
        super(input);
        this.keepOriginal = keepOriginal;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (pendingSoundex != null) {
            clearAttributes();
            termAtt.append(pendingSoundex);
            posIncAtt.setPositionIncrement(0);
            posLenAtt.setPositionLength(pendingPosLen);
            offsetAtt.setOffset(pendingStartOffset, pendingEndOffset);
            typeAtt.setType(TOKEN_TYPE_PHONETIC);
            pendingSoundex = null;
            return true;
        }

        if (!input.incrementToken()) {
            return false;
        }

        String current = termAtt.toString();
        if (!ThaiKeyboardConverter.containsThai(current)) {
            return true;
        }

        String code = ThaiSoundex.udom83(current);
        if (code.isEmpty() || code.equals(current)) {
            return true;
        }

        if (keepOriginal) {
            pendingSoundex = code;
            pendingStartOffset = offsetAtt.startOffset();
            pendingEndOffset = offsetAtt.endOffset();
            pendingPosLen = posLenAtt.getPositionLength();
            return true;
        } else {
            termAtt.setEmpty().append(code);
            typeAtt.setType(TOKEN_TYPE_PHONETIC);
            return true;
        }
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pendingSoundex = null;
    }
}
