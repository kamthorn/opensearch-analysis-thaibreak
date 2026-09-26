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

import org.apache.lucene.analysis.CharFilter;
import org.opensearch.analysis.thai.engine.ThaiKeyboardConverter;

import java.io.IOException;
import java.io.Reader;

/**
 * Character filter that converts text between US-QWERTY and Thai Kedmanee layout.
 *
 * <p>Ideal for pre-tokenization query processing so that mis-typed Thai sentences
 * can be segmented by the Thai tokenizer.
 */
public final class ThaiKeyboardCharFilter extends CharFilter {

    private final ThaiKeyboardConverter.Direction direction;

    /**
     * Creates a new keyboard layout character filter with default direction (QWERTY &rarr; Kedmanee).
     *
     * @param in underlying reader
     */
    public ThaiKeyboardCharFilter(Reader in) {
        this(in, ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE);
    }

    /**
     * Creates a new keyboard layout character filter with specified direction.
     *
     * @param in underlying reader
     * @param direction conversion direction
     */
    public ThaiKeyboardCharFilter(Reader in, ThaiKeyboardConverter.Direction direction) {
        super(in);
        this.direction = direction == null ? ThaiKeyboardConverter.Direction.QWERTY_TO_KEDMANEE : direction;
    }

    private char convert(char c) {
        if (direction == ThaiKeyboardConverter.Direction.KEDMANEE_TO_QWERTY) {
            return ThaiKeyboardConverter.toQwerty(c);
        } else {
            return ThaiKeyboardConverter.toKedmanee(c);
        }
    }

    @Override
    public int read() throws IOException {
        int c = input.read();
        if (c == -1) return -1;
        return convert((char) c);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int numRead = input.read(cbuf, off, len);
        if (numRead <= 0) return numRead;
        for (int i = off; i < off + numRead; i++) {
            cbuf[i] = convert(cbuf[i]);
        }
        return numRead;
    }

    @Override
    protected int correct(int currentOff) {
        // 1-to-1 character mapping preserves exact offset
        return currentOff;
    }
}
