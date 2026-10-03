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

import org.opensearch.index.analysis.AnalyzerProvider;
import org.opensearch.index.analysis.CharFilterFactory;
import org.opensearch.index.analysis.TokenFilterFactory;
import org.opensearch.index.analysis.TokenizerFactory;
import org.opensearch.indices.analysis.AnalysisModule;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.Plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenSearch plugin entry point for {@code analysis-thaibreak}.
 *
 * <p>Registers:
 * <ul>
 *   <li>Tokenizer type {@code thaibreak} → {@link ThaiBreakTokenizerFactory}</li>
 *   <li>Analyzer type {@code thaibreak} → {@link ThaiBreakAnalyzerProvider}</li>
 *   <li>CharFilter type {@code thaibreak_keyboard}, {@code thai_keyboard} → {@link ThaiKeyboardCharFilterFactory}</li>
 *   <li>TokenFilter type {@code thaibreak_keyboard}, {@code thai_keyboard} → {@link ThaiKeyboardTokenFilterFactory}</li>
 * </ul>
 *
 * <p>Example usage in index settings:
 * <pre>{@code
 * PUT /my-index
 * {
 *   "settings": {
 *     "analysis": {
 *       "analyzer": {
 *         "thai": {
 *           "type": "thaibreak"
 *         }
 *       },
 *       "tokenizer": {
 *         "thai_tokenizer": {
 *           "type": "thaibreak",
 *           "user_dictionary": "analysis/my-custom-words.txt"
 *         }
 *       }
 *     }
 *   }
 * }
 * }</pre>
 */
public final class ThaiBreakPlugin extends Plugin implements AnalysisPlugin {

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> getTokenizers() {
        return Map.of(
            "thaibreak",
            (AnalysisModule.AnalysisProvider<TokenizerFactory>)
                (indexSettings, env, name, settings) ->
                    new ThaiBreakTokenizerFactory(indexSettings, env, name, settings)
        );
    }

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<?>>> getAnalyzers() {
        return Map.of(
            "thaibreak",
            (AnalysisModule.AnalysisProvider<AnalyzerProvider<?>>)
                (indexSettings, env, name, settings) ->
                    new ThaiBreakAnalyzerProvider(indexSettings, env, name, settings),
            "thaibreak_person",
            (AnalysisModule.AnalysisProvider<AnalyzerProvider<?>>)
                (indexSettings, env, name, settings) ->
                    new ThaiPersonAnalyzerProvider(indexSettings, env, name, settings),
            "thai_person",
            (AnalysisModule.AnalysisProvider<AnalyzerProvider<?>>)
                (indexSettings, env, name, settings) ->
                    new ThaiPersonAnalyzerProvider(indexSettings, env, name, settings)
        );
    }

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<CharFilterFactory>> getCharFilters() {
        return Map.of(
            "thaibreak_keyboard", ThaiKeyboardCharFilterFactory::new,
            "thai_keyboard", ThaiKeyboardCharFilterFactory::new
        );
    }

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> filters = new HashMap<>();
        filters.put("thaibreak_keyboard", ThaiKeyboardTokenFilterFactory::new);
        filters.put("thai_keyboard", ThaiKeyboardTokenFilterFactory::new);
        filters.put("thaibreak_soundex", ThaiSoundexTokenFilterFactory::new);
        filters.put("thai_soundex", ThaiSoundexTokenFilterFactory::new);
        filters.put("thaibreak_tone", ThaiToneFilterFactory::new);
        filters.put("thai_tone", ThaiToneFilterFactory::new);
        filters.put("thaibreak_number", ThaiNumberFilterFactory::new);
        filters.put("thai_number", ThaiNumberFilterFactory::new);
        filters.put("thaibreak_acronym", ThaiAcronymFilterFactory::new);
        filters.put("thai_acronym", ThaiAcronymFilterFactory::new);
        filters.put("thaibreak_collation", ThaiCollationFilterFactory::new);
        filters.put("thai_collation", ThaiCollationFilterFactory::new);
        filters.put("thaibreak_romanization", ThaiRomanizationFilterFactory::new);
        filters.put("thai_romanization", ThaiRomanizationFilterFactory::new);
        // No thai_* alias: OpenSearch core may add its own thai_normalization (Lucene 10.6).
        filters.put("thaibreak_normalization", ThaiNormalizationFilterFactory::new);
        return Collections.unmodifiableMap(filters);
    }
}
