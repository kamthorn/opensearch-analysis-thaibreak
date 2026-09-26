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

import java.util.Locale;

/**
 * Decompounding mode for compound words in {@link ThaiBreakTokenizer}.
 */
public enum DecompoundMode {
    /**
     * Do not decompose compound words. Leaves compound words as single tokens (high precision).
     */
    NONE,

    /**
     * Decomposes compound words and discards the original compound token, emitting only the parts.
     */
    DISCARD,

    /**
     * Emits both the original compound word and its constituent parts in a token graph structure
     * (high recall + phrase match compatibility).
     */
    MIXED;

    public static DecompoundMode fromString(String val) {
        if (val == null || val.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(val.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid decompound_mode: '" + val + "'. Valid options are: none, discard, mixed."
            );
        }
    }
}
