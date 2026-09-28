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

import java.io.IOException;
import java.text.CollationKey;
import java.text.Collator;

/**
 * Token filter that encodes Thai tokens into sortable Collation Key hex representations
 * according to the Royal Institute of Thailand dictionary ordering rules.
 *
 * <p>Solves the classic Thai sorting issue where leading vowels ({@code เ-, แ-, โ-, ใ-, ไ-})
 * are sorted after consonants in binary UTF-8. By applying Thai collation rules, leading
 * vowels are properly reordered after initial consonants (e.g. {@code "เกาะ"} sorts
 * between {@code "กบ"} and {@code "ขวด"}).
 *
 * <p>Compatible with OpenSearch Normalizers on {@code keyword} fields for fast,
 * ICU-free Thai index sorting and aggregations.
 */
public final class ThaiCollationFilter extends TokenFilter {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private final Collator collator;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    /**
     * Constructs a new {@link ThaiCollationFilter}.
     *
     * @param input    input token stream
     * @param collator configured Thai Collator instance
     */
    public ThaiCollationFilter(TokenStream input, Collator collator) {
        super(input);
        this.collator = collator;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }

        String text = termAtt.toString();
        CollationKey key = collator.getCollationKey(text);
        byte[] bytes = key.toByteArray();

        termAtt.setEmpty();
        termAtt.resizeBuffer(bytes.length * 2);
        char[] buf = termAtt.buffer();
        int idx = 0;
        for (byte b : bytes) {
            buf[idx++] = HEX_DIGITS[(b >> 4) & 0x0F];
            buf[idx++] = HEX_DIGITS[b & 0x0F];
        }
        termAtt.setLength(bytes.length * 2);

        return true;
    }
}
