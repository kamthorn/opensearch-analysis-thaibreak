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
 * Unit tests for {@link ThaiKeyboardConverter}.
 */
public class ThaiKeyboardConverterTests extends LuceneTestCase {

    public void testQwertyToKedmaneeBasic() {
        assertEquals("สวัสดี", ThaiKeyboardConverter.toKedmanee("l;ylfu"));
        assertEquals("เป็นต้น", ThaiKeyboardConverter.toKedmanee("gxHo9ho"));
        assertEquals("ไทย", ThaiKeyboardConverter.toKedmanee("wmp"));
    }

    public void testKedmaneeToQwertyBasic() {
        assertEquals("l;ylfu", ThaiKeyboardConverter.toQwerty("สวัสดี"));
        assertEquals("gxHo9ho", ThaiKeyboardConverter.toQwerty("เป็นต้น"));
        assertEquals("wmp", ThaiKeyboardConverter.toQwerty("ไทย"));
    }

    public void testNumbersAndShiftedSymbols() {
        // 1 -> ๅ, ! -> +
        assertEquals("ๅ", ThaiKeyboardConverter.toKedmanee("1"));
        assertEquals("+", ThaiKeyboardConverter.toKedmanee("!"));
        // 0 -> จ, ) -> ๗
        assertEquals("จ", ThaiKeyboardConverter.toKedmanee("0"));
        assertEquals("๗", ThaiKeyboardConverter.toKedmanee(")"));
        // Thai numbers: ๑ -> @, ๒ -> #
        assertEquals("@", ThaiKeyboardConverter.toQwerty("๑"));
        assertEquals("#", ThaiKeyboardConverter.toQwerty("๒"));
    }

    public void testContainsThai() {
        assertTrue(ThaiKeyboardConverter.containsThai("สวัสดี"));
        assertTrue(ThaiKeyboardConverter.containsThai("Hello สวัสดี"));
        assertFalse(ThaiKeyboardConverter.containsThai("Hello World"));
        assertFalse(ThaiKeyboardConverter.containsThai("l;ylfu"));
        assertFalse(ThaiKeyboardConverter.containsThai(null));
    }

    public void testDirectionParsing() {
        assertEquals(
            ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE,
            ThaiKeyboardConverter.Direction.fromString("qwerty_to_kedmanee")
        );
        assertEquals(
            ThaiKeyboardConverter.Direction.KEDMANEE_TO_QWERTY,
            ThaiKeyboardConverter.Direction.fromString("kedmanee_to_qwerty")
        );
        assertEquals(
            ThaiKeyboardConverter.Direction.BOTH,
            ThaiKeyboardConverter.Direction.fromString("both")
        );
        assertEquals(
            ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE,
            ThaiKeyboardConverter.Direction.fromString(null)
        );
    }
}
