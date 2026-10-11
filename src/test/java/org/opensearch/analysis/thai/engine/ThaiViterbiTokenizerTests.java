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

import org.apache.lucene.tests.util.LuceneTestCase;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ThaiViterbiTokenizer} (engine only, no Lucene layer).
 */
public class ThaiViterbiTokenizerTests extends LuceneTestCase {

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

    public void testSingleWord() {
        List<String> tokens = tok().tokenize("สวัสดี", false);
        assertEquals(List.of("สวัสดี"), tokens);
    }

    public void testCompoundWord() {
        List<String> tokens = tok().tokenize("คนไข้", false);
        // Should be one token (compound in dictionary)
        assertEquals(List.of("คนไข้"), tokens);
    }

    public void testShortPhrase() {
        List<String> tokens = tok().tokenize("กินข้าว", false);
        assertFalse("Expected non-empty tokenization", tokens.isEmpty());
    }

    public void testMixedContent() {
        List<String> tokens = tok().tokenize("ภาษาไทย123abc", false);
        // Should have Thai tokens + number + Latin
        assertTrue("Expected ≥ 3 tokens, got " + tokens, tokens.size() >= 3);
    }

    public void testWhitespaceStripping() {
        List<String> tokens = tok().tokenize("สวัสดี ครับ", false);
        for (String t : tokens) {
            assertFalse("Whitespace token should be stripped: '" + t + "'", t.isBlank());
        }
    }

    public void testKeepWhitespace() {
        List<String> tokens = tok().tokenize("สวัสดี ครับ", true);
        boolean hasWs = tokens.stream().anyMatch(String::isBlank);
        assertTrue("Expected whitespace token when keepWhitespace=true", hasWs);
    }

    public void testEmptyString() {
        List<String> tokens = tok().tokenize("", false);
        assertTrue(tokens.isEmpty());
    }

    public void testNullInput() {
        List<String> tokens = tok().tokenize(null, false);
        assertTrue(tokens.isEmpty());
    }

    public void testOovFallback() {
        // Invented word — should still return something
        List<String> tokens = tok().tokenize("ฟลีบโลก", false);
        assertFalse("OOV text should produce tokens", tokens.isEmpty());
        // All tokens together should reconstitute the original
        assertEquals("ฟลีบโลก", String.join("", tokens));
    }

    public void testNormalization_DoubleEToAe() {
        // เ+เ → แ normalization allows matching dictionary "แมว", returning original slice "เเมว" as 1 token
        List<String> tokens = tok().tokenize("เเมว", false);
        assertEquals(List.of("เเมว"), tokens);
    }

    public void testNormalization_SaraAmForms() {
        // All spellings of น้ำ match the dictionary; tokens keep the original characters
        for (String water : new String[]{
                "น้ำ",        // canonical น้ำ
                "นํ้า",  // ํ + tone + า
                "น้ํา",  // tone + ํ + า
                "นำ้",        // tone typed after ำ
                "นํา้"}) { // ํ + า + tone
            String sugar = water + "ตาล";
            assertEquals(sugar, List.of(sugar, "ทราย"), tok().tokenize(sugar + "ทราย", false));
        }
    }

    public void testNormalizeForMatching() {
        assertEquals("น้ำตาล", ThaiViterbiTokenizer.normalizeForMatching("นำ้ตาล"));
        assertEquals("น้ำตาล", ThaiViterbiTokenizer.normalizeForMatching("นํา้ตาล"));
        String canonical = "น้ำตาล";
        assertSame(canonical, ThaiViterbiTokenizer.normalizeForMatching(canonical));
    }

    public void testTccKeepsMaiHanAkatSyllableWhole() {
        // Mai Han-akat is always followed by a final (or -ัวะ), so the syllable is one cluster
        for (String word : new String[]{"ผัวะ", "จั๊วะ", "ยัง", "ยั่ง", "หัว", "สัญ", "กัณฐ์"}) {
            int[] runes = word.codePoints().toArray();
            boolean[] valid = ThaiTCC.validPositions(runes);
            for (int i = 1; i < runes.length; i++) {
                assertFalse(word + " was split at " + i, valid[i]);
            }
        }
    }

