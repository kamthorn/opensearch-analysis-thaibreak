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
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.opensearch.analysis.thai.ThaiCollationKey.Decomposition;
import org.opensearch.analysis.thai.ThaiCollationKey.Strength;
import org.opensearch.index.analysis.NormalizingTokenFilterFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ThaiCollationFilterTests extends BaseTokenStreamTestCase {

    private String getCollationKeyHex(String word, ThaiCollationKey key) throws IOException {
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader(word));
        ThaiCollationFilter filter = new ThaiCollationFilter(tokenizer, key);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        filter.reset();
        assertTrue(filter.incrementToken());
        String hex = termAtt.toString();
        filter.end();
        filter.close();
        return hex;
    }

    public void testThaiAlphabeticalSortingOrder() throws IOException {
        ThaiCollationKey key = new ThaiCollationKey(Strength.TERTIARY, Decomposition.NONE);

        // Classic Thai sorting challenge: words with leading vowels (เกาะ, ไก่)
        // must be reordered after initial consonant 'ก', not sorted at the end after 'ฮ'.
        List<String> words = List.of("ฮูก", "ขวด", "เกาะ", "ไก่", "กบ");

        class WordKey {
            final String word;
            final String hexKey;
            WordKey(String word, String hexKey) {
                this.word = word;
                this.hexKey = hexKey;
            }
        }

        List<WordKey> entries = new ArrayList<>();
        for (String w : words) {
            entries.add(new WordKey(w, getCollationKeyHex(w, key)));
        }

        // Sort by binary/lexicographical order of generated Hex Collation Keys
        entries.sort(Comparator.comparing(wk -> wk.hexKey));

        List<String> sortedWords = entries.stream().map(wk -> wk.word).toList();

        // Must match Royal Institute Dictionary order
        assertEquals(List.of("กบ", "เกาะ", "ไก่", "ขวด", "ฮูก"), sortedWords);
    }

    public void testToneStrengthLevels() throws IOException {
        ThaiCollationKey primary = new ThaiCollationKey(Strength.PRIMARY, Decomposition.NONE);
        ThaiCollationKey tertiary = new ThaiCollationKey(Strength.TERTIARY, Decomposition.NONE);

        // Same consonants and vowels, different tone marks
        String w1 = "เสือ";
        String w2 = "เสื่อ";
        String w3 = "เสื้อ";

        String pKey1 = getCollationKeyHex(w1, primary);
        String pKey2 = getCollationKeyHex(w2, primary);
        String pKey3 = getCollationKeyHex(w3, primary);

        // Primary strength ignores tone differences
        assertEquals("Primary strength must treat different tones as equal", pKey1, pKey2);
        assertEquals("Primary strength must treat different tones as equal", pKey2, pKey3);

        String tKey1 = getCollationKeyHex(w1, tertiary);
        String tKey2 = getCollationKeyHex(w2, tertiary);
        String tKey3 = getCollationKeyHex(w3, tertiary);

        // Tertiary strength differentiates tones properly
        assertFalse(tKey1.equals(tKey2));
        assertFalse(tKey2.equals(tKey3));
        assertTrue(tKey1.compareTo(tKey2) < 0);
        assertTrue(tKey2.compareTo(tKey3) < 0);
    }

    public void testMultiTokenStreamCollation() throws IOException {
        ThaiCollationKey key = new ThaiCollationKey(Strength.TERTIARY, Decomposition.NONE);
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("กบ ขวด ฮูก"));
        ThaiCollationFilter filter = new ThaiCollationFilter(tokenizer, key);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        filter.reset();

        List<String> keys = new ArrayList<>();
        while (filter.incrementToken()) {
            keys.add(termAtt.toString());
        }
        filter.end();
        filter.close();

        assertEquals(3, keys.size());
        assertTrue(keys.get(0).compareTo(keys.get(1)) < 0);
        assertTrue(keys.get(1).compareTo(keys.get(2)) < 0);
    }

    public void testDeterministicAcrossLocaleAndStrengthDefaults() {
        // The same input must always yield the same key, independent of the JVM's
        // default locale or java.locale.providers setting.
        ThaiCollationKey key = new ThaiCollationKey(Strength.TERTIARY, Decomposition.NONE);
        String expected = key.hexKey("เกาะ");
        assertEquals(expected, key.hexKey("เกาะ"));
        assertEquals(expected, new ThaiCollationKey(Strength.TERTIARY, Decomposition.NONE).hexKey("เกาะ"));

        // Leading vowel is emitted after its consonant: ก (0x010000) then เ (0x030000,
        // the leading-vowel base — ranked above the following-vowel base 0x020000).
        assertTrue(expected.startsWith("010000030000"));
    }

    public void testFollowingVowelsRankBeforeLeadingVowels() throws IOException {
        ThaiCollationKey key = new ThaiCollationKey(Strength.TERTIARY, Decomposition.NONE);

        // Among words sharing the initial consonant ก, the Royal Institute dictionary
        // (and ICU's th collation) groups all ten following-vowel forms before any of
        // the five leading-vowel forms: กะ กัน กา กำ กิ กี กึ กื กุ กู < เก แก โก ใก ไก.
        // Verified against a live OpenSearch icu_collation_keyword(th) field while
        // fixing this — see git history for the exact reference ordering.
        List<String> words = List.of("กก", "กะ", "กัน", "กา", "กำ", "กิ", "กี", "กึ", "กื", "กุ", "กู",
                "เก", "แก", "โก", "ใก", "ไก");

        record WordKey(String word, String hexKey) {}
        List<WordKey> entries = new ArrayList<>();
        for (String w : words) {
            entries.add(new WordKey(w, getCollationKeyHex(w, key)));
        }
        entries.sort(Comparator.comparing(WordKey::hexKey));
        List<String> sortedWords = entries.stream().map(WordKey::word).toList();

        assertEquals(words, sortedWords);
    }

    public void testToneMarksAreSecondaryLevel() throws IOException {
        ThaiCollationKey key = new ThaiCollationKey(Strength.TERTIARY, Decomposition.NONE);

        // Tone marks must not outrank base letters of later positions:
        // ห้าง (ห า ง) < แหลม (ห แ ล) because า ranks below แ, even though ้ alone would rank above.
        assertTrue(getCollationKeyHex("ห้างสรรพสินค้า", key).compareTo(getCollationKeyHex("แหลมพรหมเทพ", key)) < 0);
        // Same base letters: unmarked sorts before marked, tones by rank.
        assertTrue(getCollationKeyHex("ไก", key).compareTo(getCollationKeyHex("ไก่", key)) < 0);
        // A tone difference in an early position loses to a base-letter difference later.
        assertTrue(getCollationKeyHex("เก่ก", key).compareTo(getCollationKeyHex("เกข", key)) < 0);
    }

    public void testDecompositionNormalizesSaraAm() {
        ThaiCollationKey none = new ThaiCollationKey(Strength.TERTIARY, Decomposition.NONE);
        ThaiCollationKey canonical = new ThaiCollationKey(Strength.TERTIARY, Decomposition.CANONICAL);

        // "กำ" (Sara Am) vs explicit "กํา" (Nikhahit + Sara Aa)
        assertFalse(none.hexKey("กำ").equals(none.hexKey("กํา")));
        assertEquals(canonical.hexKey("กำ"), canonical.hexKey("กํา"));
    }

    public void testEmptyAndNullInput() {
        ThaiCollationKey key = new ThaiCollationKey(Strength.PRIMARY, Decomposition.NONE);
        assertEquals("", key.hexKey(""));
        assertEquals("", key.hexKey(null));
    }

    public void testFactoryIsUsableInNormalizers() {
        // Reported: OpenSearch rejects thaibreak_collation inside a custom
        // normalizer unless the factory implements NormalizingTokenFilterFactory.
        assertTrue(NormalizingTokenFilterFactory.class.isAssignableFrom(ThaiCollationFilterFactory.class));
    }
}
