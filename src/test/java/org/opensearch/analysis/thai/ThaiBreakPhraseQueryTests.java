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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiTrie;

import java.io.IOException;

/**
 * Unit tests verifying Lucene {@link PhraseQuery} and {@link QueryBuilder} compatibility
 * with {@link ThaiBreakAnalyzer} in token graph mode ({@link DecompoundMode#MIXED}).
 */
public class ThaiBreakPhraseQueryTests extends LuceneTestCase {

    public void testPhraseQueryWithMixedTokenGraph() throws IOException {
        ThaiTrie trie = ThaiDictionaryLoader.loadDefault();
        ThaiBreakAnalyzer analyzer = new ThaiBreakAnalyzer(trie, DecompoundMode.MIXED);

        Directory dir = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(dir, config)) {
            // Doc 1: Has "สนามบิน" (airport)
            Document doc1 = new Document();
            doc1.add(new TextField("content", "ฉันเดินทางไปสนามบินสุวรรณภูมิวันนี้", Field.Store.YES));
            writer.addDocument(doc1);

            // Doc 2: Has "เครื่องบิน" (airplane) and "สนามเด็กเล่น" (playground), but NOT "สนามบิน"
            Document doc2 = new Document();
            doc2.add(new TextField("content", "เด็กๆ นั่งดูเครื่องบินที่สนามเด็กเล่นอย่างมีความสุข", Field.Store.YES));
            writer.addDocument(doc2);

            writer.commit();
        }

        try (DirectoryReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryBuilder queryBuilder = new QueryBuilder(analyzer);

            // 1. Phrase query for the compound word "สนามบิน"
            // In MIXED mode, both "สนามบิน" and sub-tokens "สนาม" + "บิน" are available
            Query phraseQuery = queryBuilder.createPhraseQuery("content", "สนามบิน");
            assertNotNull("Expected valid phrase query", phraseQuery);
            TopDocs hits = searcher.search(phraseQuery, 10);
            assertEquals("Phrase 'สนามบิน' must match exactly Doc 1", 1, hits.totalHits.value());
            Document matched = searcher.storedFields().document(hits.scoreDocs[0].doc);
            assertTrue(matched.get("content").contains("สุวรรณภูมิ"));

            // 2. Direct Lucene PhraseQuery on sub-tokens: "สนาม" followed by "บิน"
            PhraseQuery subTokenPhrase = new PhraseQuery("content", "สนาม", "บิน");
            TopDocs subHits = searcher.search(subTokenPhrase, 10);
            assertEquals("Phrase on sub-tokens ('สนาม', 'บิน') must match Doc 1", 1, subHits.totalHits.value());

            // 3. Multi-word phrase query: "ไป", "สนามบิน"
            PhraseQuery multiWordPhrase = new PhraseQuery("content", "ไป", "สนามบิน");
            TopDocs multiHits = searcher.search(multiWordPhrase, 10);
            assertEquals("Multi-word phrase ('ไป', 'สนามบิน') must match Doc 1", 1, multiHits.totalHits.value());

            // 4. Non-adjacent phrase must NOT match (slop = 0)
            PhraseQuery nonAdjacent = new PhraseQuery("content", "ฉัน", "สุวรรณภูมิ");
            TopDocs noHits = searcher.search(nonAdjacent, 10);
            assertEquals("Non-adjacent phrase with slop=0 must return 0 hits", 0, noHits.totalHits.value());
        } finally {
            dir.close();
            analyzer.close();
        }
    }
}
