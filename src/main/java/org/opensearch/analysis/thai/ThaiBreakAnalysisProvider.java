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

import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.TokenizerFactory;

/**
 * OpenSearch {@link org.opensearch.index.analysis.TokenizerFactory} provider
 * for the {@code thaibreak} tokenizer type.
 *
 * <p>Delegates to {@link ThaiBreakTokenizerFactory}.
 */
public final class ThaiBreakAnalysisProvider {

    private ThaiBreakAnalysisProvider() {}

    /**
     * Factory method registered with OpenSearch analysis module.
     */
    public static ThaiBreakTokenizerFactory createTokenizerFactory(
            IndexSettings indexSettings,
            Environment env,
            String name,
            Settings settings) {
        return new ThaiBreakTokenizerFactory(indexSettings, env, name, settings);
    }
}
