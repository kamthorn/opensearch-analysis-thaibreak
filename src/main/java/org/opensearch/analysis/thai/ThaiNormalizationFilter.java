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
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.opensearch.analysis.thai.engine.ThaiNormalizer;

import java.io.IOException;

/**
 * Token filter that rewrites Thai spelling variants to one canonical form with
 * {@link ThaiNormalizer}, so that e.g. {@code นํ้าตาล} (Nikhahit + tone mark + Sara Aa) and
 * {@code น้ำตาล} index to the same term.
 *
 * <p>Tokens marked as keywords are left unchanged. Offsets still point at the original text.
 */
public final class ThaiNormalizationFilter extends TokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final KeywordAttribute keywordAtt = addAttribute(KeywordAttribute.class);

    public ThaiNormalizationFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }
        if (!keywordAtt.isKeyword()) {
            termAtt.setLength(ThaiNormalizer.normalize(termAtt.buffer(), termAtt.length()));
        }
        return true;
    }
}
