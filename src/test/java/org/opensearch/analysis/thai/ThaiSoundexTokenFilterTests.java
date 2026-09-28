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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

import java.io.IOException;
import java.io.StringReader;

/**
 * Unit tests for {@link ThaiSoundexTokenFilter}.
 */
public class ThaiSoundexTokenFilterTests extends BaseTokenStreamTestCase {

    public void testKeepOriginalTrue() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("รัก"));
        TokenStream stream = new ThaiSoundexTokenFilter(source, true);
        // Emits original "รัก" (posInc=1) and soundex "ร100000" (posInc=0)
        assertTokenStreamContents(
            stream,
            new String[]{"รัก", "ร100000"},
            new int[]{0, 0},
            new int[]{3, 3},
            new int[]{1, 0}
        );
    }

    public void testKeepOriginalFalse() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("รัก"));
        TokenStream stream = new ThaiSoundexTokenFilter(source, false);
        // Replaces original "รัก" with soundex "ร100000"
        assertTokenStreamContents(
            stream,
            new String[]{"ร100000"},
            new int[]{0},
            new int[]{3},
            new int[]{1}
        );
    }

    public void testNonThaiPassthrough() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("OpenSearch 123"));
        TokenStream stream = new ThaiSoundexTokenFilter(source, true);
        assertTokenStreamContents(
            stream,
            new String[]{"OpenSearch", "123"},
            new int[]{0, 11},
            new int[]{10, 14},
            new int[]{1, 1}
        );
    }

    public void testShortTokensGetNoCode() throws IOException {
        // 1-2 character signatures collide with vast numbers of unrelated
        // words: no code is emitted below min_term_length (default 3).
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("สุ ร"));
        TokenStream stream = new ThaiSoundexTokenFilter(source, true);
        assertTokenStreamContents(
            stream,
            new String[]{"สุ", "ร"},
            new int[]{0, 3},
            new int[]{2, 4},
            new int[]{1, 1}
        );
    }

    public void testMultipleWordsHomophoneMatch() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("การ กาล"));
        TokenStream stream = new ThaiSoundexTokenFilter(source, false);
        // Both become 'ก900000'
        assertTokenStreamContents(
            stream,
            new String[]{"ก900000", "ก900000"},
            new int[]{0, 4},
            new int[]{3, 7},
            new int[]{1, 1}
        );
    }
}
