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
package org.opensearch.analysis.thai.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Ultra-compact, read-only Directed Acyclic Word Graph (DAWG) / Minimal DFA.
 * Compatible with the {@code TBD1} binary specification in {@code thai-break}.
 */
public final class CompactDawg {

    private final byte[] data;
    private final int[] offsets;
    private final int numStates;
    private final int numWords;

    /**
     * Constructs a {@link CompactDawg} from raw binary byte array.
     *
     * @param data binary data conforming to TBD1 specification
     * @throws IOException if binary is invalid or corrupted
     */
    public CompactDawg(byte[] data) throws IOException {
        if (data == null || data.length < 12) {
            throw new IOException("Invalid DAWG data: buffer too small");
        }
        if (data[0] != 'T' || data[1] != 'B' || data[2] != 'D' || data[3] != '1') {
            throw new IOException("Invalid DAWG header: expected 'TBD1' magic");
        }

        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.numStates = bb.getInt(4);
        this.numWords = bb.getInt(8);
        this.data = data;

        this.offsets = new int[numStates];
        int curr = 12;
        for (int i = 0; i < numStates; i++) {
            if (curr >= data.length) {
                throw new IOException("Corrupted DAWG at state index " + i);
            }
            offsets[i] = curr;
            int numEdges = data[curr] & 0x7F;
            curr += 1 + numEdges * 4;
        }
    }

    /**
     * Loads a {@link CompactDawg} from an {@link InputStream}.
     *
     * @param is input stream of binary DAWG
     * @return initialized {@link CompactDawg}
     * @throws IOException on read or validation failure
     */
    public static CompactDawg loadFromStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return new CompactDawg(baos.toByteArray());
    }

    /** Number of words stored in this DAWG. */
    public int numWords() {
        return numWords;
    }

    /** Number of states in this minimal automaton. */
    public int numStates() {
        return numStates;
    }

    /** Total size of binary DAWG data in bytes. */
    public int sizeInBytes() {
        return data.length;
    }

    /**
     * Checks if the exact word exists in the DAWG.
     *
     * @param word word to query
     * @return {@code true} if word is accepted
     */
    public boolean contains(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        int state = 0;
        int len = word.length();
        for (int i = 0; i < len; i++) {
            char ch = word.charAt(i);
            state = transition(state, ch);
            if (state == -1) {
                return false;
            }
        }
        return isFinal(state);
    }

    /**
     * Performs a state transition on character {@code ch}.
     *
     * @param state current state id (0 = root)
     * @param ch    input character
     * @return target state id, or -1 if no transition exists
     */
    public int transition(int state, char ch) {
        if (state < 0 || state >= numStates) {
            return -1;
        }
        int off = offsets[state];
        int numEdges = data[off] & 0x7F;
        int low = 0;
        int high = numEdges - 1;
        int base = off + 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int edgeOff = base + mid * 4;
            int edgeChar = (data[edgeOff] & 0xFF) | ((data[edgeOff + 1] & 0xFF) << 8);
            if (edgeChar == ch) {
                return (data[edgeOff + 2] & 0xFF) | ((data[edgeOff + 3] & 0xFF) << 8);
            } else if (edgeChar < ch) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    /**
     * Checks if state is an accepting (final) state.
     *
     * @param state state id
     * @return {@code true} if state accepts a word
     */
    public boolean isFinal(int state) {
        if (state < 0 || state >= numStates) {
            return false;
        }
        return (data[offsets[state]] & 0x80) != 0;
    }
}
