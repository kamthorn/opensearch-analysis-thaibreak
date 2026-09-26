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

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.opensearch.analysis.thai.engine.ThaiViterbiTokenizer;
import org.opensearch.analysis.thai.engine.ThaiTrie;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Lucene {@link Tokenizer} that segments Thai text using the Viterbi+TCC
 * algorithm from the {@code thai-break} project.
 *
 * <p>Features:
 * <ul>
 *   <li>Deterministic segmentation — no JRE locale dependency.</li>
 *   <li>Weighted dictionary (frequency-aware Viterbi shortest path).</li>
 *   <li>TCC constraints prevent mid-syllable splits.</li>
 *   <li>Supports user-supplied dictionaries for domain-specific terms.</li>
 *   <li>Mixed Thai/non-Thai text: non-Thai tokens pass through as-is.</li>
 * </ul>
 *
 * <p>Token types:
 * <ul>
 *   <li>{@code <THAI>} — Thai word token</li>
 *   <li>{@code <ALPHANUM>} — Latin/numeric token</li>
 *   <li>{@code <OOV>} — Out-of-vocabulary Thai cluster</li>
 * </ul>
 */
public final class ThaiBreakTokenizer extends Tokenizer {

    public static final String TOKEN_TYPE_THAI    = "<THAI>";
    public static final String TOKEN_TYPE_ALPHANUM = "<ALPHANUM>";
    public static final String TOKEN_TYPE_OOV     = "<OOV>";

    private final CharTermAttribute termAtt   = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute   offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute     typeAtt   = addAttribute(TypeAttribute.class);

    private final ThaiViterbiTokenizer engine;

    /** Pending tokens yet to be emitted. */
    private Iterator<String> pending = null;
    /** Character offset of the next character in the reader's stream. */
    private int streamOffset = 0;
    /** Whether we have accumulated text to segment. */
    private boolean done = false;

    private static final int BUFFER_SIZE = 4096;

    public ThaiBreakTokenizer(ThaiTrie trie) {
        this.engine = new ThaiViterbiTokenizer(trie);
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();

        while (pending == null || !pending.hasNext()) {
            if (done) return false;
            if (!readNextChunk()) {
                done = true;
                return false;
            }
        }

        String tok = pending.next();
        // Skip pure-whitespace tokens
        if (tok.isBlank()) {
            streamOffset += tok.length();
            return incrementToken();
        }

        int startOffset = streamOffset;
        streamOffset += tok.length();
        int endOffset = streamOffset;

        termAtt.append(tok);
        offsetAtt.setOffset(correctOffset(startOffset), correctOffset(endOffset));
        typeAtt.setType(tokenType(tok));
        return true;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pending = null;
        streamOffset = 0;
        done = false;
    }

    @Override
    public void end() throws IOException {
        super.end();
        offsetAtt.setOffset(correctOffset(streamOffset), correctOffset(streamOffset));
    }

    // -----------------------------------------------------------------------

    /** Read one chunk of text from the reader and schedule its tokens. */
    private boolean readNextChunk() throws IOException {
        char[] buf = new char[BUFFER_SIZE];
        StringBuilder sb = new StringBuilder();
        int n;
        // Read until buffer full or EOF
        while (sb.length() < BUFFER_SIZE && (n = input.read(buf, 0, buf.length)) > 0) {
            sb.append(buf, 0, n);
        }
        if (sb.isEmpty()) return false;

        List<String> tokens = engine.tokenize(sb.toString(), true /* keep whitespace for offsets */);
        pending = tokens.iterator();
        return true;
    }

    private static String tokenType(String tok) {
        if (tok.isEmpty()) return TOKEN_TYPE_ALPHANUM;
        int first = tok.codePointAt(0);
        if (first >= 0x0E00 && first <= 0x0E7F) return TOKEN_TYPE_THAI;
        if (Character.isLetterOrDigit(first)) return TOKEN_TYPE_ALPHANUM;
        return TOKEN_TYPE_ALPHANUM;
    }
}
