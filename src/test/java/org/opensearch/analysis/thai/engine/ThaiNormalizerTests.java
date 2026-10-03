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

public class ThaiNormalizerTests extends LuceneTestCase {

    public void testSaraAmForms() {
        // Nikhahit + Sara Aa
        assertEquals("กำลัง", ThaiNormalizer.normalize("กําลัง"));
        // Nikhahit + tone mark + Sara Aa (legacy TIS-620 rendering)
        assertEquals("น้ำตาล", ThaiNormalizer.normalize("นํ้าตาล"));
        assertEquals("ต่ำ", ThaiNormalizer.normalize("ตํ่า"));
        // tone mark + Nikhahit + Sara Aa (compatibility decomposition of น้ำ)
        assertEquals("น้ำ", ThaiNormalizer.normalize("น้ํา"));
        // tone mark typed after Sara Am
        assertEquals("น้ำ", ThaiNormalizer.normalize("นำ้"));
    }

    public void testOtherVariants() {
        assertEquals("แม่", ThaiNormalizer.normalize("เเม่"));
        assertEquals("น้ำ", ThaiNormalizer.normalize("น้้ำ"));
        // tone mark before an above vowel -> vowel first
        assertEquals("ที่", ThaiNormalizer.normalize("ท่ี"));
        assertEquals("ไทย", ThaiNormalizer.normalize("ไท​ย"));
        // Lakkhangyao, but not after ฤ / ฦ
        assertEquals("ฤๅษี", ThaiNormalizer.normalize("ฤๅษี"));
        assertEquals("กา", ThaiNormalizer.normalize("กๅ"));
    }

    public void testUnchangedTextIsSameInstance() {
        String s = "น้ำตาล";
        assertSame(s, ThaiNormalizer.normalize(s));
        String latin = "OpenSearch 3.9";
        assertSame(latin, ThaiNormalizer.normalize(latin));
        assertNull(ThaiNormalizer.normalize(null));
        assertEquals("", ThaiNormalizer.normalize(""));
    }

    public void testNikhahitWithoutSaraAaIsKept() {
        assertEquals("ณํฐ", ThaiNormalizer.normalize("ณํฐ"));
    }
}
