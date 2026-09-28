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
import org.opensearch.analysis.thai.ThaiCollationKey.Decomposition;
import org.opensearch.analysis.thai.ThaiCollationKey.Strength;

import java.io.IOException;

/**
 * Token filter that encodes Thai tokens into sortable Collation Key hex representations
 * according to the Royal Institute of Thailand dictionary ordering rules.
 *
 * <p>Solves the classic Thai sorting issue where leading vowels ({@code เ-, แ-, โ-, ใ-, ไ-})
 * are sorted after consonants in binary UTF-8. By applying Thai collation rules, leading
 * vowels are properly reordered after initial consonants (e.g. {@code "เกาะ"} sorts
 * between {@code "กบ"} and {@code "ขวด"}).
 *
 * <p>Ordering is produced by {@link ThaiCollationKey}, a self-contained implementation
 * that does not depend on the JVM's pluggable {@link java.text.Collator} locale
 * provider. The resulting keys are therefore identical on every JDK and locale,
 * making index sorting and aggregations reproducible across nodes.
 *
 * <p>Compatible with OpenSearch Normalizers on {@code keyword} fields for fast,
 * ICU-free Thai index sorting and aggregations.
 */
public final class ThaiCollationFilter extends TokenFilter {

    private final ThaiCollationKey collationKey;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    /**
     * Constructs a new {@link ThaiCollationFilter}.
     *
     * @param input        input token stream
     * @param collationKey configured collation key generator
     */
    public ThaiCollationFilter(TokenStream input, ThaiCollationKey collationKey) {
        super(input);
        this.collationKey = collationKey;
    }

    /**
     * Convenience constructor using the default {@link Strength#TERTIARY} strength
     * and {@link Decomposition#NONE} decomposition.
     *
     * @param input input token stream
     */
    public ThaiCollationFilter(TokenStream input) {
        this(input, new ThaiCollationKey(Strength.TERTIARY, Decomposition.NONE));
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }

        String hex = collationKey.hexKey(termAtt.toString());
        termAtt.setEmpty();
        termAtt.append(hex);
        return true;
    }
}
