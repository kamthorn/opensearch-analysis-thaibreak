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
import org.opensearch.analysis.thai.engine.ThaiAcronymMap;

import java.io.IOException;

/**
 * Token filter that expands Thai acronyms and abbreviations into their full form
 * as graph synonyms (or vice-versa when {@code bidirectional} is enabled).
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code "กทม."} &rarr; emits {@code "กทม."} (posInc=1) and {@code "กรุงเทพมหานคร"} (posInc=0)</li>
 *   <li>{@code "รพ."} &rarr; emits {@code "รพ."} (posInc=1) and {@code "โรงพยาบาล"} (posInc=0)</li>
 *   <li>{@code "กทม"} &rarr; matches without dots when {@code normalize_dots=true}</li>
 * </ul>
 */
public final class ThaiAcronymFilter extends TokenFilter {

    /** Token type assigned to expanded acronym synonyms. */
    public static final String TOKEN_TYPE_ACRONYM = "<ACRONYM_EXPANDED>";

    private final ThaiAcronymMap acronymMap;
    private final boolean keepOriginal;
    private final boolean bidirectional;
    private final boolean normalizeDots;

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

    private String pendingSynonym = null;
    private int pendingStartOffset = 0;
    private int pendingEndOffset = 0;
    private int pendingPosLen = 1;

    /**
     * Constructs a new {@link ThaiAcronymFilter}.
     *
     * @param input         input token stream
     * @param acronymMap    acronym lookup dictionary
     * @param keepOriginal  whether to keep the original token and emit synonym at posInc=0
     * @param bidirectional whether to also abbreviate full words to acronyms
     * @param normalizeDots whether to match acronyms regardless of dots
     */
    public ThaiAcronymFilter(
            TokenStream input,
            ThaiAcronymMap acronymMap,
            boolean keepOriginal,
            boolean bidirectional,
            boolean normalizeDots
    ) {
        super(input);
        this.acronymMap = acronymMap != null ? acronymMap : ThaiAcronymMap.loadDefault();
        this.keepOriginal = keepOriginal;
        this.bidirectional = bidirectional;
        this.normalizeDots = normalizeDots;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (pendingSynonym != null) {
            clearAttributes();
            termAtt.setEmpty().append(pendingSynonym);
            offsetAtt.setOffset(pendingStartOffset, pendingEndOffset);
            posIncAtt.setPositionIncrement(0);
            posLenAtt.setPositionLength(pendingPosLen);
            typeAtt.setType(TOKEN_TYPE_ACRONYM);
            pendingSynonym = null;
            return true;
        }

        if (!input.incrementToken()) {
            return false;
        }

        String term = termAtt.toString();
        String target = acronymMap.expand(term, normalizeDots);

        if (target == null && bidirectional) {
            target = acronymMap.abbreviate(term);
        }

        if (target != null && !target.equals(term)) {
            if (keepOriginal) {
                pendingSynonym = target;
                pendingStartOffset = offsetAtt.startOffset();
                pendingEndOffset = offsetAtt.endOffset();
                pendingPosLen = posLenAtt.getPositionLength();
                // Return original token now; pendingSynonym will be returned next
                return true;
            } else {
                termAtt.setEmpty().append(target);
                typeAtt.setType(TOKEN_TYPE_ACRONYM);
                return true;
            }
        }

        return true;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pendingSynonym = null;
    }
}
