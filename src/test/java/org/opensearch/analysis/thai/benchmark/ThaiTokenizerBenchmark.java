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
package org.opensearch.analysis.thai.benchmark;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.th.ThaiTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.opensearch.analysis.thai.DecompoundMode;
import org.opensearch.analysis.thai.ThaiBreakTokenizer;
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiKeyboardConverter;
import org.opensearch.analysis.thai.engine.ThaiSoundex;
import org.opensearch.analysis.thai.engine.ThaiTrie;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Performance benchmark comparing Lucene's {@link ThaiTokenizer} (JDK BreakIterator)
 * against {@link ThaiBreakTokenizer} (Viterbi + TCC DAG) across multiple text sizes.
 */
public final class ThaiTokenizerBenchmark {

    // 1. Short search query (~25 characters)
    private static final String SHORT_QUERY =
        "ค้นหากระเป๋าสะพายราคาถูก";

    // 2. Medium text paragraph (~160 characters)
    private static final String MEDIUM_PARAGRAPH =
        "การท่องเที่ยวแห่งประเทศไทยประกาศแผนฟื้นฟูเศรษฐกิจและการท่องเที่ยวเชิงอนุรักษ์ในเขตพื้นที่ภาคเหนือ "
        + "เพื่อส่งเสริมวัฒนธรรมและประเพณีท้องถิ่นอย่างยั่งยืน";

    // 3. Long article text (~1,200 characters)
    private static final String LONG_ARTICLE =
        "ปัญญาประดิษฐ์หรือเอไอ (Artificial Intelligence: AI) กำลังเข้ามามีบทบาทสำคัญอย่างยิ่งต่อการขับเคลื่อนเศรษฐกิจดิจิทัลของประเทศไทย "
        + "ไม่ว่าจะเป็นภาคธุรกิจ การเงิน การแพทย์ และการศึกษา โดยเฉพาะระบบการสืบค้นข้อมูลภาษาไทย (Thai Information Retrieval) "
        + "ซึ่งในอดีตมักประสบปัญหาการตัดคำผิดพลาดเนื่องจากภาษาไทยไม่มีการเว้นวรรคระหว่างคำอย่างชัดเจน "
        + "การพัฒนาอัลกอริทึมการตัดคำด้วยเทคนิค Viterbi ร่วมกับ Thai Character Cluster (TCC) และโครงสร้างข้อมูล Trie "
        + "ช่วยยกระดับความแม่นยำในการแยกแยะคำประสม คำยืมภาษาต่างประเทศ และชื่อเฉพาะได้อย่างมีประสิทธิภาพ "
        + "นอกจากนี้ การสนับสนุนโหมดการตัดคำแบบ Token Graph (Mixed Decompound Mode) ยังช่วยเพิ่มอัตราการค้นพบ (Recall) "
        + "ควบคู่ไปกับความแม่นยำ (Precision) ในระดับสากลทัดเทียมกับเครื่องมือวิเคราะห์ภาษาญี่ปุ่น Kuromoji และภาษาเกาหลี Nori "
        + "ทำให้ผู้ใช้งานสามารถค้นหาข้อมูลได้อย่างรวดเร็วและถูกต้องสมบูรณ์แบบมากยิ่งขึ้น "
        + "โครงการนี้จึงถือเป็นก้าวสำคัญของโครงสร้างพื้นฐานภาษาไทยในระบบ OpenSearch และ Apache Lucene";

    private static final int WARMUP_ITERATIONS = 5_000;
    private static final int MEASURE_ITERATIONS = 20_000;

