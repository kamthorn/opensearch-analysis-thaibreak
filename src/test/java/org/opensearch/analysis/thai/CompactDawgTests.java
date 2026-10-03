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

import org.apache.lucene.tests.util.LuceneTestCase;
import org.opensearch.analysis.thai.engine.CompactDawg;

import java.io.InputStream;

public class CompactDawgTests extends LuceneTestCase {

    public void testCompactDawgLoadingAndLookup() throws Exception {
        CompactDawg dawg;
        long t0 = System.nanoTime();
        try (InputStream is = getClass().getResourceAsStream("/org/opensearch/analysis/thai/words.dawg")) {
            assertNotNull("words.dawg must exist in resources", is);
            dawg = CompactDawg.loadFromStream(is);
        }
        long t1 = System.nanoTime();
        double loadMs = (t1 - t0) / 1_000_000.0;
        assertTrue("Load time should be under 50ms", loadMs < 50.0);

        // Verify loaded stats
        assertEquals(51347, dawg.numWords());
        assertTrue("States should be around 45,000", dawg.numStates() > 30000);
        assertTrue("DAWG size should be around 392KB", dawg.sizeInBytes() < 450000);

        // Verification of known words
        assertTrue(dawg.contains("กุมภาพันธ์"));
        assertTrue(dawg.contains("สนามบิน"));
        assertTrue(dawg.contains("กทม."));
        assertTrue(dawg.contains("กรมควบคุมโรค"));
        assertTrue(dawg.contains("รถไฟฟ้า"));
        assertTrue(dawg.contains("กฎหมาย"));

        // Verification of non-words
        assertFalse(dawg.contains("คำนี้ไม่มีในพจนานุกรมแน่นอน12345"));
        assertFalse(dawg.contains("xyz123"));
        assertFalse(dawg.contains(""));
        assertFalse(dawg.contains(null));

        // Fast transition prefix test for "สนามบิน"
        int s = 0;
        for (char c : "สนามบิน".toCharArray()) {
            s = dawg.transition(s, c);
            assertTrue("Valid transition", s != -1);
        }
        assertTrue("State must be final", dawg.isFinal(s));
    }
}
