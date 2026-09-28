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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.opensearch.analysis.thai.engine.ThaiTrie;

/**
 * Specialized analyzer optimized for Thai person names, entity titles, and identity search.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link ThaiBreakTokenizer} — with {@link DecompoundMode#MIXED} as default to preserve
 *       full compound names while emitting sub-word tokens as a token graph.</li>
 *   <li>{@link LowerCaseFilter} — normalizes mixed Latin/English names.</li>
 *   <li>{@link ThaiToneFilter} — strips tone marks and diacritics as synonyms for loose matching.</li>
 *   <li>{@link ThaiSoundexTokenFilter} — generates Udom83 phonetic signatures as synonyms for homophone matching.</li>
 * </ol>
 */
public final class ThaiPersonAnalyzer extends Analyzer {

    private final ThaiTrie trie;
    private final DecompoundMode mode;
    private final boolean tone;
    private final boolean soundex;

    /**
     * Constructs a {@link ThaiPersonAnalyzer} with default settings (mixed decompounding, tone and soundex enabled).
     *
     * @param trie Thai dictionary trie
     */
    public ThaiPersonAnalyzer(ThaiTrie trie) {
        this(trie, DecompoundMode.MIXED, true, true);
    }

    /**
     * Constructs a {@link ThaiPersonAnalyzer} with custom settings.
     *
     * @param trie    Thai dictionary trie
     * @param mode    compound decompounding mode (default: {@link DecompoundMode#MIXED})
     * @param tone    whether to include tone and diacritic stripping
     * @param soundex whether to include Udom83 phonetic soundex matching
     */
    public ThaiPersonAnalyzer(ThaiTrie trie, DecompoundMode mode, boolean tone, boolean soundex) {
        this.trie = trie;
        this.mode = mode != null ? mode : DecompoundMode.MIXED;
        this.tone = tone;
        this.soundex = soundex;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new ThaiBreakTokenizer(trie, mode);
        TokenStream stream = new LowerCaseFilter(tokenizer);
        if (tone) {
            stream = new ThaiToneFilter(stream, true, true, true);
        }
        if (soundex) {
            stream = new ThaiSoundexTokenFilter(stream, true);
        }
        return new TokenStreamComponents(tokenizer, stream);
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
    }
}
