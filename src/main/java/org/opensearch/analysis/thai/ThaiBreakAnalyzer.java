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
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.opensearch.analysis.thai.engine.ThaiTrie;

/**
 * Analyzer for Thai text using the Viterbi+TCC segmentation engine.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link ThaiBreakTokenizer} — Viterbi word segmentation + optional decompounding</li>
 *   <li>{@link ThaiNormalizationFilter} — canonical Thai spelling (on by default)</li>
 *   <li>{@link LowerCaseFilter} — lowercase Latin letters</li>
 *   <li>{@link StopFilter} — optional stop words</li>
 * </ol>
 */
public final class ThaiBreakAnalyzer extends Analyzer {

    private final ThaiTrie trie;
    private final CharArraySet stopWords;
    private final DecompoundMode mode;
    private final boolean normalization;

    /** Creates an analyzer with the provided dictionary and no stop words. */
    public ThaiBreakAnalyzer(ThaiTrie trie) {
        this(trie, CharArraySet.EMPTY_SET, DecompoundMode.NONE);
    }

    /** Creates an analyzer with the provided dictionary and decompound mode. */
    public ThaiBreakAnalyzer(ThaiTrie trie, DecompoundMode mode) {
        this(trie, CharArraySet.EMPTY_SET, mode);
    }

    /** Creates an analyzer with a custom stop-word set and decompound mode. */
    public ThaiBreakAnalyzer(ThaiTrie trie, CharArraySet stopWords, DecompoundMode mode) {
        this(trie, stopWords, mode, true);
    }

    /**
     * Creates an analyzer with a custom stop-word set and decompound mode.
     *
     * @param normalization whether to apply {@link ThaiNormalizationFilter}
     */
    public ThaiBreakAnalyzer(ThaiTrie trie, CharArraySet stopWords, DecompoundMode mode,
                             boolean normalization) {
        this.trie = trie;
        this.normalization = normalization;
        this.stopWords = stopWords == null ? CharArraySet.EMPTY_SET : stopWords;
        this.mode = mode == null ? DecompoundMode.NONE : mode;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new ThaiBreakTokenizer(trie, mode);
        TokenStream stream = tokenizer;
        if (normalization) {
            stream = new ThaiNormalizationFilter(stream);
        }
        stream = new LowerCaseFilter(stream);
        if (!stopWords.isEmpty()) {
            stream = new StopFilter(stream, stopWords);
        }
        return new TokenStreamComponents(tokenizer, stream);
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        TokenStream stream = normalization ? new ThaiNormalizationFilter(in) : in;
        return new LowerCaseFilter(stream);
    }
}
