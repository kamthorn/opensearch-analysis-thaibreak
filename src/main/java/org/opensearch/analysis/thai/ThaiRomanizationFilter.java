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
import org.opensearch.analysis.thai.engine.ThaiRomanizer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Token filter that transliterates Thai tokens into Latin characters using the
 * Royal Thai General System of Transcription (RTGS) and curated dictionary lookups.
 *
 * <p>Enables cross-lingual and karaoke search (e.g. typing {@code "krungthep"},
 * {@code "bangkok"}, {@code "phuket"}, or {@code "sawatdi"} finds Thai documents).
 *
 * <p>When {@code keep_original} is {@code true} (default), emits the romanized
 * terms as synonyms at position increment 0.
 */
public final class ThaiRomanizationFilter extends TokenFilter {

    /** Token type assigned to romanized tokens. */
    public static final String TOKEN_TYPE_ROMANIZATION = "<ROMANIZATION>";

    private final ThaiRomanizer romanizer;
    private final boolean keepOriginal;

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

    private final Deque<String> pendingSynonyms = new ArrayDeque<>();
    private int pendingStartOffset = 0;
    private int pendingEndOffset = 0;
    private int pendingPosLen = 1;

    /**
     * Constructs a {@link ThaiRomanizationFilter}.
     *
     * @param input         input token stream
     * @param romanizer     romanization engine
     * @param keepOriginal  whether to keep original Thai token and emit romanization as synonym
     */
    public ThaiRomanizationFilter(TokenStream input, ThaiRomanizer romanizer, boolean keepOriginal) {
        super(input);
        this.romanizer = romanizer != null ? romanizer : ThaiRomanizer.loadDefault();
        this.keepOriginal = keepOriginal;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!pendingSynonyms.isEmpty()) {
            clearAttributes();
            String syn = pendingSynonyms.pollFirst();
            termAtt.setEmpty().append(syn);
            offsetAtt.setOffset(pendingStartOffset, pendingEndOffset);
            posIncAtt.setPositionIncrement(0);
            posLenAtt.setPositionLength(pendingPosLen);
            typeAtt.setType(TOKEN_TYPE_ROMANIZATION);
            return true;
        }

        if (!input.incrementToken()) {
            return false;
        }

        String term = termAtt.toString();
        if (!ThaiRomanizer.hasThai(term)) {
            return true;
        }

        List<String> romanized = romanizer.romanize(term);
        if (romanized == null || romanized.isEmpty()) {
            return true;
        }

        pendingStartOffset = offsetAtt.startOffset();
        pendingEndOffset = offsetAtt.endOffset();
        pendingPosLen = posLenAtt.getPositionLength();

        if (keepOriginal) {
            pendingSynonyms.addAll(romanized);
            return true;
        } else {
            termAtt.setEmpty().append(romanized.get(0));
            typeAtt.setType(TOKEN_TYPE_ROMANIZATION);
            for (int i = 1; i < romanized.size(); i++) {
                pendingSynonyms.add(romanized.get(i));
            }
            return true;
        }
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pendingSynonyms.clear();
    }
}
