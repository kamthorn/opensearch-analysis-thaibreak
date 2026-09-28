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

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.opensearch.analysis.thai.engine.ThaiTrie;
import org.opensearch.common.settings.Settings;

import java.io.IOException;
import java.io.StringReader;

/**
 * Unit tests for {@code filter_bound_morphemes}: excluding dictionary entries that
 * corpus evidence (LST20) shows are almost never standalone words, only bound
 * prefixes/suffixes (e.g. {@code สุ}, {@code ไชย}), so OOV names built from them
 * merge into one chunk instead of fragmenting around a spurious dictionary hit.
 */
public class ThaiBoundMorphemeFilterTests extends BaseTokenStreamTestCase {

    public void testDisabledByDefault() throws IOException {
        ThaiTrie trie = ThaiBreakTokenizerFactory.loadTrie(null, Settings.EMPTY);
        Tokenizer tok = new ThaiBreakTokenizer(trie);
        tok.setReader(new StringReader("สุรไชย"));
        // Baseline (undesired) behavior: สุ and ไชย are real dictionary words,
        // so the OOV ร in between is left stranded on its own.
        assertTokenStreamContents(tok, new String[]{"สุ", "ร", "ไชย"});
    }

    public void testFixesStrandedOovBetweenBoundMorphemes() throws IOException {
        Settings settings = Settings.builder().put("filter_bound_morphemes", true).build();
        ThaiTrie trie = ThaiBreakTokenizerFactory.loadTrie(null, settings);
        Tokenizer tok = new ThaiBreakTokenizer(trie);
        tok.setReader(new StringReader("สุรไชย"));
        // สุ and ไชย no longer match on their own, so the whole run becomes one
        // merged OOV token instead of fragmenting around ร.
        assertTokenStreamContents(tok, new String[]{"สุรไชย"});
    }

    public void testDoesNotBreakOrdinaryCompounds() throws IOException {
        Settings settings = Settings.builder().put("filter_bound_morphemes", true).build();
        ThaiTrie trie = ThaiBreakTokenizerFactory.loadTrie(null, settings);
        Tokenizer tok = new ThaiBreakTokenizer(trie);
        // ตู (a bound-morpheme candidate, from names like เฉิงตู) is also the tail
        // of ประตู here — the filter must not disturb this legitimate boundary.
        tok.setReader(new StringReader("ประตูประหยัดไฟ"));
        assertTokenStreamContents(tok, new String[]{"ประตู", "ประหยัด", "ไฟ"});
    }

    public void testUserDictionaryOverridesExclusion() throws IOException {
        Settings settings = Settings.builder()
            .put("filter_bound_morphemes", true)
            .putList("user_dictionary_rules", "สุ\t100.0")
            .build();
        ThaiTrie trie = ThaiBreakTokenizerFactory.loadTrie(null, settings);
        // An explicit user_dictionary entry for an excluded word still wins.
        assertTrue(trie.contains("สุ"));
    }
}
