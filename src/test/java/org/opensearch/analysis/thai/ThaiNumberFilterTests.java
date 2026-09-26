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
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.test.IndexSettingsModule;

import java.io.IOException;
import java.io.StringReader;

/**
 * Unit tests for {@link ThaiNumberFilter} and {@link ThaiNumberFilterFactory}.
 */
public class ThaiNumberFilterTests extends BaseTokenStreamTestCase {

    public void testThaiDigitsKeepOriginalTrue() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("๑๒๕๐"));
        TokenStream stream = new ThaiNumberFilter(source, true, true, true, 0L);
        assertTokenStreamContents(
            stream,
            new String[]{"๑๒๕๐", "1250"},
            new int[]{0, 0},
            new int[]{4, 4},
            new int[]{1, 0}
        );
    }

    public void testThaiDigitsKeepOriginalFalse() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("๑๒๕๐"));
        TokenStream stream = new ThaiNumberFilter(source, false, true, true, 0L);
        assertTokenStreamContents(
            stream,
            new String[]{"1250"},
            new int[]{0},
            new int[]{4},
            new int[]{1}
        );
    }

    public void testMixedThaiDigits() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("ชั้น๓"));
        TokenStream stream = new ThaiNumberFilter(source, true, true, true, 0L);
        assertTokenStreamContents(
            stream,
            new String[]{"ชั้น๓", "ชั้น3"},
            new int[]{0, 0},
            new int[]{5, 5},
            new int[]{1, 0}
        );
    }

    public void testSingleWordNumber() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("ห้าหมื่น"));
        TokenStream stream = new ThaiNumberFilter(source, true, true, true, 0L);
        assertTokenStreamContents(
            stream,
            new String[]{"ห้าหมื่น", "50000"},
            new int[]{0, 0},
            new int[]{8, 8},
            new int[]{1, 0}
        );
    }

    public void testMultiTokenNumberSequenceKeepOriginalTrue() throws IOException {
        // Adjacent tokens: "ห้า" (0..3) followed by "หมื่น" (3..8) without space
        // Simulate by custom offset or a test stream
        CustomTokenStream source = new CustomTokenStream(
            new String[]{"ราคา", "ห้า", "หมื่น", "บาท"},
            new int[]{0, 4, 7, 12},
            new int[]{4, 7, 12, 15}
        );
        TokenStream stream = new ThaiNumberFilter(source, true, true, true, 0L);
        // Expects:
        // "ราคา" (posInc=1, posLen=1)
        // "ห้า" (posInc=1, posLen=1)
        // "50000" (posInc=0, posLen=2, offset 4..12)
        // "หมื่น" (posInc=1, posLen=1)
        // "บาท" (posInc=1, posLen=1)
        assertTokenStreamContents(
            stream,
            new String[]{"ราคา", "ห้า", "50000", "หมื่น", "บาท"},
            new int[]{0, 4, 4, 7, 12},
            new int[]{4, 7, 12, 12, 15},
            new int[]{1, 1, 0, 1, 1}
        );
    }

    public void testMultiTokenNumberSequenceKeepOriginalFalse() throws IOException {
        CustomTokenStream source = new CustomTokenStream(
            new String[]{"ราคา", "ห้า", "หมื่น", "บาท"},
            new int[]{0, 4, 7, 12},
            new int[]{4, 7, 12, 15}
        );
        TokenStream stream = new ThaiNumberFilter(source, false, true, true, 0L);
        assertTokenStreamContents(
            stream,
            new String[]{"ราคา", "50000", "บาท"},
            new int[]{0, 4, 12},
            new int[]{4, 12, 15},
            new int[]{1, 1, 1}
        );
    }

    public void testComplexSpelledOutNumbers() {
        assertEquals(Long.valueOf(0L), ThaiNumberFilter.parseThaiWordNumber("ศูนย์"));
        assertEquals(Long.valueOf(1L), ThaiNumberFilter.parseThaiWordNumber("หนึ่ง"));
        assertEquals(Long.valueOf(12L), ThaiNumberFilter.parseThaiWordNumber("สิบสอง"));
        assertEquals(Long.valueOf(21L), ThaiNumberFilter.parseThaiWordNumber("ยี่สิบเอ็ด"));
        assertEquals(Long.valueOf(100L), ThaiNumberFilter.parseThaiWordNumber("ร้อย"));
        assertEquals(Long.valueOf(350L), ThaiNumberFilter.parseThaiWordNumber("สามร้อยห้าสิบ"));
        assertEquals(Long.valueOf(15000L), ThaiNumberFilter.parseThaiWordNumber("หมื่นห้าพัน"));
        assertEquals(Long.valueOf(50000L), ThaiNumberFilter.parseThaiWordNumber("ห้าหมื่น"));
        assertEquals(Long.valueOf(120000L), ThaiNumberFilter.parseThaiWordNumber("หนึ่งแสนสองหมื่น"));
        assertEquals(Long.valueOf(1000000L), ThaiNumberFilter.parseThaiWordNumber("ล้าน"));
        assertEquals(Long.valueOf(2350000L), ThaiNumberFilter.parseThaiWordNumber("สองล้านสามแสนห้าหมื่น"));
        assertEquals(Long.valueOf(20000000L), ThaiNumberFilter.parseThaiWordNumber("ยี่สิบล้าน"));
    }

    public void testNonNumberWordsIgnored() {
        assertNull(ThaiNumberFilter.parseThaiWordNumber("เก้าอี้"));
        assertNull(ThaiNumberFilter.parseThaiWordNumber("ร้อยกรอง"));
        assertNull(ThaiNumberFilter.parseThaiWordNumber("สามเหลี่ยม"));
        assertNull(ThaiNumberFilter.parseThaiWordNumber("หมื่นลี้"));
        assertNull(ThaiNumberFilter.parseThaiWordNumber("บ้าน"));
    }

    public void testMinWordValue() throws IOException {
        Tokenizer source = new WhitespaceTokenizer();
        source.setReader(new StringReader("ห้า"));
        // minWordValue = 10 -> "ห้า" (val=5) is not converted
        TokenStream stream = new ThaiNumberFilter(source, true, true, true, 10L);
        assertTokenStreamContents(
            stream,
            new String[]{"ห้า"},
            new int[]{0},
            new int[]{3},
            new int[]{1}
        );
    }

    public void testFactoryCreation() {
        IndexSettings indexSettings = IndexSettingsModule.newIndexSettings("test", Settings.EMPTY);
        Environment environment = new Environment(
            Settings.builder().put("path.home", createTempDir().toString()).build(),
            null
        );
        Settings settings = Settings.builder()
            .put("keep_original", false)
            .put("min_word_value", 100)
            .build();
        ThaiNumberFilterFactory factory = new ThaiNumberFilterFactory(
            indexSettings,
            environment,
            "thaibreak_number",
            settings
        );
        assertNotNull(factory);
    }

    /** Helper custom token stream to emit tokens with explicit offsets. */
    private static final class CustomTokenStream extends TokenStream {
        private final String[] terms;
        private final int[] startOffsets;
        private final int[] endOffsets;
        private int index = 0;

        private final org.apache.lucene.analysis.tokenattributes.CharTermAttribute termAtt =
            addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
        private final org.apache.lucene.analysis.tokenattributes.OffsetAttribute offsetAtt =
            addAttribute(org.apache.lucene.analysis.tokenattributes.OffsetAttribute.class);
        private final org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute posIncAtt =
            addAttribute(org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute.class);

        CustomTokenStream(String[] terms, int[] startOffsets, int[] endOffsets) {
            this.terms = terms;
            this.startOffsets = startOffsets;
            this.endOffsets = endOffsets;
        }

        @Override
        public boolean incrementToken() {
            if (index >= terms.length) {
                return false;
            }
            clearAttributes();
            termAtt.append(terms[index]);
            offsetAtt.setOffset(startOffsets[index], endOffsets[index]);
            posIncAtt.setPositionIncrement(1);
            index++;
            return true;
        }

        @Override
        public void reset() {
            index = 0;
        }
    }
}
