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
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.opensearch.analysis.thai.engine.ThaiViterbiTokenizer;
import org.opensearch.analysis.thai.engine.ThaiTrie;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lucene {@link Tokenizer} that segments Thai text using the Viterbi+TCC
 * algorithm from the {@code thai-break} project, with support for compound
 * word decomposition modes ({@link DecompoundMode}).
 *
 * <p>Features:
 * <ul>
 *   <li>Deterministic segmentation — no JRE locale dependency.</li>
 *   <li>Weighted dictionary (frequency-aware Viterbi shortest path).</li>
 *   <li>TCC constraints prevent mid-syllable splits.</li>
 *   <li>Supports user-supplied dictionaries for domain-specific terms.</li>
 *   <li>Compound word decompounding (NONE, DISCARD, MIXED graph).</li>
 *   <li>Mixed Thai/non-Thai text: non-Thai tokens pass through as-is.</li>
 * </ul>
 *
 * <p>Token types:
 * <ul>
 *   <li>{@code <THAI>} — Thai word token</li>
 *   <li>{@code <ALPHANUM>} — Latin/numeric token</li>
 * </ul>
 */
public final class ThaiBreakTokenizer extends Tokenizer {

    public static final String TOKEN_TYPE_THAI     = "<THAI>";
    public static final String TOKEN_TYPE_ALPHANUM = "<ALPHANUM>";

    private final CharTermAttribute          termAtt   = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute              offsetAtt = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute   posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute      posLenAtt = addAttribute(PositionLengthAttribute.class);
    private final TypeAttribute                typeAtt   = addAttribute(TypeAttribute.class);

    private final ThaiViterbiTokenizer engine;
    private final DecompoundMode mode;

    private final List<Token> pendingTokens = new ArrayList<>();
    private int tokenIndex = 0;

    /** Character offset of the next character in the reader's stream. */
    private int streamOffset = 0;
    /** Whether we have accumulated text to segment. */
    private boolean done = false;

    private static final int BUFFER_SIZE = 4096;

    public ThaiBreakTokenizer(ThaiTrie trie) {
        this(trie, DecompoundMode.NONE);
    }

    public ThaiBreakTokenizer(ThaiTrie trie, DecompoundMode mode) {
        this.engine = new ThaiViterbiTokenizer(trie);
        this.mode = mode == null ? DecompoundMode.NONE : mode;
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();

        while (tokenIndex >= pendingTokens.size()) {
            if (done) return false;
            if (!readNextChunk()) {
                done = true;
                return false;
            }
        }

        Token t = pendingTokens.get(tokenIndex++);
        termAtt.append(t.term);
        offsetAtt.setOffset(correctOffset(t.startOffset), correctOffset(t.endOffset));
        posIncAtt.setPositionIncrement(t.posInc);
        posLenAtt.setPositionLength(t.posLen);
        typeAtt.setType(t.type);
        return true;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pendingTokens.clear();
        tokenIndex = 0;
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
        while (sb.length() < BUFFER_SIZE && (n = input.read(buf, 0, buf.length)) > 0) {
            sb.append(buf, 0, n);
        }
        if (sb.isEmpty()) return false;

        List<String> rawTokens = engine.tokenize(sb.toString(), true /* keep whitespace for offsets */);
        pendingTokens.clear();
        tokenIndex = 0;

        for (String tok : rawTokens) {
            int tokStart = streamOffset;
            int tokLen = tok.length();
            streamOffset += tokLen;
            int tokEnd = streamOffset;

            // Skip whitespace
            if (tok.isBlank()) {
                continue;
            }

            String type = tokenType(tok);
            List<String> parts = null;
            if (mode != DecompoundMode.NONE && TOKEN_TYPE_THAI.equals(type)) {
                parts = engine.decompose(tok);
            }

            if (parts == null || parts.size() <= 1) {
                // Not a compound or mode == NONE
                pendingTokens.add(new Token(tok, tokStart, tokEnd, 1, 1, type));
            } else if (mode == DecompoundMode.DISCARD) {
                // Emit only the decomposed parts sequentially
                int partStart = tokStart;
                for (String part : parts) {
                    int partEnd = partStart + part.length();
                    pendingTokens.add(new Token(part, partStart, partEnd, 1, 1, type));
                    partStart = partEnd;
                }
            } else if (mode == DecompoundMode.MIXED) {
                // Emit compound token first (posInc = 1, posLen = parts.size())
                int numParts = parts.size();
                pendingTokens.add(new Token(tok, tokStart, tokEnd, 1, numParts, type));

                // Then emit decomposed parts (first part has posInc = 0, subsequent parts have posInc = 1)
                int partStart = tokStart;
                for (int i = 0; i < numParts; i++) {
                    String part = parts.get(i);
                    int partEnd = partStart + part.length();
                    int posInc = (i == 0) ? 0 : 1;
                    pendingTokens.add(new Token(part, partStart, partEnd, posInc, 1, type));
                    partStart = partEnd;
                }
            }
        }
        return !pendingTokens.isEmpty();
    }

    private static String tokenType(String tok) {
        if (tok.isEmpty()) return TOKEN_TYPE_ALPHANUM;
        int first = tok.codePointAt(0);
        if (first >= 0x0E00 && first <= 0x0E7F) return TOKEN_TYPE_THAI;
        return TOKEN_TYPE_ALPHANUM;
    }

    private static final class Token {
        final String term;
        final int startOffset;
        final int endOffset;
        final int posInc;
        final int posLen;
        final String type;

        Token(String term, int startOffset, int endOffset, int posInc, int posLen, String type) {
            this.term = term;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.posInc = posInc;
            this.posLen = posLen;
            this.type = type;
        }
    }
}
