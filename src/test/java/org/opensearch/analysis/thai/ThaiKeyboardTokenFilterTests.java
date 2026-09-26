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
import org.opensearch.analysis.thai.engine.ThaiKeyboardConverter;

import java.io.IOException;
import java.io.StringReader;

/**
 * Unit tests for {@link ThaiKeyboardTokenFilter}.
 */
public class ThaiKeyboardTokenFilterTests extends BaseTokenStreamTestCase {

    public void testKeepOriginalTrue() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("l;ylfu"));
        TokenStream stream = new ThaiKeyboardTokenFilter(
            source,
            ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE,
            true,
            1
        );
        // Should emit original "l;ylfu" (posInc=1) and synonym "สวัสดี" (posInc=0)
        assertTokenStreamContents(
            stream,
            new String[]{"l;ylfu", "สวัสดี"},
            new int[]{0, 0},
            new int[]{6, 6},
            new int[]{1, 0}
        );
    }

    public void testKeepOriginalFalse() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("l;ylfu"));
        TokenStream stream = new ThaiKeyboardTokenFilter(
            source,
            ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE,
            false,
            1
        );
        // Replaces original token directly
        assertTokenStreamContents(
            stream,
            new String[]{"สวัสดี"},
            new int[]{0},
            new int[]{6},
            new int[]{1}
        );
    }

    public void testNoDuplicateForUnchangedTerm() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("สวัสดี"));
        TokenStream stream = new ThaiKeyboardTokenFilter(
            source,
            ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE,
            true,
            1
        );
        // Already Thai; conversion does nothing -> no duplicate synonym emitted
        assertTokenStreamContents(
            stream,
            new String[]{"สวัสดี"},
            new int[]{0},
            new int[]{6},
            new int[]{1}
        );
    }

    public void testBidirectionalBoth() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("l;ylfu สวัสดี"));
        TokenStream stream = new ThaiKeyboardTokenFilter(
            source,
            ThaiKeyboardConverter.Direction.BOTH,
            true,
            1
        );
        // First token "l;ylfu" -> emits "l;ylfu" + "สวัสดี" (posInc=0)
        // Second token "สวัสดี" -> emits "สวัสดี" + "l;ylfu" (posInc=0)
        assertTokenStreamContents(
            stream,
            new String[]{"l;ylfu", "สวัสดี", "สวัสดี", "l;ylfu"},
            new int[]{0, 0, 7, 7},
            new int[]{6, 6, 13, 13},
            new int[]{1, 0, 1, 0}
        );
    }

    public void testMinTermLength() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("a bc"));
        TokenStream stream = new ThaiKeyboardTokenFilter(
            source,
            ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE,
            false,
            2 // min_term_length = 2; "a" should be skipped
        );
        // "a" kept as is, "bc" -> "ิแ"
        assertTokenStreamContents(
            stream,
            new String[]{"a", "ิแ"},
            new int[]{0, 2},
            new int[]{1, 4},
            new int[]{1, 1}
        );
    }
}
