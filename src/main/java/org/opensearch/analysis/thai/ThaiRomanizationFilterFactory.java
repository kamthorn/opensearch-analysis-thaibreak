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

import org.apache.lucene.analysis.TokenStream;
import org.opensearch.analysis.thai.engine.ThaiRomanizer;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * OpenSearch factory for {@link ThaiRomanizationFilter}.
 *
 * <p>Supported settings:
 * <ul>
 *   <li>{@code keep_original}: {@code true} (default) or {@code false}</li>
 *   <li>{@code romanizations_path}: path to custom romanizations file relative to OpenSearch config</li>
 *   <li>{@code romanizations}: inline list of romanization rules (e.g. {@code ["บางกอก=>bangkok"]})</li>
 * </ul>
 */
public final class ThaiRomanizationFilterFactory extends AbstractTokenFilterFactory {

    private final boolean keepOriginal;
    private final ThaiRomanizer romanizer;

    /**
     * Constructs a new factory from index settings and environment.
     *
     * @param indexSettings index settings
     * @param environment   environment
     * @param name          filter name
     * @param settings      component settings
     */
    public ThaiRomanizationFilterFactory(
            IndexSettings indexSettings,
            Environment environment,
            String name,
            Settings settings
    ) {
        super(indexSettings, name, settings);
        this.keepOriginal = settings.getAsBoolean("keep_original", true);

        ThaiRomanizer base = ThaiRomanizer.loadDefault();

        String pathStr = settings.get("romanizations_path");
        if (pathStr != null && !pathStr.isBlank()) {
            Path file = (environment != null && environment.configDir() != null)
                    ? environment.configDir().resolve(pathStr.strip())
                    : Path.of(pathStr.strip());
            try (InputStream is = Files.newInputStream(file)) {
                base = ThaiRomanizer.loadFromStream(is);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to load custom romanizations from " + file, e);
            }
        }

        List<String> inlineRules = settings.getAsList("romanizations", null);
        if (inlineRules != null && !inlineRules.isEmpty()) {
            base = ThaiRomanizer.fromRules(inlineRules, base);
        }

        this.romanizer = base;
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new ThaiRomanizationFilter(tokenStream, romanizer, keepOriginal);
    }
}
