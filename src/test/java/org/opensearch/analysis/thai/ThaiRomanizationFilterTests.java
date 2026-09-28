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
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.opensearch.analysis.thai.engine.ThaiRomanizer;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class ThaiRomanizationFilterTests extends LuceneTestCase {

    public void testBundledDictionaryLookup() throws IOException {
        ThaiRomanizer romanizer = ThaiRomanizer.loadDefault();
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("สวัสดี เชียงใหม่ ภูเก็ต"));
        ThaiRomanizationFilter filter = new ThaiRomanizationFilter(tokenizer, romanizer, true);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncAtt = filter.addAttribute(PositionIncrementAttribute.class);
        TypeAttribute typeAtt = filter.addAttribute(TypeAttribute.class);
        filter.reset();

        List<String> terms = new ArrayList<>();
        List<Integer> posIncs = new ArrayList<>();
        List<String> types = new ArrayList<>();

        while (filter.incrementToken()) {
            terms.add(termAtt.toString());
            posIncs.add(posIncAtt.getPositionIncrement());
            types.add(typeAtt.type());
        }
        filter.end();
        filter.close();

        // "สวัสดี" -> original (posInc=1) + "sawatdi" (posInc=0)
        assertEquals("สวัสดี", terms.get(0));
        assertEquals(1, (int) posIncs.get(0));
        assertEquals("sawatdi", terms.get(1));
        assertEquals(0, (int) posIncs.get(1));
        assertEquals(ThaiRomanizationFilter.TOKEN_TYPE_ROMANIZATION, types.get(1));

        // "เชียงใหม่" -> original + "chiangmai"
        assertEquals("เชียงใหม่", terms.get(2));
        assertEquals(1, (int) posIncs.get(2));
        assertEquals("chiangmai", terms.get(3));
        assertEquals(0, (int) posIncs.get(3));

        // "ภูเก็ต" -> original + "phuket"
        assertEquals("ภูเก็ต", terms.get(4));
        assertEquals(1, (int) posIncs.get(4));
        assertEquals("phuket", terms.get(5));
        assertEquals(0, (int) posIncs.get(5));
    }

    public void testMultipleSynonymRomanizations() throws IOException {
        ThaiRomanizer romanizer = ThaiRomanizer.loadDefault();
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("กรุงเทพ"));
        ThaiRomanizationFilter filter = new ThaiRomanizationFilter(tokenizer, romanizer, true);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncAtt = filter.addAttribute(PositionIncrementAttribute.class);
        filter.reset();

        List<String> terms = new ArrayList<>();
        List<Integer> posIncs = new ArrayList<>();
        while (filter.incrementToken()) {
            terms.add(termAtt.toString());
            posIncs.add(posIncAtt.getPositionIncrement());
        }
        filter.end();
        filter.close();

        // กรุงเทพ should emit original and both "krungthep" and "bangkok" as synonyms
        assertTrue(terms.contains("กรุงเทพ"));
        assertTrue(terms.contains("krungthep"));
        assertTrue(terms.contains("bangkok"));
        assertEquals(3, terms.size());
    }

    public void testAlgorithmicFallback() throws IOException {
        ThaiRomanizer romanizer = ThaiRomanizer.loadDefault();
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("กบ ไก่"));
        ThaiRomanizationFilter filter = new ThaiRomanizationFilter(tokenizer, romanizer, true);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncAtt = filter.addAttribute(PositionIncrementAttribute.class);
        filter.reset();

        List<String> terms = new ArrayList<>();
        List<Integer> posIncs = new ArrayList<>();
        while (filter.incrementToken()) {
            terms.add(termAtt.toString());
            posIncs.add(posIncAtt.getPositionIncrement());
        }
        filter.end();
        filter.close();

        // "กบ" -> original (1) + "kop" (0)
        assertEquals("กบ", terms.get(0));
        assertEquals("kop", terms.get(1));
        assertEquals(0, (int) posIncs.get(1));

        // "ไก่" -> original (1) + "kai" (0)
        assertEquals("ไก่", terms.get(2));
        assertEquals("kai", terms.get(3));
        assertEquals(0, (int) posIncs.get(3));
    }

    public void testKeepOriginalFalse() throws IOException {
        ThaiRomanizer romanizer = ThaiRomanizer.loadDefault();
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("สวัสดี"));
        ThaiRomanizationFilter filter = new ThaiRomanizationFilter(tokenizer, romanizer, false);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncAtt = filter.addAttribute(PositionIncrementAttribute.class);
        filter.reset();

        List<String> terms = new ArrayList<>();
        while (filter.incrementToken()) {
            terms.add(termAtt.toString());
        }
        filter.end();
        filter.close();

        // Replaced in-place
        assertEquals(1, terms.size());
        assertEquals("sawatdi", terms.get(0));
    }

    public void testNonThaiTokens() throws IOException {
        ThaiRomanizer romanizer = ThaiRomanizer.loadDefault();
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("OpenSearch 2026"));
        ThaiRomanizationFilter filter = new ThaiRomanizationFilter(tokenizer, romanizer, true);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        filter.reset();

        List<String> terms = new ArrayList<>();
        while (filter.incrementToken()) {
            terms.add(termAtt.toString());
        }
        filter.end();
        filter.close();

        assertEquals(2, terms.size());
        assertEquals("OpenSearch", terms.get(0));
        assertEquals("2026", terms.get(1));
    }

    public void testCustomRules() throws IOException {
        ThaiRomanizer custom = ThaiRomanizer.fromRules(
                List.of("ดีพเลิร์นนิง=>deeplearning", "โคราช=>koratcity"),
                ThaiRomanizer.loadDefault()
        );
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("ดีพเลิร์นนิง"));
        ThaiRomanizationFilter filter = new ThaiRomanizationFilter(tokenizer, custom, false);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        filter.reset();

        assertTrue(filter.incrementToken());
        assertEquals("deeplearning", termAtt.toString());
        assertFalse(filter.incrementToken());
        filter.end();
        filter.close();
    }

    public void testWordsWithTonesAndThanthakhat() throws IOException {
        ThaiRomanizer romanizer = ThaiRomanizer.loadDefault();
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader("รักษ์ ข้าว กาแฟ"));
        ThaiRomanizationFilter filter = new ThaiRomanizationFilter(tokenizer, romanizer, false);

        CharTermAttribute termAtt = filter.addAttribute(CharTermAttribute.class);
        filter.reset();

        List<String> terms = new ArrayList<>();
        while (filter.incrementToken()) {
            terms.add(termAtt.toString());
        }
        filter.end();
        filter.close();

        // "รักษ์" -> thanthakhat removed -> "rak"
        // "ข้าว" -> tone removed -> "khao"
        // "กาแฟ" -> dictionary -> "kafae"
        assertEquals(List.of("rak", "khao", "kafae"), terms);
    }
}
