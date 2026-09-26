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

import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiTrie;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AnalyzerProvider;
import org.opensearch.index.analysis.AnalyzerScope;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OpenSearch {@link AnalyzerProvider} for the {@code thaibreak} analyzer.
 *
 * <p>Settings:
 * <ul>
 *   <li>{@code user_dictionary} — optional path (relative to config dir) for
 *       a custom word list in TSV format.</li>
 *   <li>{@code decompound_mode} — compound word decompounding mode:
 *       {@code none}, {@code discard}, or {@code mixed}. Default: {@code none}.</li>
 * </ul>
 */
public final class ThaiBreakAnalyzerProvider implements AnalyzerProvider<ThaiBreakAnalyzer> {

    private final String name;
    private final ThaiBreakAnalyzer analyzer;

    public ThaiBreakAnalyzerProvider(IndexSettings indexSettings,
                                     Environment env,
                                     String name,
                                     Settings settings) {
        this.name = name;

        ThaiTrie trie;
        try {
            trie = ThaiDictionaryLoader.loadDefault();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load bundled Thai dictionary", e);
        }

        String userDictPath = settings.get("user_dictionary");
        if (userDictPath != null && !userDictPath.isBlank()) {
            Path dictPath = env.configDir().resolve(userDictPath);
            try (InputStream is = Files.newInputStream(dictPath)) {
                ThaiDictionaryLoader.loadFromStream(is, trie);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load user dictionary: " + dictPath, e);
            }
        }

        DecompoundMode mode = DecompoundMode.fromString(settings.get("decompound_mode", "none"));
        this.analyzer = new ThaiBreakAnalyzer(trie, mode);
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
    public ThaiBreakAnalyzer get() {
        return analyzer;
    }
}