    public void testTccKeepsClusterOnsetAfterSaraEWhole() {
        // เ + a true cluster or ห-led onset + -ิ / -ือ / -า is one syllable
        for (String word : new String[]{"เปล่า", "เหล้า", "เหงา", "เพลิง", "เหลือ", "เกลือ", "เครือ"}) {
            int[] runes = word.codePoints().toArray();
            boolean[] valid = ThaiTCC.validPositions(runes);
            for (int i = 1; i < runes.length; i++) {
                assertFalse(word + " was split at " + i, valid[i]);
            }
        }
        // a consonant pair that is not a cluster can still start the next word
        assertTrue(ThaiTCC.validPositions("เทลง".codePoints().toArray())[2]);
        assertTrue(ThaiTCC.validPositions("ทะเลว่า".codePoints().toArray())[4]);
    }

    public void testOutOfVocabularyNameIsKeptWhole() {
        // ชวรัตน์ and ศุภชัย are not dictionary words; they used to be cut into ชว|รัตน์ and ศุภ|ชัย
        assertEquals(List.of("ชวรัตน์"), tok().tokenize("ชวรัตน์", false));
        assertEquals(List.of("ศุภชัย"), tok().tokenize("ศุภชัย", false));
        assertEquals("นาย", tok().tokenize("นายศุภชัยกล่าวว่ามีการประชุม", false).get(0));
        assertEquals("ศุภชัย", tok().tokenize("นายศุภชัยกล่าวว่ามีการประชุม", false).get(1));
    }

    public void testUnknownWordDoesNotSwallowFunctionWords() {
        // ฮิวจ์ส and จินตะ are not dictionary words; they must not absorb the function words next to them
        assertEquals(List.of("มาร์ค", "ฮิวจ์ส", "ไม่", "ได้", "สามารถ"), tok().tokenize("มาร์คฮิวจ์สไม่ได้สามารถ", false));
        List<String> tokens = tok().tokenize("ภานุวัฒน์ จินตะและสุทธินันท์", false);
        assertTrue(tokens.toString(), tokens.contains("และ"));
        assertTrue(tokens.toString(), tokens.contains("จินตะ"));
    }

    public void testUnknownWordDoesNotContainFunctionWords() {
        // ชวรัตน์ is not a dictionary word; it used to swallow ที่ลืมไว้ into one unknown word
        assertEquals(List.of("ชวรัตน์", "ที่", "ลืม", "ไว้"), tok().tokenize("ชวรัตน์ที่ลืมไว้", false));
        // มา may occur inside an unknown word: it is a frequent syllable of transliterated names
        assertEquals("อาห์มาดิเนจัด", tok().tokenize("อาห์มาดิเนจัดกล่าวว่า", false).get(0));
    }

    public void testUnknownWordDoesNotSwallowTitle() {
        // เต๋อ is not a dictionary word; it used to merge with the title before it
        assertEquals(List.of("พี่", "เต๋อ", "มา", "แล้ว"), tok().tokenize("พี่เต๋อมาแล้ว", false));
    }

    public void testDefaultDictionaryHasWordWeights() {
        int[] word = "ที่".codePoints().toArray();
        double weight = 0;
        for (ThaiTrie.PrefixMatch m : TRIE.prefixes(word, 0, word.length)) {
            if (m.end == word.length) weight = m.weight;
        }
        assertTrue("ที่ should carry its word weight, got " + weight, weight > 1.0);
    }

    public void testDictionaryWordsOfUpTo40CharactersAreMatched() {
        // พระบาทสมเด็จพระเจ้าอยู่หัว has 26 characters; the Java engine takes the limit from the dictionary
        String title = "พระบาทสมเด็จพระเจ้าอยู่หัว";
        String long40 = "สมเด็จพระนางเจ้าสิริกิติ์พระบรมราชินีนาถ";
        assertEquals(40, long40.codePointCount(0, long40.length()));
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault().copy();
        trie.add(title, 4.0);
        trie.add(long40, 4.0);
        ThaiViterbiTokenizer custom = new ThaiViterbiTokenizer(trie);
        assertEquals(List.of(title, "เสด็จ"), custom.tokenize(title + "เสด็จ", false));
        assertEquals(List.of(long40, "เสด็จ"), custom.tokenize(long40 + "เสด็จ", false));
    }

