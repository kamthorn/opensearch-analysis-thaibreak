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
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiTrie;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link ThaiToneFilter}.
 */
public class ThaiToneFilterTests extends BaseTokenStreamTestCase {

    public void testToneStrippingKeepOriginalTrue() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("นะค่ะ"));
        TokenStream stream = new ThaiToneFilter(source, true, true, true);
        // Emits original "นะค่ะ" (posInc=1) and synonym "นะคะ" (posInc=0)
        assertTokenStreamContents(
            stream,
            new String[]{"นะค่ะ", "นะคะ"},
            new int[]{0, 0},
            new int[]{5, 5},
            new int[]{1, 0}
        );
    }

    public void testToneStrippingKeepOriginalFalse() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("นะค่ะ"));
        TokenStream stream = new ThaiToneFilter(source, false, true, true);
        assertTokenStreamContents(
            stream,
            new String[]{"นะคะ"},
            new int[]{0},
            new int[]{5},
            new int[]{1}
        );
    }

    public void testNoDuplicateForUntonedWord() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("นะคะ"));
        TokenStream stream = new ThaiToneFilter(source, true, true, true);
        // Already unaccented -> no duplicate emitted
        assertTokenStreamContents(
            stream,
            new String[]{"นะคะ"},
            new int[]{0},
            new int[]{4},
            new int[]{1}
        );
    }

    public void testThanthakhatGarunStripping() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("การ์ด"));
        TokenStream stream = new ThaiToneFilter(source, false, true, true);
        assertTokenStreamContents(
            stream,
            new String[]{"การด"},
            new int[]{0},
            new int[]{5},
            new int[]{1}
        );
    }

    public void testMaiTaiKhuStripping() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("มงค็ล"));
        TokenStream stream = new ThaiToneFilter(source, false, true, true);
        assertTokenStreamContents(
            stream,
            new String[]{"มงคล"},
            new int[]{0},
            new int[]{5},
            new int[]{1}
        );
    }

    public void testNoStrippedSynonymForDecompoundFragments() throws IOException {
        // MIXED mode emits น้ำปลา as a compound graph (น้ำปลา + น้ำ + ปลา).
        // The whole token keeps its stripped synonym (นำปลา); the fragment
        // น้ำ must not emit stripped นำ.
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();
        ThaiBreakTokenizer tok = new ThaiBreakTokenizer(trie, DecompoundMode.MIXED);
        tok.setReader(new StringReader("น้ำปลา"));
        TokenStream stream = new ThaiToneFilter(tok, true, true, true);

        List<String> terms = new ArrayList<>();
        stream.reset();
        org.apache.lucene.analysis.tokenattributes.CharTermAttribute termAtt =
            stream.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
        while (stream.incrementToken()) {
            terms.add(termAtt.toString());
        }
        stream.end();
        stream.close();

        assertTrue(terms.contains("น้ำปลา"));
        assertTrue(terms.contains("นำปลา"));
        assertTrue(terms.contains("น้ำ"));
        assertTrue(terms.contains("ปลา"));
        assertFalse("Fragment น้ำ must not emit stripped นำ", terms.contains("นำ"));
    }
}
