# opensearch-analysis-thaibreak

[![Release](https://img.shields.io/github/v/release/kamthorn/opensearch-analysis-thaibreak?color=brightgreen)](https://github.com/kamthorn/opensearch-analysis-thaibreak/releases)
[![Docker](https://img.shields.io/badge/docker-ghcr.io-blue?logo=docker)](https://github.com/kamthorn/opensearch-analysis-thaibreak/pkgs/container/opensearch-thaibreak)

OpenSearch Analysis Plugin for the Thai language — powered by the **Viterbi + TCC** segmentation engine from [thai-break](https://github.com/kamthorn/thai-break).

## Features

- **Deterministic** segmentation — no JRE locale/platform dependency (unlike the built-in `ThaiTokenizer` which relies on `java.text.BreakIterator`)
- **Frequency-weighted** Viterbi shortest-path algorithm with TCC (Thai Character Cluster) constraints
- **35,072-word** bundled dictionary with category weights (Apache-2.0)
- **User dictionary** support — add domain-specific terms (medical, legal, brand names, etc.)
- Mixed Thai/Latin/numeric content handled correctly
- OOV (out-of-vocabulary) fallback: unknown words are preserved as single tokens

## Comparison

| Feature | Built-in `thai` | `thaibreak` (this plugin) |
|---------|----------------|--------------------------|
| Algorithm | Java `BreakIterator` (JRE dictionary) | Viterbi DAG + TCC |
| Cross-JVM deterministic | ❌ | ✅ |
| User dictionary | ❌ | ✅ |
| Compound words | Varies by JRE | Frequency-weighted |
| Dictionary size | Varies by JRE | 35,072 words |

## Installation & Docker

### 🐳 Run Pre-built Docker Image (Instant Start)

Pre-built Docker images are published to GitHub Container Registry (GHCR):

```bash
# Run OpenSearch 2.18.0 with thaibreak pre-installed
docker run -d -p 9200:9200 -p 9600:9600 \
  -e "discovery.type=single-node" \
  -e "plugins.security.disabled=true" \
  --name opensearch-thaibreak \
  ghcr.io/kamthorn/opensearch-thaibreak:2.18.0

# Verify plugin installation
curl http://localhost:9200/_cat/plugins?v
```

Available Docker tags:
- `ghcr.io/kamthorn/opensearch-thaibreak:latest`
- `ghcr.io/kamthorn/opensearch-thaibreak:2.18.0`
- `ghcr.io/kamthorn/opensearch-thaibreak:2.19.0`
- `ghcr.io/kamthorn/opensearch-thaibreak:3.8.0`

### Or Run with Docker Compose

```bash
docker compose up -d
./docker/test-search.sh
```

### Install into existing OpenSearch

From GitHub Releases (Direct HTTPS URL — no compilation needed):

```bash
# OpenSearch 2.18.0
bin/opensearch-plugin install https://github.com/kamthorn/opensearch-analysis-thaibreak/releases/download/v1.0.0/analysis-thaibreak-2.18.0.0.zip

# OpenSearch 2.17.1
bin/opensearch-plugin install https://github.com/kamthorn/opensearch-analysis-thaibreak/releases/download/v1.0.0/analysis-thaibreak-2.17.1.0.zip

# OpenSearch 2.15.0
bin/opensearch-plugin install https://github.com/kamthorn/opensearch-analysis-thaibreak/releases/download/v1.0.0/analysis-thaibreak-2.15.0.0.zip
```

Or from local ZIP:

```bash
bin/opensearch-plugin install file:///path/to/analysis-thaibreak-2.18.0.0.zip
```

> For full architectural benchmarks, mathematical formulation, and Lucene upstream integration details, see the [Thai Search Whitepaper](docs/THAI_SEARCH_WHITEPAPER.md).

## Usage

### Default analyzer

```json
PUT /my-index
{
  "settings": {
    "analysis": {
      "analyzer": {
        "thai": {
          "type": "thaibreak"
        }
      }
    }
  }
}
```

### Custom tokenizer with user dictionary

Place your word list at `config/analysis/my-words.txt` (one word per line, optional `\t<weight>`), or specify inline rules directly in index settings with `user_dictionary_rules`:

```json
PUT /my-index
{
  "settings": {
    "analysis": {
      "tokenizer": {
        "thai_custom": {
          "type": "thaibreak",
          "decompound_mode": "mixed",
          "user_dictionary": "analysis/my-words.txt",
          "user_dictionary_rules": [
            "ซูเปอร์คอมพิวเตอร์",
            "ดีพเลิร์นนิง\t10.0"
          ]
        }
      },
      "analyzer": {
        "thai_custom": {
          "type": "custom",
          "tokenizer": "thai_custom"
        }
      }
    }
  }
}
```

#### Dynamic Dictionary Reloading

When you update custom dictionary files on disk, you can reload search analyzers without restarting the cluster:

```http
POST /my-index/_reload_search_analyzers
```

### Decompounding Modes (`decompound_mode`)

Just like `analysis-nori` and `analysis-kuromoji`, `thaibreak` supports compound word decomposition:

| Mode | Behavior | Example (`สนามบิน`) | Recommended For |
|---|---|---|---|
| `none` (default) | Do not decompose compound words. | `["สนามบิน"]` | Search time / High precision |
| `discard` | Decompose compound words into parts and discard the compound. | `["สนาม", "บิน"]` | Simple sub-word matching |
| `mixed` | Emit **both** the compound token and its sub-tokens as a **Token Graph** with overlapping positions. | `สนามบิน` (posLen: 2), `สนาม` (posInc: 0), `บิน` (posInc: 1) | **Index time** / High recall + Phrase search |

### Keyboard Mis-typing Filters (`thaibreak_keyboard`)

Allows auto-correcting and matching queries typed without switching keyboard layout:
- **`char_filter: thaibreak_keyboard`**: Pre-tokenization stream conversion (e.g. `l;ylfu` &rarr; `สวัสดี`). Ideal for query analyzers before tokenization so that words can be properly segmented.
- **`token_filter: thaibreak_keyboard`**: Token-level synonym emission (e.g. emits original `l;ylfu` with `posInc=1` and converted `สวัสดี` with `posInc=0`).

#### Parameters:
- `direction`: `qwerty_to_kedmanee` (default), `kedmanee_to_qwerty`, or `both` (token filter only).
- `keep_original`: `true` (default for token filter) or `false`.
- `min_term_length`: Minimum length of term to convert (token filter only, default: `1`).

```json
PUT /thai-keyboard-index
{
  "settings": {
    "analysis": {
      "filter": {
        "thai_keyboard_synonyms": {
          "type": "thaibreak_keyboard",
          "direction": "both",
          "keep_original": true
        }
      },
      "analyzer": {
        "thai_search": {
          "type": "custom",
          "tokenizer": "thaibreak",
          "filter": ["lowercase", "thai_keyboard_synonyms"]
        }
      }
    }
  }
}
```

### Thai Phonetic / Soundex Filter (`thaibreak_soundex`)

Implements the standard **Udom83** Thai soundex algorithm (Master's thesis, Chulalongkorn University, 1983). Generates a 7-character phonetic signature mapping homophones (คำพ้องเสียง) and common spelling variations to identical codes:
- `กาล` / `การ` / `การณ์` &rarr; `ก900000`
- `ลัก` / `รัก` / `รักษ์` &rarr; `ร100000`
- `พันธ์` / `พันธุ์` &rarr; `พ300000`

#### Parameters:
- `keep_original`: `true` (default; emits soundex signature as synonym at `posInc=0`) or `false` (replaces original token).

```json
PUT /thai-phonetic-index
{
  "settings": {
    "analysis": {
      "filter": {
        "thai_soundex_filter": {
          "type": "thaibreak_soundex",
          "keep_original": true
        }
      },
      "analyzer": {
        "thai_phonetic": {
          "type": "custom",
          "tokenizer": "thaibreak",
          "filter": ["lowercase", "thai_soundex_filter"]
        }
      }
    }
  }
}
```

### Thai Tone & Diacritic Stripping Filter (`thaibreak_tone`)

Similar to Lucene's `ASCIIFoldingFilter`, strips tone marks (ไม้เอก-ไม้จัตวา: `\u0E48` - `\u0E4B`), ไม้ไต่คู้ (`\u0E47`), and ทัณฑฆาต/การันต์ (`\u0E4C`) to enable fuzzy/loose search for words with tone ambiguities or common colloquial spellings:
- `นะค่ะ` &rarr; `นะคะ`
- `มงค็ล` &rarr; `มงคล`
- `การ์ด` &rarr; `การด`

#### Parameters:
- `keep_original`: `true` (default; emits tone-stripped token as synonym at `posInc=0`) or `false` (replaces in-place).
- `strip_tones`: `true` (default; strips tone marks and mai tai khu).
- `strip_thanthakhat`: `true` (default; strips thanthakhat/garun).

```json
PUT /thai-loose-index
{
  "settings": {
    "analysis": {
      "filter": {
        "thai_loose_filter": {
          "type": "thaibreak_tone",
          "keep_original": true
        }
      },
      "analyzer": {
        "thai_loose": {
          "type": "custom",
          "tokenizer": "thaibreak",
          "filter": ["lowercase", "thai_loose_filter"]
        }
      }
    }
  }
}
```

### Thai Number & Digit Converter Filter (`thaibreak_number`)

Converts Thai digits (`๐-๙`) and spelled-out Thai written number words (e.g. `หนึ่งแสนสองหมื่น`, `ห้าหมื่น`, `สามร้อย`, `สิบสอง`, `ยี่สิบเอ็ด`) into Arabic numbers (`120000`, `50000`, `300`, `12`, `21`):
- Thai Digits: `๑๒๕๐` &rarr; `1250`, `ชั้น๓` &rarr; `ชั้น3`
- Written Words: `ห้าหมื่น` &rarr; `50000`, `สองล้านสามแสน` &rarr; `2300000`
- Multi-token Sequences: Emits graph token synonyms spanning the combined sequence with `posInc=0` and `posLen=N`.

#### Parameters:
- `keep_original`: `true` (default; emits converted number as synonym alongside original words) or `false` (replaces original).
- `convert_digits`: `true` (default; converts `[๐-๙]` to `[0-9]`).
- `convert_words`: `true` (default; converts spelled-out Thai words to Arabic numbers).
- `min_word_value`: `0` (default; minimum numerical value for word conversion).

```json
PUT /thai-number-index
{
  "settings": {
    "analysis": {
      "filter": {
        "thai_number_synonyms": {
          "type": "thaibreak_number",
          "keep_original": true
        }
      },
      "analyzer": {
        "thai_number_search": {
          "type": "custom",
          "tokenizer": "thaibreak",
          "filter": ["lowercase", "thai_number_synonyms"]
        }
      }
    }
  }
}
```


## Building from source

```bash
./gradlew build -Dopensearch.version=3.8.0
```

The plugin ZIP is produced at `build/distributions/analysis-thaibreak-3.8.0.0.zip`.

## Dictionary format

```
คนขับรถ
พารากอน	10.0
COVID-19
```

Lines starting with `#` are ignored. Weight defaults to `1.0`.

## Performance Benchmark

To run the performance benchmark harness on your machine:

```bash
./gradlew benchmark
```

Empirical results measured on Linux amd64 (JDK 25, 20,000 iterations per workload):

### Workload 1: Short Query (23 chars - Search Query)

| Tokenizer Implementation | Tokens | Throughput | Mean Latency | P50 Latency |
|---|---|---|---|---|
| **Lucene ThaiTokenizer (BreakIterator)** | 7 | 407,372 op/s | 2.45 µs | 0.85 µs |
| **`thaibreak` (`decompound=none`)** | 6 | **121,166 op/s** | **8.25 µs** | **7.32 µs** |
| **`thaibreak` (`decompound=mixed`)** | 12 | **87,379 op/s** | **11.44 µs** | **10.45 µs** |
| **`thaibreak` (`decompound=discard`)** | 9 | **83,025 op/s** | **12.04 µs** | **11.05 µs** |

### Workload 2: Medium Paragraph (157 chars)

| Tokenizer Implementation | Tokens | Throughput | Mean Latency | P50 Latency |
|---|---|---|---|---|
| **Lucene ThaiTokenizer (BreakIterator)** | 27 | 167,478 op/s | 5.97 µs | 3.32 µs |
| **`thaibreak` (`decompound=none`)** | 27 | **22,622 op/s** | **44.21 µs** | **42.67 µs** |
| **`thaibreak` (`decompound=mixed`)** | 47 | **13,375 op/s** | **74.77 µs** | **72.56 µs** |
| **`thaibreak` (`decompound=discard`)** | 37 | **13,391 op/s** | **74.68 µs** | **72.64 µs** |

### Workload 3: Long Article (1,154 chars)

| Tokenizer Implementation | Tokens | Throughput | Mean Latency | P50 Latency |
|---|---|---|---|---|
| **Lucene ThaiTokenizer (BreakIterator)** | 191 | 44,985 op/s | 22.23 µs | 21.87 µs |
| **`thaibreak` (`decompound=none`)** | 202 | **2,310 op/s** | **432.95 µs** | **425.98 µs** |
| **`thaibreak` (`decompound=mixed`)** | 282 | **1,703 op/s** | **587.13 µs** | **570.08 µs** |
| **`thaibreak` (`decompound=discard`)** | 243 | **1,700 op/s** | **588.29 µs** | **578.13 µs** |

### Ancillary Components Throughput

- **`ThaiKeyboardConverter` (Kedmanee &harr; QWERTY)**: **18,380,000 ops/sec** (~54 ns/op)
- **`ThaiSoundex` (Udom83 Phonetic)**: **260,000 ops/sec** (~3.8 µs/op)

## Memory Safety & Streaming

- **Streaming Safe-Chunking ($O(1)$ Memory)**: Rather than buffering arbitrary amounts of text into memory, the tokenizer streams large documents in sliding 8,192-character windows with `findSafeCut()` lookback. It safely breaks on whitespace, newlines, and punctuation without splitting Thai Character Clusters (TCC), preventing OOM on massive text fields or OCR dumps.

## Known limitations

- **User dictionary format**: `user_dictionary` is a flat `word[\tweight]`
  list — it does not support multi-word phrases or synonym mapping.
- **Decompounding is dictionary-bound**: `discard`/`mixed` modes only split
  a compound into parts that are themselves already in the dictionary; they
  won't decompose a genuinely unknown compound.

## Apache Lucene Upstream

ฟีเจอร์หลักของ plugin นี้ได้รับการเสนอเข้า **Apache Lucene Core**:

| PR | หัวข้อ | สถานะ |
|---|---|:---:|
| [#16717](https://github.com/apache/lucene/pull/16717) | ThaiCharFilter + ThaiNormalizer | 🟣 **Merged** |
| [#16718](https://github.com/apache/lucene/pull/16718) | Modern Thai Stopwords | 🟣 **Merged** |
| [#16720](https://github.com/apache/lucene/pull/16720) | ThaiRepeatFilter + OffsetAttribute fix | 🟣 **Merged** |
| [#16722](https://github.com/apache/lucene/pull/16722) | User Dictionary Support | 🟢 **Open** (Review passed) |

## License

Apache-2.0 — see [LICENSE.txt](LICENSE.txt).

Dictionary and core segmentation engine derived from [thai-break](https://github.com/kamthorn/thai-break) (Apache-2.0).

> 📋 **Changelog:** ดูประวัติการเปลี่ยนแปลงทั้งหมดที่ [CHANGELOG.md](CHANGELOG.md)
