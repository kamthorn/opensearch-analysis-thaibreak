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

/**
 * Unit tests for {@link ThaiSoundex} (Udom83 algorithm).
 */
public class ThaiSoundexTests extends LuceneTestCase {

    public void testHomophonesLak() {
        // ลัก, รัก, รักษ์ all have soundex 'ร100000'
        assertEquals("ร100000", ThaiSoundex.udom83("ลัก"));
        assertEquals("ร100000", ThaiSoundex.udom83("รัก"));
        assertEquals("ร100000", ThaiSoundex.udom83("รักษ์"));
    }

    public void testHomophonesKan() {
        // กาล, การ, การณ์ all have soundex 'ก900000'
        assertEquals("ก900000", ThaiSoundex.udom83("กาล"));
        assertEquals("ก900000", ThaiSoundex.udom83("การ"));
        assertEquals("ก900000", ThaiSoundex.udom83("การณ์"));
    }

    public void testHomophonesPhan() {
        // พันธ์, พันธุ์ have soundex 'พ300000'
        assertEquals("พ300000", ThaiSoundex.udom83("พันธ์"));
        assertEquals("พ300000", ThaiSoundex.udom83("พันธุ์"));
    }

    public void testStandardExamples() {
        assertEquals("บ931900", ThaiSoundex.udom83("บูรณการ"));
        assertEquals("ป775300", ThaiSoundex.udom83("ปัจจุบัน"));
    }

    public void testEdgeCases() {
        assertEquals("", ThaiSoundex.udom83(null));
        assertEquals("", ThaiSoundex.udom83(""));
    }
}
