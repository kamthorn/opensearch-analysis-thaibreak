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

import static org.junit.Assert.*;

/**
 * Tests for {@link ThaiDictionaryLoader}'s cached default dictionary and the
 * copy-on-write contract that keeps per-index user dictionaries isolated.
 */
public class ThaiDictionaryLoaderTests extends LuceneTestCase {

    public void testLoadDefaultReturnsSharedCachedInstance() {
        ThaiTrie first = ThaiDictionaryLoader.loadDefault();
        ThaiTrie second = ThaiDictionaryLoader.loadDefault();
        assertSame("loadDefault() should return the same cached instance", first, second);
    }

    public void testCopyMutationDoesNotLeakIntoSharedDefault() {
        ThaiTrie shared = ThaiDictionaryLoader.loadDefault();
        double sharedWeightBefore = shared.totalWeight();

        String customWord = "มะเขือเทศสีม่วงประหลาด9999";
        ThaiTrie perIndexCopy = shared.copy();
        perIndexCopy.add(customWord, 5.0);

        assertTrue("Custom word should be findable in the per-index copy",
            containsWord(perIndexCopy, customWord));
        assertFalse("Custom word must not leak into the shared default trie",
            containsWord(shared, customWord));
        assertFalse("Custom word must not leak into a fresh loadDefault() call",
            containsWord(ThaiDictionaryLoader.loadDefault(), customWord));
        assertEquals("Shared default's total weight must be unchanged by the copy's mutation",
            sharedWeightBefore, shared.totalWeight(), 0.0);
    }

    private static boolean containsWord(ThaiTrie trie, String word) {
        int[] runes = word.codePoints().toArray();
        for (ThaiTrie.PrefixMatch m : trie.prefixes(runes, 0, 0)) {
            if (m.end == runes.length) {
                return true;
            }
        }
        return false;
    }
}
