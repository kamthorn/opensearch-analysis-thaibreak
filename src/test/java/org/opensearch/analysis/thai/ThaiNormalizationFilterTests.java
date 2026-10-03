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

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiTrie;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class ThaiNormalizationFilterTests extends BaseTokenStreamTestCase {

    private static final String FORM_B = "นํ้าตาลหวาน"; // นํ้าตาลหวาน

    public void testNormalizesTermAndKeepsOffsets() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("นํ้า เเม่"));
        TokenStream stream = new ThaiNormalizationFilter(source);
        assertTokenStreamContents(
            stream,
            new String[]{"น้ำ", "แม่"},
            new int[]{0, 5},
            new int[]{4, 9}
        );
    }

    public void testKeywordIsNotChanged() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("นํ้า"));
        CharArraySet keywords = new CharArraySet(List.of("นํ้า"), false);
        TokenStream stream = new ThaiNormalizationFilter(new SetKeywordMarkerFilter(source, keywords));
        assertTokenStreamContents(stream, new String[]{"นํ้า"});
    }

    public void testAnalyzerGivesSameTermsForAllSaraAmForms() throws IOException {
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();
        try (ThaiBreakAnalyzer analyzer = new ThaiBreakAnalyzer(trie)) {
            assertAnalyzesTo(analyzer, "น้ำตาลหวาน", new String[]{"น้ำตาล", "หวาน"});
            assertAnalyzesTo(analyzer, FORM_B, new String[]{"น้ำตาล", "หวาน"}, new int[]{0, 7}, new int[]{7, 11});
            assertAnalyzesTo(analyzer, "น้ําตาลหวาน", new String[]{"น้ำตาล", "หวาน"});
            assertAnalyzesTo(analyzer, "กําลังทํางาน", new String[]{"กำลัง", "ทำงาน"});
            // tone mark typed after Sara Am
            assertAnalyzesTo(analyzer, "นำ้ตาลหวาน", new String[]{"น้ำตาล", "หวาน"});
            assertEquals(new BytesRef("น้ำตาล"), analyzer.normalize("f", "นํ้าตาล"));
        }
    }

    public void testAnalyzerNormalizationCanBeDisabled() throws IOException {
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();
        try (ThaiBreakAnalyzer analyzer = new ThaiBreakAnalyzer(trie, null, DecompoundMode.NONE, false)) {
            assertAnalyzesTo(analyzer, FORM_B, new String[]{"นํ้าตาล", "หวาน"});
        }
    }

    public void testPersonAnalyzerNormalizes() throws IOException {
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();
        try (ThaiPersonAnalyzer analyzer = new ThaiPersonAnalyzer(trie, DecompoundMode.NONE, false, false)) {
            assertAnalyzesTo(analyzer, "สําราญ", new String[]{"สำราญ"});
        }
    }
}
