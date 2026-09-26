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

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.Tokenizer;
import org.junit.BeforeClass;
import static org.junit.Assert.*;
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiTrie;

import java.io.IOException;
import java.io.StringReader;

/**
 * Unit tests for {@link ThaiBreakTokenizer}.
 */
public class ThaiBreakTokenizerTests extends BaseTokenStreamTestCase {

    private static ThaiTrie trie;

    @BeforeClass
    public static void loadDict() throws IOException {
        trie = ThaiDictionaryLoader.loadDefault();
    }

    private Tokenizer newTokenizer() {
        return new ThaiBreakTokenizer(trie);
    }

    // -----------------------------------------------------------------------
    // Basic Thai segmentation
    // -----------------------------------------------------------------------

    public void testBasicSegmentation() throws IOException {
        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader("คนไข้"));
        // Dictionary has คนไข้ → should stay together
        assertTokenStreamContents(tok, new String[]{"คนไข้"});
    }

    public void testPhrase() throws IOException {
        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader("กินข้าวกับเพื่อน"));
        String[] tokens = consumeTokens(tok);
        // Should produce ≥ 3 meaningful tokens
        assertTrue("Expected at least 3 tokens, got " + tokens.length, tokens.length >= 3);
    }

    public void testMixedThaLatin() throws IOException {
        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader("ภาษาไทย Thai language"));
        String[] tokens = consumeTokens(tok);
        // Must contain both Thai and Latin tokens
        boolean hasLatin = false;
        for (String t : tokens) {
            if (t.matches("[A-Za-z]+")) { hasLatin = true; break; }
        }
        assertTrue("Expected Latin token", hasLatin);
    }

    public void testMixedThaiAndNumber() throws IOException {
        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader("ราคา100บาท"));
        String[] tokens = consumeTokens(tok);
        assertTrue("Expected at least 3 tokens (ราคา, 100, บาท)", tokens.length >= 3);
    }

    public void testEmptyInput() throws IOException {
        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader(""));
        assertTokenStreamContents(tok, new String[]{});
    }

    public void testPureLatinPassthrough() throws IOException {
        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader("Hello World"));
        String[] tokens = consumeTokens(tok);
        boolean hasHello = false;
        for (String t : tokens) if (t.equals("Hello")) hasHello = true;
        assertTrue("Expected 'Hello' token", hasHello);
    }

    public void testKnownCompoundWord() throws IOException {
        // "คนไข้" should be in dict as a single word
        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader("คนไข้มาถึงแล้ว"));
        String[] tokens = consumeTokens(tok);
        boolean found = false;
        for (String t : tokens) if (t.equals("คนไข้")) found = true;
        assertTrue("Expected คนไข้ as a single token", found);
    }

    public void testResetWorks() throws IOException {
        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader("สวัสดี"));
        assertTokenStreamContents(tok, new String[]{"สวัสดี"});
        tok.setReader(new StringReader("ขอบคุณ"));
        assertTokenStreamContents(tok, new String[]{"ขอบคุณ"});
    }

    // -----------------------------------------------------------------------
    // OOV (out-of-vocabulary) handling
    // -----------------------------------------------------------------------

    public void testOovWord() throws IOException {
        // A random invented word should still come out as some token
        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader("ฟลีบโลก"));
        String[] tokens = consumeTokens(tok);
        assertTrue("Expected at least one token for OOV input", tokens.length >= 1);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static String[] consumeTokens(Tokenizer tok) throws IOException {
        tok.reset();
        java.util.List<String> out = new java.util.ArrayList<>();
        org.apache.lucene.analysis.tokenattributes.CharTermAttribute termAtt =
            tok.getAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
        while (tok.incrementToken()) {
            out.add(termAtt.toString());
        }
        tok.end();
        tok.close();
        return out.toArray(String[]::new);
    }

    // -----------------------------------------------------------------------
    // Decompound Mode tests (NONE, DISCARD, MIXED)
    // -----------------------------------------------------------------------

    public void testDecompoundModeNone() throws IOException {
        Tokenizer tok = new ThaiBreakTokenizer(trie, DecompoundMode.NONE);
        tok.setReader(new StringReader("สนามบิน"));
        assertTokenStreamContents(tok, new String[]{"สนามบิน"}, new int[]{0}, new int[]{7});
    }

    public void testDecompoundModeDiscard() throws IOException {
        Tokenizer tok = new ThaiBreakTokenizer(trie, DecompoundMode.DISCARD);
        tok.setReader(new StringReader("สนามบิน"));
        assertTokenStreamContents(
            tok,
            new String[]{"สนาม", "บิน"},
            new int[]{0, 4},
            new int[]{4, 7},
            new int[]{1, 1}
        );
    }

    public void testDecompoundModeMixed() throws IOException {
        Tokenizer tok = new ThaiBreakTokenizer(trie, DecompoundMode.MIXED);
        tok.setReader(new StringReader("สนามบิน"));
        // Token graph:
        // "สนามบิน" (posInc=1, posLen=2, offset 0..7)
        // "สนาม"    (posInc=0, posLen=1, offset 0..4)
        // "บิน"     (posInc=1, posLen=1, offset 4..7)
        assertTokenStreamContents(
            tok,
            new String[]{"สนามบิน", "สนาม", "บิน"},
            new int[]{0, 0, 4},
            new int[]{7, 4, 7},
            null,
            new int[]{1, 0, 1},
            new int[]{2, 1, 1},
            7
        );
    }
}
