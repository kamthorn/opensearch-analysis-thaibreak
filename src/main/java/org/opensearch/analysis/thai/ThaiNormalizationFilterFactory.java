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
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;
import org.opensearch.index.analysis.NormalizingTokenFilterFactory;

/**
 * OpenSearch factory for {@link ThaiNormalizationFilter} ({@code thaibreak_normalization}).
 * No settings. Also usable in a custom {@code normalizer} for keyword fields.
 */
public final class ThaiNormalizationFilterFactory extends AbstractTokenFilterFactory
        implements NormalizingTokenFilterFactory {

    /**
     * Constructs a new factory from index settings and environment.
     *
     * @param indexSettings index settings
     * @param environment   environment
     * @param name          filter name
     * @param settings      component settings
     */
    public ThaiNormalizationFilterFactory(
        IndexSettings indexSettings,
        Environment environment,
        String name,
        Settings settings
    ) {
        super(indexSettings, name, settings);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new ThaiNormalizationFilter(tokenStream);
    }
}
