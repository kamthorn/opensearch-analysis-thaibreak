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
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiTrie;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.TokenizerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OpenSearch {@link TokenizerFactory} for the {@code thaibreak} tokenizer.
 *
 * <p>Configuration parameters (all optional):
 * <table>
 *   <caption>Parameters</caption>
 *   <tr><th>Name</th><th>Default</th><th>Description</th></tr>
 *   <tr>
 *     <td>{@code user_dictionary}</td>
 *     <td>(none)</td>
 *     <td>Path to a custom word list relative to the OpenSearch config directory.
 *         Same TSV format as the bundled dictionary.</td>
 *   </tr>
 * </table>
 *
 * <p>Example index mapping:
 * <pre>{@code
 * "settings": {
 *   "analysis": {
 *     "tokenizer": {
 *       "my_thaibreak": {
 *         "type": "thaibreak",
 *         "user_dictionary": "analysis/my-thai-dict.txt"
 *       }
 *     }
 *   }
 * }
 * }</pre>
 */
public final class ThaiBreakTokenizerFactory implements TokenizerFactory {

    private final String name;
    private final ThaiTrie trie;

    /**
     * Constructor called by the OpenSearch analysis module.
     *
     * @param indexSettings index-level settings
     * @param env           OpenSearch environment (for config-relative paths)
     * @param name          factory name as registered
     * @param settings      tokenizer settings from the index mapping
     */
    public ThaiBreakTokenizerFactory(IndexSettings indexSettings,
                                     Environment env,
                                     String name,
                                     Settings settings) {
        this.name = name;

        ThaiTrie t;
        try {
            t = ThaiDictionaryLoader.loadDefault();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load bundled Thai dictionary", e);
        }

        // Optional user dictionary
        String userDictPath = settings.get("user_dictionary");
        if (userDictPath != null && !userDictPath.isBlank()) {
            Path dictPath = env.configDir().resolve(userDictPath);
            try (InputStream is = Files.newInputStream(dictPath)) {
                ThaiDictionaryLoader.loadFromStream(is, t);
            } catch (IOException e) {
                throw new RuntimeException(
                    "Failed to load user dictionary: " + dictPath, e);
            }
        }

        this.trie = t;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Tokenizer create() {
        return new ThaiBreakTokenizer(trie);
    }
}
