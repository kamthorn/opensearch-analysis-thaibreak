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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
 *     <td>Path (or comma-separated paths) to custom word list(s) relative to the OpenSearch config directory.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code user_dictionary_rules}</td>
 *     <td>(none)</td>
 *     <td>Inline list of custom words/weights in the index settings.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code decompound_mode}</td>
 *     <td>{@code none}</td>
 *     <td>Compound word decompounding mode: {@code none}, {@code discard}, or {@code mixed}.</td>
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
 *         "decompound_mode": "mixed",
 *         "user_dictionary": "analysis/my-thai-dict.txt",
 *         "user_dictionary_rules": ["คำเฉพาะ 10.0", "คำใหม่"]
 *       }
 *     }
 *   }
 * }
 * }</pre>
 */
public final class ThaiBreakTokenizerFactory implements TokenizerFactory {

    private final String name;
    private final Environment env;
    private final Settings settings;
    private final DecompoundMode mode;
    private final AtomicReference<ThaiTrie> trieRef;

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
        this.env = env;
        this.settings = settings;
        this.mode = DecompoundMode.fromString(settings.get("decompound_mode", "none"));
        this.trieRef = new AtomicReference<>(loadTrie(env, settings));
    }

    /**
     * Loads the trie with default dictionary, optional external file(s), and inline rules.
     *
     * @param env      environment
     * @param settings settings
     * @return populated {@link ThaiTrie}
     */
    public static ThaiTrie loadTrie(Environment env, Settings settings) {
        ThaiTrie t = ThaiDictionaryLoader.loadDefault();

        String userDictPath = settings.get("user_dictionary");
        List<String> rules = settings.getAsList("user_dictionary_rules", null);

        if ((userDictPath != null && !userDictPath.isBlank()) || (rules != null && !rules.isEmpty())) {
            t = t.copy();
        }

        if (userDictPath != null && !userDictPath.isBlank()) {
            String[] paths = userDictPath.split(",");
            for (String p : paths) {
                p = p.strip();
                if (p.isEmpty()) continue;
                Path dictPath = (env != null && env.configDir() != null)
                    ? env.configDir().resolve(p)
                    : Path.of(p);
                if (Files.exists(dictPath)) {
                    try (InputStream is = Files.newInputStream(dictPath)) {
                        ThaiDictionaryLoader.loadFromStream(is, t);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to load user dictionary: " + dictPath, e);
                    }
                } else {
                    throw new IllegalArgumentException("User dictionary file not found: " + dictPath);
                }
            }
        }

        if (rules != null && !rules.isEmpty()) {
            ThaiDictionaryLoader.loadFromLines(rules, t);
        }

        return t;
    }

    /**
     * Reloads the user dictionary dynamically from disk and updates the active trie.
     */
    public synchronized void reload() {
        this.trieRef.set(loadTrie(env, settings));
    }

    /**
     * Returns the currently active {@link ThaiTrie}.
     *
     * @return active trie
     */
    public ThaiTrie getTrie() {
        return trieRef.get();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Tokenizer create() {
        return new ThaiBreakTokenizer(trieRef.get(), mode);
    }
}
