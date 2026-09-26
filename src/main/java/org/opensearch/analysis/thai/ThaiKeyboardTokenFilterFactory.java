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
import org.opensearch.analysis.thai.engine.ThaiKeyboardConverter;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;

/**
 * OpenSearch factory for {@link ThaiKeyboardTokenFilter}.
 *
 * <p>Supported settings:
 * <ul>
 *   <li>{@code direction}: {@code "qwerty_to_kedmanee"} (default), {@code "kedmanee_to_qwerty"}, or {@code "both"}</li>
 *   <li>{@code keep_original}: {@code true} (default) or {@code false}</li>
 *   <li>{@code min_term_length}: minimum term length to convert (default {@code 1})</li>
 * </ul>
 */
public final class ThaiKeyboardTokenFilterFactory extends AbstractTokenFilterFactory {

    private final ThaiKeyboardConverter.Direction direction;
    private final boolean keepOriginal;
    private final int minTermLength;

    /**
     * Constructs a new factory from index settings and environment.
     *
     * @param indexSettings index settings
     * @param environment   environment
     * @param name          filter name
     * @param settings      component settings
     */
    public ThaiKeyboardTokenFilterFactory(
        IndexSettings indexSettings,
        Environment environment,
        String name,
        Settings settings
    ) {
        super(indexSettings, name, settings);
        this.direction = ThaiKeyboardConverter.Direction.fromString(settings.get("direction", "qwerty_to_kedmanee"));
        this.keepOriginal = settings.getAsBoolean("keep_original", true);
        this.minTermLength = settings.getAsInt("min_term_length", 1);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new ThaiKeyboardTokenFilter(tokenStream, direction, keepOriginal, minTermLength);
    }
}
