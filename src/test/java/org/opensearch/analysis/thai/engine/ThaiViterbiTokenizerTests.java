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
package org.opensearch.analysis.thai.engine;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ThaiViterbiTokenizer} (engine only, no Lucene layer).
 */
public class ThaiViterbiTokenizerTests {

    private static final ThaiTrie TRIE;

    static {
        try {
            TRIE = ThaiDictionaryLoader.loadDefault();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ThaiViterbiTokenizer tok() {
        return new ThaiViterbiTokenizer(TRIE);
    }

    @Test
    public void testSingleWord() {
        List<String> tokens = tok().tokenize("สวัสดี", false);
        assertEquals(List.of("สวัสดี"), tokens);
    }

    @Test
    public void testCompoundWord() {
        List<String> tokens = tok().tokenize("คนไข้", false);
        // Should be one token (compound in dictionary)
        assertEquals(List.of("คนไข้"), tokens);
    }

    @Test
    public void testShortPhrase() {
        List<String> tokens = tok().tokenize("กินข้าว", false);
        assertFalse("Expected non-empty tokenization", tokens.isEmpty());
    }

    @Test
    public void testMixedContent() {
        List<String> tokens = tok().tokenize("ภาษาไทย123abc", false);
        // Should have Thai tokens + number + Latin
        assertTrue("Expected ≥ 3 tokens, got " + tokens, tokens.size() >= 3);
    }

    @Test
    public void testWhitespaceStripping() {
        List<String> tokens = tok().tokenize("สวัสดี ครับ", false);
        for (String t : tokens) {
            assertFalse("Whitespace token should be stripped: '" + t + "'", t.isBlank());
        }
    }

    @Test
    public void testKeepWhitespace() {
        List<String> tokens = tok().tokenize("สวัสดี ครับ", true);
        boolean hasWs = tokens.stream().anyMatch(String::isBlank);
        assertTrue("Expected whitespace token when keepWhitespace=true", hasWs);
    }

    @Test
    public void testEmptyString() {
        List<String> tokens = tok().tokenize("", false);
        assertTrue(tokens.isEmpty());
    }

    @Test
    public void testNullInput() {
        List<String> tokens = tok().tokenize(null, false);
        assertTrue(tokens.isEmpty());
    }

    @Test
    public void testOovFallback() {
        // Invented word — should still return something
        List<String> tokens = tok().tokenize("ฟลีบโลก", false);
        assertFalse("OOV text should produce tokens", tokens.isEmpty());
        // All tokens together should reconstitute the original
        assertEquals("ฟลีบโลก", String.join("", tokens));
    }

    @Test
    public void testNormalization_DoubleEToAe() {
        // เ+เ → แ normalization allows matching dictionary "แมว", returning original slice "เเมว" as 1 token
        List<String> tokens = tok().tokenize("เเมว", false);
        assertEquals(List.of("เเมว"), tokens);
    }

    @Test
    public void testEnglishPassthrough() {
        List<String> tokens = tok().tokenize("OpenSearch", false);
        assertEquals(List.of("OpenSearch"), tokens);
    }

    @Test
    public void testLongSentence() {
        String text = "วันนี้ฉันไปซื้อของที่ตลาดนัดแล้วก็กลับบ้าน";
        List<String> tokens = tok().tokenize(text, false);
        // Reconstruction check
        assertEquals(text, String.join("", tokens));
        assertTrue("Expected ≥ 5 tokens for long sentence", tokens.size() >= 5);
    }

    @Test
    public void testDecompose() {
        // Compound word with both parts in dictionary: สนามบิน -> สนาม, บิน
        List<String> parts = tok().decompose("สนามบิน");
        assertNotNull("Expected สนามบิน to decompose", parts);
        assertEquals(List.of("สนาม", "บิน"), parts);

        // Another compound word: คนไข้ -> คน, ไข้
        List<String> parts2 = tok().decompose("คนไข้");
        assertNotNull("Expected คนไข้ to decompose", parts2);
        assertEquals(List.of("คน", "ไข้"), parts2);

        // Non-compound word: สวัสดี -> null
        assertNull("Non-compound word should return null", tok().decompose("สวัสดี"));

        // Short words
        assertNull(tok().decompose("กบ"));
        assertNull(tok().decompose(null));
    }
}
