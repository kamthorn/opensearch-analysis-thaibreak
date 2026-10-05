# Retrieval benchmark

Measures how well `analysis-thaibreak` finds the right articles: dictionary (base only or base plus
thai-break-dict-extra) and decompounding setup compared on 483 LST20 test articles with 750 queries.
Results are in the plugin README ("Retrieval benchmark").

## Needs

- the LST20 corpus (NECTEC agreement; not in this repository, and neither is anything derived from it, so
  the query list is generated and never committed)
- two OpenSearch clusters with the plugin installed: one with the released ZIP, one with the ZIP made by
  `make_base_variant.py` (the same plugin whose dictionary is only the base words of thai-break)

## Run

```bash
python3 make_base_variant.py analysis-thaibreak-3.9.0.0.zip ../../../thai-break/data/words.txt \
    analysis-thaibreak-base-3.9.0.0.zip

# one container per ZIP, e.g. FROM opensearchproject/opensearch:3.9.0 and
#   opensearch-plugin install --batch file:///tmp/plugin.zip
python3 retrieval.py --lst20 ../../../LST20_Corpus \
    --cluster merged=http://localhost:19811 --cluster base=http://localhost:19812 --out results.json
python3 analyze.py results.json
```

`retrieval.py` indexes the corpus once per analysis setup (`index mode / search mode`, plus "layered": the
main field analyzed with `none` and a `.sub` field indexed with `mixed`), runs every query, and stores the
ranked article ids. `analyze.py` reports P, R, F1 and nDCG@10 per query group with bootstrap 95% confidence
intervals over queries, and paired differences.

Two runs return the same hit sets, but articles with equal scores can come in a different order, which moves
nDCG@10 by up to about 0.002.
