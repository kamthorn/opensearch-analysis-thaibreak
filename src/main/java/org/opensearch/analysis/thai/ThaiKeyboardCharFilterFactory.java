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

import org.opensearch.analysis.thai.engine.ThaiKeyboardConverter;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractCharFilterFactory;

import java.io.Reader;

/**
 * OpenSearch factory for {@link ThaiKeyboardCharFilter}.
 *
 * <p>Supported settings:
 * <ul>
 *   <li>{@code direction}: {@code "qwerty_to_kedmanee"} (default) or {@code "kedmanee_to_qwerty"}</li>
 * </ul>
 */
public final class ThaiKeyboardCharFilterFactory extends AbstractCharFilterFactory {

    private final ThaiKeyboardConverter.Direction direction;

    /**
     * Constructs a new factory from index settings and environment.
     *
     * @param indexSettings index settings
     * @param environment   environment
     * @param name          filter name
     * @param settings      component settings
     */
    public ThaiKeyboardCharFilterFactory(
        IndexSettings indexSettings,
        Environment environment,
        String name,
        Settings settings
    ) {
        super(indexSettings, name);
        String dirSetting = settings.get("direction", "qwerty_to_kedmanee");
        this.direction = ThaiKeyboardConverter.Direction.fromString(dirSetting);
    }

    @Override
    public Reader create(Reader reader) {
        return new ThaiKeyboardCharFilter(reader, direction);
    }
}
