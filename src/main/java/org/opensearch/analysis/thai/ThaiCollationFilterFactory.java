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
import org.opensearch.analysis.thai.ThaiCollationKey.Decomposition;
import org.opensearch.analysis.thai.ThaiCollationKey.Strength;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;

import java.util.Locale;

/**
 * OpenSearch factory for {@link ThaiCollationFilter}.
 *
 * <p>Supported settings:
 * <ul>
 *   <li>{@code strength}: {@code primary}, {@code secondary}, {@code tertiary} (default), or {@code identical}</li>
 *   <li>{@code decomposition}: {@code none} (default), {@code canonical}, or {@code full}</li>
 * </ul>
 */
public final class ThaiCollationFilterFactory extends AbstractTokenFilterFactory {

    private final ThaiCollationKey collationKey;

    /**
     * Constructs a new factory from index settings and environment.
     *
     * @param indexSettings index settings
     * @param environment   environment
     * @param name          filter name
     * @param settings      component settings
     */
    public ThaiCollationFilterFactory(
            IndexSettings indexSettings,
            Environment environment,
            String name,
            Settings settings
    ) {
        super(indexSettings, name, settings);

        Strength strength = parseStrength(settings.get("strength", "tertiary"));
        Decomposition decomposition = parseDecomposition(settings.get("decomposition", "none"));
        this.collationKey = new ThaiCollationKey(strength, decomposition);
    }

    private static Strength parseStrength(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "primary":
                return Strength.PRIMARY;
            case "secondary":
                return Strength.SECONDARY;
            case "identical":
                return Strength.IDENTICAL;
            case "tertiary":
            default:
                return Strength.TERTIARY;
        }
    }

    private static Decomposition parseDecomposition(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "canonical":
                return Decomposition.CANONICAL;
            case "full":
                return Decomposition.FULL;
            case "none":
            default:
                return Decomposition.NONE;
        }
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new ThaiCollationFilter(tokenStream, collationKey);
    }
}
