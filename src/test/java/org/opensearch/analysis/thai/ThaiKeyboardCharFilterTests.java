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

import org.apache.lucene.analysis.CharFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.junit.BeforeClass;
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiKeyboardConverter;
import org.opensearch.analysis.thai.engine.ThaiTrie;

import java.io.IOException;
import java.io.StringReader;

/**
 * Unit tests for {@link ThaiKeyboardCharFilter}.
 */
public class ThaiKeyboardCharFilterTests extends BaseTokenStreamTestCase {

    private static ThaiTrie trie;

    @BeforeClass
    public static void loadDict() throws IOException {
        trie = ThaiDictionaryLoader.loadDefault();
    }

    public void testCharFilterDirectRead() throws IOException {
        CharFilter filter = new ThaiKeyboardCharFilter(new StringReader("l;ylfu"));
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = filter.read()) != -1) {
            sb.append((char) c);
        }
        assertEquals("สวัสดี", sb.toString());
    }

    public void testCharFilterBufferRead() throws IOException {
        CharFilter filter = new ThaiKeyboardCharFilter(new StringReader("gxHo9ho"));
        char[] buf = new char[16];
        int n = filter.read(buf, 0, buf.length);
        assertEquals("เป็นต้น", new String(buf, 0, n));
    }

    public void testCharFilterIntegrationWithTokenizer() throws IOException {
        // User mistakenly types "สวัสดี" in English keyboard layout
        CharFilter filter = new ThaiKeyboardCharFilter(new StringReader("l;ylfu"));
        Tokenizer tokenizer = new ThaiBreakTokenizer(trie);
        tokenizer.setReader(filter);
        assertTokenStreamContents(
            tokenizer,
            new String[]{"สวัสดี"},
            new int[]{0},
            new int[]{6}
        );
    }

    public void testKedmaneeToQwertyCharFilter() throws IOException {
        CharFilter filter = new ThaiKeyboardCharFilter(
            new StringReader("สวัสดี"),
            ThaiKeyboardConverter.Direction.KEDMANEE_TO_QWERTY
        );
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = filter.read()) != -1) {
            sb.append((char) c);
        }
        assertEquals("l;ylfu", sb.toString());
    }
}
