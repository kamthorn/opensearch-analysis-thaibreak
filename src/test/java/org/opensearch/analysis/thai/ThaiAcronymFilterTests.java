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
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.opensearch.analysis.thai.engine.ThaiAcronymMap;
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class ThaiAcronymFilterTests extends BaseTokenStreamTestCase {

    private TokenStream createStream(String text, ThaiAcronymMap map, boolean keepOriginal, boolean bidirectional, boolean normalizeDots) {
        ThaiBreakTokenizer tokenizer = new ThaiBreakTokenizer(ThaiDictionaryLoader.loadDefault());
        tokenizer.setReader(new StringReader(text));
        return new ThaiAcronymFilter(tokenizer, map, keepOriginal, bidirectional, normalizeDots);
    }

    public void testAcronymExpansionWithDots() throws IOException {
        TokenStream ts = createStream("กทม.", null, true, false, true);
        List<String> terms = new ArrayList<>();
        List<Integer> posIncs = new ArrayList<>();

        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncAtt = ts.addAttribute(PositionIncrementAttribute.class);
        TypeAttribute typeAtt = ts.addAttribute(TypeAttribute.class);

        ts.reset();
        while (ts.incrementToken()) {
            terms.add(termAtt.toString());
            posIncs.add(posIncAtt.getPositionIncrement());
        }
        ts.end();
        ts.close();

        assertEquals(List.of("กทม.", "กรุงเทพมหานคร"), terms);
        assertEquals(List.of(1, 0), posIncs);
    }

    public void testAcronymExpansionWithoutDots() throws IOException {
        TokenStream ts = createStream("กทม", null, true, false, true);
        List<String> terms = new ArrayList<>();
        List<Integer> posIncs = new ArrayList<>();

        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncAtt = ts.addAttribute(PositionIncrementAttribute.class);

        ts.reset();
        while (ts.incrementToken()) {
            terms.add(termAtt.toString());
            posIncs.add(posIncAtt.getPositionIncrement());
        }
        ts.end();
        ts.close();

        assertEquals(List.of("กทม", "กรุงเทพมหานคร"), terms);
        assertEquals(List.of(1, 0), posIncs);
    }

    public void testMultipleAcronymsInSentence() throws IOException {
        TokenStream ts = createStream("รพ. ใน กทม.", null, true, false, true);
        List<String> terms = new ArrayList<>();
        List<Integer> posIncs = new ArrayList<>();

        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncAtt = ts.addAttribute(PositionIncrementAttribute.class);

        ts.reset();
        while (ts.incrementToken()) {
            terms.add(termAtt.toString());
            posIncs.add(posIncAtt.getPositionIncrement());
        }
        ts.end();
        ts.close();

        assertTrue(terms.contains("โรงพยาบาล"));
        assertTrue(terms.contains("กรุงเทพมหานคร"));
    }

    public void testReplaceMode() throws IOException {
        TokenStream ts = createStream("กทม.", null, false, false, true);
        List<String> terms = new ArrayList<>();

        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);

        ts.reset();
        while (ts.incrementToken()) {
            terms.add(termAtt.toString());
        }
        ts.end();
        ts.close();

        assertEquals(List.of("กรุงเทพมหานคร"), terms);
    }

    public void testBidirectionalMode() throws IOException {
        TokenStream ts = createStream("กรุงเทพมหานคร", null, true, true, true);
        List<String> terms = new ArrayList<>();
        List<Integer> posIncs = new ArrayList<>();

        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncAtt = ts.addAttribute(PositionIncrementAttribute.class);

        ts.reset();
        while (ts.incrementToken()) {
            terms.add(termAtt.toString());
            posIncs.add(posIncAtt.getPositionIncrement());
        }
        ts.end();
        ts.close();

        assertEquals(List.of("กรุงเทพมหานคร", "กทม."), terms);
        assertEquals(List.of(1, 0), posIncs);
    }

    public void testCustomMergedRules() throws IOException {
        ThaiAcronymMap customMap = ThaiAcronymMap.mergeWithRules(
                ThaiAcronymMap.loadDefault(),
                List.of("อบต.=>องค์การบริหารส่วนตำบล", "มทส.=>มหาวิทยาลัยเทคโนโลยีสุรนารี")
        );

        TokenStream ts = createStream("มทส.", customMap, false, false, true);
        List<String> terms = new ArrayList<>();
        CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);

        ts.reset();
        while (ts.incrementToken()) {
            terms.add(termAtt.toString());
        }
        ts.end();
        ts.close();

        assertEquals(List.of("มหาวิทยาลัยเทคโนโลยีสุรนารี"), terms);
    }
}
