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

Place your word list at `config/analysis/my-words.txt` (one word per line, optional `\t<weight>`):

```json
PUT /my-index
{
  "settings": {
    "analysis": {
      "tokenizer": {
        "thai_custom": {
          "type": "thaibreak",
          "user_dictionary": "analysis/my-words.txt"
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

## License

Apache-2.0 — see [LICENSE.txt](LICENSE.txt).

Dictionary and core segmentation engine derived from [thai-break](https://github.com/kamthorn/thai-break) (Apache-2.0).
