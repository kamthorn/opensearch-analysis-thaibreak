# opensearch-analysis-thaibreak

OpenSearch Analysis Plugin for the Thai language — powered by the **Viterbi + TCC** segmentation engine from [thai-break](https://github.com/kamthorn/thai-break).

## Features

- **Deterministic** segmentation — no JRE locale/platform dependency (unlike the built-in `ThaiTokenizer` which relies on `java.text.BreakIterator`)
- **Frequency-weighted** Viterbi shortest-path algorithm with TCC (Thai Character Cluster) constraints
- **25,907-word** bundled dictionary (Apache-2.0)
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
| Dictionary size | Varies by JRE | 25,907 words |

## Installation

```bash
bin/opensearch-plugin install file:///path/to/opensearch-analysis-thaibreak-3.8.0.0.zip
```

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

## Known limitations

- **Whole-field buffering**: segmentation is a global Viterbi shortest-path
  computation, not a streaming algorithm, so each field's text is fully
  buffered in memory before it is tokenized. This is normal for typical
  text fields but worth knowing if you plan to analyze extremely large
  documents.
- **User dictionary format**: `user_dictionary` is a flat `word[\tweight]`
  list — it does not support multi-word phrases or synonym mapping.
- **Decompounding is dictionary-bound**: `discard`/`mixed` modes only split
  a compound into parts that are themselves already in the dictionary; they
  won't decompose a genuinely unknown compound.

## License

Apache-2.0 — see [LICENSE.txt](LICENSE.txt).

Dictionary and core segmentation engine derived from [thai-break](https://github.com/kamthorn/thai-break) (Apache-2.0).