    public void testRoyalVocabularyIsOneToken() {
        // from thai-break-dict-extra data/royal: fixed royal terms are single tokens
        assertEquals(List.of("พระชนมพรรษา"), tok().tokenize("พระชนมพรรษา", false));
        assertEquals(List.of("พระบรมวงศานุวงศ์", "เสด็จ"), tok().tokenize("พระบรมวงศานุวงศ์เสด็จ", false));
        assertEquals(List.of("สมเด็จพระเทพรัตนราชสุดา"), tok().tokenize("สมเด็จพระเทพรัตนราชสุดา", false));
        assertEquals(List.of("พระบาทสมเด็จพระเจ้าอยู่หัว"), tok().tokenize("พระบาทสมเด็จพระเจ้าอยู่หัว", false));
    }

    public void testSpelledOutLettersAreOneToken() {
        assertEquals(List.of("เอทีเอ็ม"), tok().tokenize("เอทีเอ็ม", false));
        assertEquals(List.of("ดีเอสไอ"), tok().tokenize("ดีเอสไอ", false));
        assertEquals(List.of("ยูเอ็น", "และ", "ไอที"), tok().tokenize("ยูเอ็นและไอที", false));
        // a single letter name stays a word, and a tone mark keeps ที่ from being the letter T
        assertEquals(List.of("ไอ"), tok().tokenize("ไอ", false));
        assertEquals(List.of("ที่", "ดีเอสไอ"), tok().tokenize("ที่ดีเอสไอ", false));
    }

    public void testWordAndFullStopIsNotAnAbbreviation() {
        // the full stop is a token of its own (the bundled dictionary may or may not have ต่อไป as a word)
        for (String text : new String[]{"ต่อไป.", "หรือไม่.", "ได้."}) {
            List<String> tokens = tok().tokenize(text, false);
            assertEquals(tokens.toString(), ".", tokens.get(tokens.size() - 1));
            assertEquals(text, String.join("", tokens));
        }
        assertEquals(List.of("ของ", "ส.ส."), tok().tokenize("ของส.ส.", false));
        assertEquals(List.of("ของ", "พ.ต.ท."), tok().tokenize("ของพ.ต.ท.", false));
        // real abbreviations stay whole
        assertEquals(List.of("จ.", "เชียงใหม่"), tok().tokenize("จ.เชียงใหม่", false));
        assertEquals(List.of("ก.พ."), tok().tokenize("ก.พ.", false));
    }

    public void testRepetitionMarkIsNotPartOfAnUnknownWord() {
        assertEquals(List.of("อื่น", "ๆ"), tok().tokenize("อื่นๆ", false));
        assertEquals(List.of("ใด", "ๆ"), tok().tokenize("ใดๆ", false));
    }

    public void testEnglishPassthrough() {
        List<String> tokens = tok().tokenize("OpenSearch", false);
        assertEquals(List.of("OpenSearch"), tokens);
    }

    public void testLongSentence() {
        String text = "วันนี้ฉันไปซื้อของที่ตลาดนัดแล้วก็กลับบ้าน";
        List<String> tokens = tok().tokenize(text, false);
        // Reconstruction check
        assertEquals(text, String.join("", tokens));
        assertTrue("Expected ≥ 5 tokens for long sentence", tokens.size() >= 5);
    }

    public void testHyphenatedCompositesSplitSymmetrically() {
        // Reported: searching โควิด found nothing because documents write
        // โควิด-19. Hyphen/number composites are compositional, so the
        // dictionary must not contain them as atomic units: query and
        // documents then split identically and match.
        assertEquals(List.of("โควิด"), tok().tokenize("โควิด", false));
        assertEquals(
            List.of("โรค", "โควิด", "-", "19", "ระบาด"),
            tok().tokenize("โรคโควิด-19ระบาด", false));
    }

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

        // Atomic single morphemes must never decompose (reported bug):
        // ตาราง would split into ตา|ราง, สำหรับ into สำ|หรับ
        assertNull("ตาราง is atomic", tok().decompose("ตาราง"));
        assertNull("สำหรับ is atomic", tok().decompose("สำหรับ"));

        // Short words
        assertNull(tok().decompose("กบ"));
        assertNull(tok().decompose(null));
    }
}
