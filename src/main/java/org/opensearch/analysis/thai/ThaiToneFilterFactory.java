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
 * OpenSearch factory for {@link ThaiToneFilter}.
 *
 * <p>Supported settings:
 * <ul>
 *   <li>{@code keep_original}: {@code true} (default) or {@code false}</li>
 *   <li>{@code strip_tones}: {@code true} (default) or {@code false}</li>
 *   <li>{@code strip_thanthakhat}: {@code true} (default) or {@code false}</li>
 * </ul>
 */
public final class ThaiToneFilterFactory extends AbstractTokenFilterFactory {

    private final boolean keepOriginal;
    private final boolean stripTones;
    private final boolean stripThanthakhat;

    /**
     * Constructs a new factory from index settings and environment.
     *
     * @param indexSettings index settings
     * @param environment   environment
     * @param name          filter name
     * @param settings      component settings
     */
    public ThaiToneFilterFactory(
        IndexSettings indexSettings,
        Environment environment,
        String name,
        Settings settings
    ) {
        super(indexSettings, name, settings);
        this.keepOriginal = settings.getAsBoolean("keep_original", true);
        this.stripTones = settings.getAsBoolean("strip_tones", true);
        this.stripThanthakhat = settings.getAsBoolean("strip_thanthakhat", true);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new ThaiToneFilter(tokenStream, keepOriginal, stripTones, stripThanthakhat);
    }
}
