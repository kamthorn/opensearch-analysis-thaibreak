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
    // Long input (regression: must not corrupt tokens at internal read-buffer
    // boundaries, e.g. the old fixed 4096-char chunk split)
    // -----------------------------------------------------------------------

    public void testLongInputDoesNotCorruptTokenAtOldChunkBoundary() throws IOException {
        String filler = "การทดสอบระบบตัดคำภาษาไทยที่ยาวมากเพื่อดูว่าการแบ่งชิ้นข้อความจะทำให้เกิดปัญหาหรือไม่ ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 4090) {
            sb.append(filler);
        }
        sb.setLength(4090); // land the compound word right across the old 4096 boundary
        sb.append("โรงพยาบาลศิริราช");
        sb.append(filler);

        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader(sb.toString()));
        String[] tokens = consumeTokens(tok);

        boolean intact = false;
        for (String t : tokens) {
            if (t.equals("โรงพยาบาลศิริราช") || t.equals("โรงพยาบาล")) {
                intact = true;
                break;
            }
        }
        assertTrue("Expected โรงพยาบาล(ศิริราช) to survive intact across a >4096 char input, got: "
            + java.util.Arrays.toString(tokens), intact);

        // No single isolated Thai consonant tokens (a symptom of a mid-cluster chunk cut)
        for (String t : tokens) {
            assertFalse("Unexpected single-character garbage token: " + t, t.codePointCount(0, t.length()) == 1
                && t.codePointAt(0) >= 0x0E01 && t.codePointAt(0) <= 0x0E2E);
        }
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

    public void testMultiChunkSafeStreaming() throws IOException {
        // Build a 50,000-character realistic Thai text spanning >6 chunks of 8KB
        String paragraph = "การพัฒนาเทคโนโลยีการสืบค้นข้อมูลภาษาไทยสำหรับองค์กรขนาดใหญ่จำเป็นต้องมีความแม่นยำสูง "
            + "และการจัดการหน่วยความจำที่มีประสิทธิภาพเพื่อป้องกันปัญหาหน่วยความจำไม่เพียงพอเมื่อประมวลผลเอกสารขนาดใหญ่ ";
        StringBuilder sb = new StringBuilder();
        int markerIndex = 1;
        while (sb.length() < 50_000) {
            sb.append(paragraph);
            sb.append("หมุดหมายที่").append(markerIndex++).append(" ");
        }

        Tokenizer tok = newTokenizer();
        tok.setReader(new StringReader(sb.toString()));
        tok.reset();

        org.apache.lucene.analysis.tokenattributes.CharTermAttribute termAtt =
            tok.getAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
        org.apache.lucene.analysis.tokenattributes.OffsetAttribute offsetAtt =
            tok.getAttribute(org.apache.lucene.analysis.tokenattributes.OffsetAttribute.class);

        int prevStart = 0;
        int tokenCount = 0;
        while (tok.incrementToken()) {
            tokenCount++;
            int start = offsetAtt.startOffset();
            int end = offsetAtt.endOffset();
            assertTrue("Token start offset must not decrease: " + start + " < " + prevStart, start >= prevStart);
            assertTrue("Token end must be >= start: " + start + " > " + end, end >= start);
            assertTrue("Token length must match offset diff", termAtt.length() <= (end - start));
            prevStart = start;
        }
        tok.end();
        assertEquals("Final stream offset must equal total input length", sb.length(), offsetAtt.endOffset());
        tok.close();

        assertTrue("Expected thousands of tokens for 50KB text, got: " + tokenCount, tokenCount > 3000);
    }

    public void testAtomicWordsStayWholeInMixedMode() throws IOException {
        // Reported bug: ตาราง decomposed into ตา|ราง, สำหรับ into สำ|หรับ.
        // Atomic single morphemes must survive MIXED mode as single tokens.
        for (String word : new String[]{"ตาราง", "สำหรับ"}) {
            Tokenizer tok = new ThaiBreakTokenizer(trie, DecompoundMode.MIXED);
            tok.setReader(new StringReader(word));
            assertTokenStreamContents(
                tok,
                new String[]{word},
                new int[]{0},
                new int[]{word.length()}
            );
        }
    }

    public void testExtraDictionaryWordsAndDecompound() throws IOException {
        // Test abbreviation from extra dictionary
        Tokenizer tokAbbr = newTokenizer();
        tokAbbr.setReader(new StringReader("ประชุม กมธ. ที่ กทม."));
        assertTokenStreamContents(
            tokAbbr,
            new String[]{"ประชุม", "กมธ.", "ที่", "กทม."}
        );

        // Test compound word from extra dictionary decomposed in MIXED mode
        Tokenizer tokMixed = new ThaiBreakTokenizer(trie, DecompoundMode.MIXED);
        tokMixed.setReader(new StringReader("กรมควบคุมโรค"));
        assertTokenStreamContents(
            tokMixed,
            new String[]{"กรมควบคุมโรค", "กรม", "ควบคุม", "โรค"},
            new int[]{0, 0, 3, 9},
            new int[]{12, 3, 9, 12},
            null,
            new int[]{1, 0, 1, 1},
            new int[]{3, 1, 1, 1},
            12
        );
    }
}