    public static void main(String[] args) throws Exception {
        System.out.println("===============================================================================");
        System.out.println(" OpenSearch Thai Analysis Benchmark: Lucene ThaiTokenizer vs thaibreak");
        System.out.println(" JVM: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println(" OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("===============================================================================\n");

        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();

        // Workloads to evaluate
        Workload[] workloads = new Workload[]{
            new Workload("Short Query (23 chars)", SHORT_QUERY),
            new Workload("Medium Paragraph (157 chars)", MEDIUM_PARAGRAPH),
            new Workload("Long Article (1,154 chars)", LONG_ARTICLE)
        };

        for (Workload w : workloads) {
            System.out.printf(Locale.ROOT, "--- Benchmark Workload: %s ---\n", w.name);

            // 1. Lucene ThaiTokenizer (BreakIterator)
            BenchResult luceneRes = bench(
                "Lucene ThaiTokenizer (BreakIterator)",
                w.text,
                ThaiTokenizer::new
            );

            // 2. ThaiBreakTokenizer (NONE mode)
            BenchResult viterbiNoneRes = bench(
                "thaibreak (Viterbi+TCC, decompound=none)",
                w.text,
                () -> new ThaiBreakTokenizer(trie, DecompoundMode.NONE)
            );

            // 3. ThaiBreakTokenizer (MIXED mode)
            BenchResult viterbiMixedRes = bench(
                "thaibreak (Viterbi+TCC, decompound=mixed)",
                w.text,
                () -> new ThaiBreakTokenizer(trie, DecompoundMode.MIXED)
            );

            // 4. ThaiBreakTokenizer (DISCARD mode)
            BenchResult viterbiDiscardRes = bench(
                "thaibreak (Viterbi+TCC, decompound=discard)",
                w.text,
                () -> new ThaiBreakTokenizer(trie, DecompoundMode.DISCARD)
            );

            printComparison(w, Arrays.asList(luceneRes, viterbiNoneRes, viterbiMixedRes, viterbiDiscardRes));
            System.out.println();
        }

        // Benchmark helper components: Keyboard and Soundex
        System.out.println("--- Benchmark Component: Keyboard Layout Converter & Udom83 Soundex ---");
        benchComponent("ThaiKeyboardConverter.toKedmanee", SHORT_QUERY, () -> ThaiKeyboardConverter.toKedmanee("l;ylfu"));
        benchComponent("ThaiSoundex.udom83", "การท่องเที่ยวแห่งประเทศไทย", () -> ThaiSoundex.udom83("การท่องเที่ยว"));
        System.out.println("\nBenchmark completed successfully.");
    }

    @FunctionalInterface
    interface TokenizerSupplier {
        Tokenizer get();
    }

    private static BenchResult bench(String name, String text, TokenizerSupplier supplier) throws IOException {
        Tokenizer tok = supplier.get();
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            consume(tok, text);
        }

        // Measure
        long[] times = new long[MEASURE_ITERATIONS];
        int tokenCount = 0;
        long totalChars = (long) text.length() * MEASURE_ITERATIONS;

        long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            long t0 = System.nanoTime();
            tokenCount = consume(tok, text);
            times[i] = System.nanoTime() - t0;
        }
        long elapsedTotalNs = System.nanoTime() - start;

        Arrays.sort(times);
        double meanUs = (elapsedTotalNs / (double) MEASURE_ITERATIONS) / 1_000.0;
        double p50Us  = times[MEASURE_ITERATIONS / 2] / 1_000.0;
        double p99Us  = times[(int) (MEASURE_ITERATIONS * 0.99)] / 1_000.0;
        double opsPerSec = (MEASURE_ITERATIONS / (elapsedTotalNs / 1_000_000_000.0));
        double mbPerSec = (totalChars / (1024.0 * 1024.0)) / (elapsedTotalNs / 1_000_000_000.0);

        return new BenchResult(name, tokenCount, opsPerSec, meanUs, p50Us, p99Us, mbPerSec);
    }

    private static void benchComponent(String name, String text, Runnable action) {
        // Warmup
        for (int i = 0; i < 20_000; i++) {
            action.run();
        }
        int iterations = 100_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            action.run();
        }
        long elapsed = System.nanoTime() - start;
        double opsPerSec = iterations / (elapsed / 1_000_000_000.0);
        double meanNs = elapsed / (double) iterations;
        System.out.printf(Locale.ROOT, "%-35s : %,12.0f ops/sec (mean: %6.2f ns/op)\n", name, opsPerSec, meanNs);
    }

    private static int consume(Tokenizer tok, String text) throws IOException {
        tok.setReader(new StringReader(text));
        tok.reset();
        int count = 0;
        while (tok.incrementToken()) {
            count++;
        }
        tok.end();
        tok.close();
        return count;
    }

    private static void printComparison(Workload w, List<BenchResult> results) {
        System.out.printf(
            Locale.ROOT,
            "| %-44s | %-6s | %-12s | %-10s | %-10s | %-10s |\n",
            "Tokenizer Implementation", "Tokens", "Throughput", "Mean Lat", "P50 Lat", "Throughput"
        );
        System.out.printf(
            Locale.ROOT,
            "|----------------------------------------------|--------|--------------|------------|------------|------------|\n"
        );
        for (BenchResult r : results) {
            System.out.printf(
                Locale.ROOT,
                "| %-44s | %6d | %,9.0f op/s | %7.2f µs | %7.2f µs | %7.2f MB/s |\n",
                r.name, r.tokens, r.opsPerSec, r.meanUs, r.p50Us, r.mbPerSec
            );
        }
    }

    private static final class Workload {
        final String name;
        final String text;
        Workload(String name, String text) {
            this.name = name;
            this.text = text;
        }
    }

    private static final class BenchResult {
        final String name;
        final int tokens;
        final double opsPerSec;
        final double meanUs;
        final double p50Us;
        final double p99Us;
        final double mbPerSec;

        BenchResult(String name, int tokens, double opsPerSec, double meanUs, double p50Us, double p99Us, double mbPerSec) {
            this.name = name;
            this.tokens = tokens;
            this.opsPerSec = opsPerSec;
            this.meanUs = meanUs;
            this.p50Us = p50Us;
            this.p99Us = p99Us;
            this.mbPerSec = mbPerSec;
        }
    }
}
