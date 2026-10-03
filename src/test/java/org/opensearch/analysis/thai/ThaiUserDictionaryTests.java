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
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.opensearch.analysis.thai.engine.ThaiDictionaryLoader;
import org.opensearch.analysis.thai.engine.ThaiTrie;
import org.opensearch.common.settings.Settings;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Unit tests for user dictionary loading (file, inline rules, and dynamic reload).
 */
public class ThaiUserDictionaryTests extends BaseTokenStreamTestCase {

    public void testInlineUserDictionaryRules() throws IOException {
        Settings settings = Settings.builder()
            .putList("user_dictionary_rules", "ซูเปอร์คอมพิวเตอร์", "ดีพเลิร์นนิง\t10.0")
            .build();

        ThaiTrie trie = ThaiBreakTokenizerFactory.loadTrie(null, settings);
        assertTrue(trie.contains("ซูเปอร์คอมพิวเตอร์"));
        assertTrue(trie.contains("ดีพเลิร์นนิง"));

        // Verify tokenizer segments the custom compound as 1 token
        Tokenizer tok = new ThaiBreakTokenizer(trie);
        tok.setReader(new StringReader("ฉันใช้ซูเปอร์คอมพิวเตอร์"));
        assertTokenStreamContents(tok, new String[]{"ฉัน", "ใช้", "ซูเปอร์คอมพิวเตอร์"});
    }

    public void testFileUserDictionaryAndReload() throws IOException {
        Path tempDir = createTempDir("thaibreak_dict");
        Path customDict = tempDir.resolve("custom-words.txt");
        Files.writeString(customDict, "คำศัพท์เฉพาะกิจ\n");

        Settings settings = Settings.builder()
            .put("user_dictionary", customDict.toAbsolutePath().toString())
            .build();

        ThaiBreakTokenizerFactory factory = new ThaiBreakTokenizerFactory(
            null,
            null,
            "thaibreak",
            settings
        );

        // Before reload: includes "คำศัพท์เฉพาะกิจ"
        assertTrue(factory.getTrie().contains("คำศัพท์เฉพาะกิจ"));

        Tokenizer tok1 = factory.create();
        tok1.setReader(new StringReader("พบคำศัพท์เฉพาะกิจ"));
        assertTokenStreamContents(tok1, new String[]{"พบ", "คำศัพท์เฉพาะกิจ"});

        // Now append a new word to the file
        Files.writeString(customDict, "คำศัพท์เฉพาะกิจ\nคำใหม่ล่าสุด\n");

        // Dynamically reload factory dictionary
        factory.reload();

        assertTrue(factory.getTrie().contains("คำใหม่ล่าสุด"));

        Tokenizer tok2 = factory.create();
        tok2.setReader(new StringReader("พบคำใหม่ล่าสุด"));
        assertTokenStreamContents(tok2, new String[]{"พบ", "คำใหม่ล่าสุด"});
    }

    public void testLongUserDictionaryWordStaysWhole() throws IOException {
        // Reported bug: words longer than the old 25-char scan window
        // (e.g. a 44-char office name) never matched. The scan window now
        // follows ThaiTrie.maxWordLength().
        String longName = "สำนักงานพัฒนาวิทยาศาสตร์และเทคโนโลยีแห่งชาติ";
        Settings settings = Settings.builder()
            .putList("user_dictionary_rules", longName)
            .build();

        ThaiTrie trie = ThaiBreakTokenizerFactory.loadTrie(null, settings);
        assertTrue(trie.contains(longName));
        assertTrue("maxWordLength must cover the long entry",
            trie.maxWordLength() >= longName.codePointCount(0, longName.length()));

        Tokenizer tok = new ThaiBreakTokenizer(trie);
        tok.setReader(new StringReader("ไปที่" + longName + "พรุ่งนี้"));
        assertTokenStreamContents(tok, new String[]{"ไป", "ที่", longName, "พรุ่งนี้"});
    }

    public void testUserDictionaryWordWithDecomposedSaraAm() throws IOException {
        // Text is matched with Sara Am recomposed, so a rule typed as นํ้า... must be too.
        String typed = "นํ้าตาลมะพร้าวอินทรีย์"; // นํ้าตาลมะพร้าวอินทรีย์
        String canonical = "น้ำตาลมะพร้าวอินทรีย์";
        Settings settings = Settings.builder()
            .putList("user_dictionary_rules", typed)
            .build();

        ThaiTrie trie = ThaiBreakTokenizerFactory.loadTrie(null, settings);
        assertTrue(trie.contains(canonical));

        Tokenizer tok = new ThaiBreakTokenizer(trie);
        tok.setReader(new StringReader("ซื้อ" + canonical));
        assertTokenStreamContents(tok, new String[]{"ซื้อ", canonical});

        // The tokenizer keeps the original characters of the text
        Tokenizer tok2 = new ThaiBreakTokenizer(trie);
        tok2.setReader(new StringReader("ซื้อ" + typed));
        assertTokenStreamContents(tok2, new String[]{"ซื้อ", typed});
    }

    public void testUserDictionaryFileWithDoubleSaraE() throws IOException {
        Path dir = createTempDir();
        Path dict = dir.resolve("words.txt");
        Files.writeString(dict, "เเอปพลิเคชันสุขภาพดีเด่น\t5.0\n");

        ThaiTrie trie;
        try (var in = Files.newInputStream(dict)) {
            trie = ThaiDictionaryLoader.loadFromStream(in, new ThaiTrie());
        }
        assertTrue(trie.contains("แอปพลิเคชันสุขภาพดีเด่น"));
    }
}
