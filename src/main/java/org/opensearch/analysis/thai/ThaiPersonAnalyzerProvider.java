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

import org.opensearch.analysis.thai.engine.ThaiTrie;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AnalyzerProvider;
import org.opensearch.index.analysis.AnalyzerScope;

/**
 * OpenSearch {@link AnalyzerProvider} for the {@code thaibreak_person} / {@code thai_person} analyzer.
 *
 * <p>Supported settings:
 * <ul>
 *   <li>{@code decompound_mode} — compound decompounding mode: {@code none}, {@code discard}, or {@code mixed} (default: {@code mixed}).</li>
 *   <li>{@code tone} — whether to include tone and diacritic stripping (default: {@code true}).</li>
 *   <li>{@code soundex} — whether to include Udom83 phonetic soundex matching (default: {@code true}).</li>
 *   <li>{@code user_dictionary} — path to custom dictionary file.</li>
 *   <li>{@code user_dictionary_rules} — inline custom dictionary rules.</li>
 * </ul>
 */
public final class ThaiPersonAnalyzerProvider implements AnalyzerProvider<ThaiPersonAnalyzer> {

    private final String name;
    private final ThaiPersonAnalyzer analyzer;

    /**
     * Constructs a {@link ThaiPersonAnalyzerProvider} from index settings.
     *
     * @param indexSettings index settings
     * @param env           environment
     * @param name          analyzer name
     * @param settings      component settings
     */
    public ThaiPersonAnalyzerProvider(IndexSettings indexSettings,
                                      Environment env,
                                      String name,
                                      Settings settings) {
        this.name = name;
        ThaiTrie trie = ThaiBreakTokenizerFactory.loadTrie(env, settings);
        DecompoundMode mode = DecompoundMode.fromString(settings.get("decompound_mode", "mixed"));
        boolean tone = settings.getAsBoolean("tone", true);
        boolean soundex = settings.getAsBoolean("soundex", true);
        this.analyzer = new ThaiPersonAnalyzer(trie, mode, tone, soundex);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AnalyzerScope scope() {
        return AnalyzerScope.INDEX;
    }

    @Override
    public ThaiPersonAnalyzer get() {
        return analyzer;
    }
}
