# Changelog

All notable changes to `opensearch-analysis-thaibreak` are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).  
Versioning follows `<opensearch.version>.0` (e.g. `2.18.0.0` for OpenSearch 2.18.0).

---

## [v1.0.0] — 2026-09-26

### ✨ New Features

- **Viterbi + TCC Thai Tokenizer (`thaibreak`)**: Deterministic Thai word segmentation engine using frequency-weighted Viterbi dynamic programming constrained by Thai Character Clusters (TCC). Replaces JVM `BreakIterator` dependency with cross-platform deterministic behaviour.

- **Compound Word Decomposition (`decompound_mode`)**: Three modes — `none`, `discard`, `mixed`. Mixed mode emits both compound and constituent words as a Lucene Graph Token Stream (`posLen` + `posInc=0`), enabling full subword search via standard `PhraseQuery`.

- **Streaming Safe-Chunking ($O(1)$ Memory)**: Processes arbitrarily large documents (OCR, legal PDFs) using a sliding 8,192-character buffer with `findSafeCut()` lookback, preventing OOM and GC pauses on very large fields.

- **User Dictionary Support**: Configure custom domain-specific terms via `user_dictionary`, `user_dictionary_path`, or inline `user_dictionary_rules` (medical, legal, brand names, etc.). Supports atomic hot-reload.

- **Thai Keyboard Converter (`thaibreak_keyboard` / `thai_keyboard`)**: Recovers queries typed without switching the keyboard layout (Kedmanee ↔ QWERTY). Available as both `CharFilter` and `TokenFilter`.

- **Thai Soundex / Phonetic Filter (`thaibreak_soundex` / `thai_soundex`)**: Implements the Udom83 algorithm (Chulalongkorn University, 1983). Maps homophones and common spelling variants to identical 7-character phonetic signatures (e.g. `กาล`, `การ`, `การณ์` → `ก900000`).

- **Thai Tone & Diacritic Normalizer (`thaibreak_tone` / `thai_tone`)**: Strips tone marks (ไม้เอก-จัตวา: `\u0E48`–`\u0E4B`), ไม้ไต่คู้ (`\u0E47`), and ทัณฑฆาต/การันต์ (`\u0E4C`) for fault-tolerant "loose" search (เช่น `นะค่ะ` ↔ `นะคะ`, `มงค็ล` ↔ `มงคล`). Supports `keep_original` dual-emission.

- **Thai Number Converter (`thaibreak_number` / `thai_number`)**: Converts Thai digits (`๐–๙` → `0–9`) and spelled-out Thai written numbers (`ห้าหมื่น` → `50000`, `ยี่สิบเอ็ด` → `21`, `สองล้านสามแสน` → `2300000`) to Arabic numerals as graph synonyms. Prevents false matches on common Thai words (`เก้าอี้`, `ร้อยกรอง`).

### 📦 Distribution & Infrastructure

- Multi-version release packaging: pre-built ZIP artifacts for OpenSearch `2.11.1`, `2.15.0`, `2.17.1`, `2.18.0`, `2.19.0`, and `3.8.0`.
- SHA-512 checksums alongside each artifact.
- GitHub Actions automated Release workflow (`release.yml`): triggers on `v*` tags, runs all tests, packages all ZIPs, and publishes the GitHub Release automatically.
- GitHub Actions multi-version Matrix Build (`matrix-build.yml`).
- One-Click Docker Compose evaluation environment (`docker-compose.yml` + `docker/Dockerfile` + `docker/test-search.sh`).
- Technical Architecture Whitepaper (`docs/THAI_SEARCH_WHITEPAPER.md`).

### 🔒 Quality & Safety

- All tests pass with Lucene randomized testing framework across multiple locales and seeds.
- `forbiddenApis` checks pass (no unsafe/forbidden API usage).
- `licenseHeaders` checks pass (Apache 2.0 on all source files).
- Plugin JAR size: ~160 KB (including embedded 25,907-word corpus dictionary).

### 🌐 Supported OpenSearch Versions

| OpenSearch Version | Artifact |
| :--- | :--- |
| 2.11.1 | `analysis-thaibreak-2.11.1.0.zip` |
| 2.15.0 | `analysis-thaibreak-2.15.0.0.zip` |
| 2.17.1 | `analysis-thaibreak-2.17.1.0.zip` |
| 2.18.0 | `analysis-thaibreak-2.18.0.0.zip` |
| 2.19.0 | `analysis-thaibreak-2.19.0.0.zip` |
| 3.8.0  | `analysis-thaibreak-3.8.0.0.zip`  |
