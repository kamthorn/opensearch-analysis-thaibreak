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

import java.io.IOException;
import java.io.StringReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ThaiCollationFilterTests extends BaseTokenStreamTestCase {

    private String getCollationKeyHex(String word, Collator collator) throws IOException {
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader(word));
        ThaiCollationFilter filter = new ThaiCollationFilter(tokenizer, collator);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        filter.reset();
        assertTrue(filter.incrementToken());
        String hex = termAtt.toString();
        filter.end();
        filter.close();
        return hex;
    }

    public void testThaiAlphabeticalSortingOrder() throws IOException {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("th-TH"));

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
            entries.add(new WordKey(w, getCollationKeyHex(w, collator)));
        }

        // Sort by binary/lexicographical order of generated Hex Collation Keys
        entries.sort(Comparator.comparing(wk -> wk.hexKey));

        List<String> sortedWords = entries.stream().map(wk -> wk.word).toList();

        // Must match Royal Institute Dictionary order
        assertEquals(List.of("กบ", "เกาะ", "ไก่", "ขวด", "ฮูก"), sortedWords);
    }

    public void testToneStrengthLevels() throws IOException {
        Collator primaryCollator = Collator.getInstance(Locale.forLanguageTag("th-TH"));
        primaryCollator.setStrength(Collator.PRIMARY);

        Collator tertiaryCollator = Collator.getInstance(Locale.forLanguageTag("th-TH"));
        tertiaryCollator.setStrength(Collator.TERTIARY);

        // Same consonants and vowels, different tone marks
        String w1 = "เสือ";
        String w2 = "เสื่อ";
        String w3 = "เสื้อ";

        String pKey1 = getCollationKeyHex(w1, primaryCollator);
        String pKey2 = getCollationKeyHex(w2, primaryCollator);
        String pKey3 = getCollationKeyHex(w3, primaryCollator);

        // Primary strength ignores tone differences
        assertEquals("Primary strength must treat different tones as equal", pKey1, pKey2);
        assertEquals("Primary strength must treat different tones as equal", pKey2, pKey3);

        String tKey1 = getCollationKeyHex(w1, tertiaryCollator);
        String tKey2 = getCollationKeyHex(w2, tertiaryCollator);
        String tKey3 = getCollationKeyHex(w3, tertiaryCollator);

        // Tertiary strength differentiates tones properly
        assertFalse(tKey1.equals(tKey2));
        assertFalse(tKey2.equals(tKey3));
        assertTrue(tKey1.compareTo(tKey2) < 0);
        assertTrue(tKey2.compareTo(tKey3) < 0);
    }

    public void testMultiTokenStreamCollation() throws IOException {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("th-TH"));
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("กบ ขวด ฮูก"));
        ThaiCollationFilter filter = new ThaiCollationFilter(tokenizer, collator);

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
}
