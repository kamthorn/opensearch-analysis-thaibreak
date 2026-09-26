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

/**
 * OpenSearch factory for {@link ThaiNumberFilter}.
 *
 * <p>Supported settings:
 * <ul>
 *   <li>{@code keep_original}: {@code true} (default) or {@code false}</li>
 *   <li>{@code convert_digits}: {@code true} (default) or {@code false}</li>
 *   <li>{@code convert_words}: {@code true} (default) or {@code false}</li>
 *   <li>{@code min_word_value}: {@code 0} (default) minimum numerical value for word conversion</li>
 * </ul>
 */
public final class ThaiNumberFilterFactory extends AbstractTokenFilterFactory {

    private final boolean keepOriginal;
    private final boolean convertDigits;
    private final boolean convertWords;
    private final long minWordValue;

    /**
     * Constructs a new factory from index settings and environment.
     *
     * @param indexSettings index settings
     * @param environment   environment
     * @param name          filter name
     * @param settings      component settings
     */
    public ThaiNumberFilterFactory(
        IndexSettings indexSettings,
        Environment environment,
        String name,
        Settings settings
    ) {
        super(indexSettings, name, settings);
        this.keepOriginal = settings.getAsBoolean("keep_original", true);
        this.convertDigits = settings.getAsBoolean("convert_digits", true);
        this.convertWords = settings.getAsBoolean("convert_words", true);
        this.minWordValue = settings.getAsLong("min_word_value", 0L);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new ThaiNumberFilter(tokenStream, keepOriginal, convertDigits, convertWords, minWordValue);
    }
}
