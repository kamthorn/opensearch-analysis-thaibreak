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
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiSoundex;
import org.opensearch.analysis.thai.engine.ThaiTrie;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ThaiPersonAnalyzerTests extends LuceneTestCase {

    public void testPersonNameSegmentationAndPhonetic() throws IOException {
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();
        ThaiPersonAnalyzer analyzer = new ThaiPersonAnalyzer(trie);

        TokenStream ts = analyzer.tokenStream("name", "สมชาย");
        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncAtt = ts.addAttribute(PositionIncrementAttribute.class);
        PositionLengthAttribute posLenAtt = ts.addAttribute(PositionLengthAttribute.class);

        ts.reset();
        List<String> terms = new ArrayList<>();
        List<Integer> posIncs = new ArrayList<>();
        List<Integer> posLens = new ArrayList<>();

        while (ts.incrementToken()) {
            terms.add(termAtt.toString());
            posIncs.add(posIncAtt.getPositionIncrement());
            posLens.add(posLenAtt.getPositionLength());
        }
        ts.end();
        ts.close();

        // "สมชาย" segmented into "สม" and "ชาย"
        assertTrue("Expected subword 'สม'", terms.contains("สม"));
        assertTrue("Expected subword 'ชาย'", terms.contains("ชาย"));

        // Phonetic codes are emitted for the WHOLE name only: fragment codes
        // (สม, ชาย) would match unrelated queries, so they are suppressed.
        // Original fragment terms stay indexed, preserving exact sub-name search.
        assertTrue("Expected Soundex for whole 'สมชาย'",
            terms.contains(ThaiSoundex.udom83("สมชาย")));
        assertFalse("No Soundex for fragment 'สม'",
            terms.contains(ThaiSoundex.udom83("สม")));
        assertFalse("No Soundex for fragment 'ชาย'",
            terms.contains(ThaiSoundex.udom83("ชาย")));
    }

    public void testOovNameFragmentsKeepOriginalsWithoutShortCodes() throws IOException {
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();
        ThaiPersonAnalyzer analyzer = new ThaiPersonAnalyzer(trie);

        // สุรไชย is not in the dictionary: segments into สุ|ร|ไชย.
        // Short-fragment codes (สุ, ร) are noise-dominated and suppressed;
        // the 3-char ไชย keeps its code (documented precision/recall trade-off).
        TokenStream ts = analyzer.tokenStream("name", "สุรไชย");
        List<String> terms = collectTerms(ts);

        assertTrue(terms.contains("สุ"));
        assertTrue(terms.contains("ร"));
        assertTrue(terms.contains("ไชย"));
        assertFalse(terms.contains(ThaiSoundex.udom83("สุ")));
        assertTrue(terms.contains(ThaiSoundex.udom83("ไชย")));
    }

    public void testHomophoneNameMatching() throws IOException {
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();
        ThaiPersonAnalyzer analyzer = new ThaiPersonAnalyzer(trie);

        // Test ณัฐพล vs นัฐพล (homophones in Thai names)
        TokenStream ts1 = analyzer.tokenStream("name", "ณัฐพล");
        List<String> terms1 = collectTerms(ts1);

        TokenStream ts2 = analyzer.tokenStream("name", "นัฐพล");
        List<String> terms2 = collectTerms(ts2);

        // "ณัฐ" and "นัฐ" must share the identical Udom83 Soundex signature "น800000"
        String natSoundex = ThaiSoundex.udom83("ณัฐ");
        assertEquals("Udom83 Soundex must be identical for 'ณัฐ' and 'นัฐ'", natSoundex, ThaiSoundex.udom83("นัฐ"));
        assertTrue(terms1.contains(natSoundex));
        assertTrue(terms2.contains(natSoundex));

        // Both full names share the common phonetic stream
        assertEquals("Both homophone streams should have matching Soundex codes",
                filterSoundexOnly(terms1), filterSoundexOnly(terms2));
    }

    public void testToneStrippingInPersonNames() throws IOException {
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();
        ThaiPersonAnalyzer analyzer = new ThaiPersonAnalyzer(trie);

        TokenStream ts = analyzer.tokenStream("name", "พรทิพย์");
        List<String> terms = collectTerms(ts);

        // "พรทิพย์" -> "พร" and "ทิพย์"
        assertTrue(terms.contains("พร"));
        assertTrue(terms.contains("ทิพย์"));

        // Thanthakhat stripped version of "ทิพย์" -> "ทิพย"
        assertTrue("Should contain thanthakhat stripped form 'ทิพย'", terms.contains("ทิพย"));
    }

    public void testMixedDecompoundWithCustomDictionary() throws IOException {
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault().copy();
        trie.add("สมชาย", 10.0); // add as compound entry

        ThaiPersonAnalyzer analyzer = new ThaiPersonAnalyzer(trie, DecompoundMode.MIXED, true, true);
        TokenStream ts = analyzer.tokenStream("name", "สมชาย");
        List<String> terms = collectTerms(ts);

        // In MIXED mode with dictionary entry: both compound "สมชาย" and subwords "สม", "ชาย" are present
        assertTrue(terms.contains("สมชาย"));
        assertTrue(terms.contains("สม"));
        assertTrue(terms.contains("ชาย"));
    }

    private static List<String> collectTerms(TokenStream ts) throws IOException {
        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
        List<String> terms = new ArrayList<>();
        ts.reset();
        while (ts.incrementToken()) {
            terms.add(termAtt.toString());
        }
        ts.end();
        ts.close();
        return terms;
    }

    private static List<String> filterSoundexOnly(List<String> terms) {
        List<String> res = new ArrayList<>();
        for (String t : terms) {
            if (t.matches("^[ก-ฮ][0-9]{6}$")) {
                res.add(t);
            }
        }
        return res;
    }
}
