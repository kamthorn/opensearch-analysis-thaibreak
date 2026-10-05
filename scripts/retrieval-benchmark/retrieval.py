#!/usr/bin/env python3
"""Retrieval benchmark of analysis-thaibreak on the LST20 test articles.

Queries (seeded, so the same corpus gives the same list):
  450 named entities (150 each PER, ORG, LOC) that occur in 2-40 articles, and
  300 content words (NN/VV/AJ, 3+ letters) that occur in 2-40 articles.
Relevance:
  strict   an article is relevant if the gold annotation has the query as an entity or word
  lenient  an article is relevant if its text contains the query as a substring
Each query is a `match` with operator AND over one field (or a bool of two fields for "layered").

  python3 retrieval.py --lst20 ../../../LST20_Corpus \\
      --cluster merged=http://localhost:19811 --cluster base=http://localhost:19812 --out results.json

Each --cluster is OpenSearch with analysis-thaibreak installed; "base" is the variant built by
make_base_variant.py. Then run analyze.py results.json.
"""
import argparse
import collections
import json
import random
import re
import urllib.error
import urllib.request

from lst20 import THAI, articles, entities, text_of, words_of

# (name, index decompound_mode, search decompound_mode)
CONFIGS = [('none/none', 'none', 'none'), ('mixed/none', 'mixed', 'none'),
           ('mixed/mixed', 'mixed', 'mixed'), ('discard/discard', 'discard', 'discard')]
# (name, boost of the field analyzed with `none`, boost of the sub field indexed with `mixed`)
LAYERED = [('layered 3:1', 3, 1), ('layered 1:1', 1, 1)]


def req(base, method, path, body=None, content_type='application/json'):
    data = body if isinstance(body, bytes) else (json.dumps(body).encode() if body is not None else None)
    r = urllib.request.Request(base + path, data, {'Content-Type': content_type}, method=method)
    try:
        return json.load(urllib.request.urlopen(r, timeout=600))
    except urllib.error.HTTPError as e:
        return json.load(e)


def build_queries(arts, texts):
    random.seed(20261004)
    ent_docs, ent_type, word_docs = collections.defaultdict(set), {}, collections.defaultdict(set)
    for aid, s in arts:
        for t, e in entities(s):
            if THAI.search(e):
                ent_docs[e].add(aid)
                ent_type[e] = t
        for w, pos, ne in words_of(s):
            if ne == 'O' and pos in ('NN', 'VV', 'AJ') and len(w) >= 3 and re.match(r'^[ก-๎]+$', w):
                word_docs[w].add(aid)
    titles = {'นาย', 'นาง', 'นางสาว', 'พล.อ.', 'พ.ต.ท.', 'ดร.'}
    qs = []
    for typ in ('PER', 'ORG', 'LOC'):
        c = sorted(e for e, d in ent_docs.items() if ent_type[e] == typ and 2 <= len(d) <= 40 and len(e) >= 3 and e not in titles)
        random.shuffle(c)
        qs += [('NE-' + typ, e, ent_docs[e]) for e in c[:150]]
    c = sorted(w for w, d in word_docs.items() if 2 <= len(d) <= 40)
    random.shuffle(c)
    qs += [('word', w, word_docs[w]) for w in c[:300]]
    return [dict(group=g, q=q, strict=sorted(strict), lenient=sorted(a for a, t in texts.items() if q in t))
            for g, q, strict in qs]


def analysis(index_mode, search_mode):
    tok = lambda m: {'type': 'thaibreak', 'decompound_mode': m}
    chain = lambda t: {'type': 'custom', 'tokenizer': t, 'filter': ['thaibreak_normalization', 'lowercase']}
    return {'analysis': {'tokenizer': {'t_i': tok(index_mode), 't_q': tok(search_mode)},
                         'analyzer': {'a_i': chain('t_i'), 'a_q': chain('t_q')}}}


def index_corpus(base, idx, settings, mapping, texts):
    req(base, 'DELETE', f'/{idx}')
    r = req(base, 'PUT', f'/{idx}', {'settings': {'number_of_shards': 1, 'number_of_replicas': 0, **settings},
                                       'mappings': {'properties': {'body': mapping}}})
    assert r.get('acknowledged'), r
    lines = []
    for aid, t in texts.items():
        lines += [json.dumps({'index': {'_index': idx, '_id': aid}}), json.dumps({'body': t}, ensure_ascii=False)]
    r = req(base, 'POST', '/_bulk?refresh=true', ('\n'.join(lines) + '\n').encode(), 'application/x-ndjson')
    assert not r.get('errors'), str(r)[:300]
    req(base, 'POST', f'/{idx}/_forcemerge?max_num_segments=1')


def search(base, idx, queries, build):
    out = {}
    for q in queries:
        r = req(base, 'POST', f'/{idx}/_search', {'size': 1000, '_source': False, 'query': build(q['q'])})
        out[q['q']] = [h['_id'] for h in r['hits']['hits']]
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--lst20', required=True, help='LST20 corpus directory (contains test/)')
    ap.add_argument('--cluster', action='append', required=True, metavar='NAME=URL')
    ap.add_argument('--out', default='results.json')
    args = ap.parse_args()

    arts = articles(args.lst20)
    texts = {aid: text_of(s) for aid, s in arts}
    queries = build_queries(arts, texts)
    print(collections.Counter(q['group'] for q in queries), 'articles', len(texts))
    runs = {'queries': queries, 'results': {}}
    match = lambda field: (lambda q: {'match': {field: {'query': q, 'operator': 'and'}}})
    for spec in args.cluster:
        name, base = spec.split('=', 1)
        for cname, im, sm in CONFIGS:
            idx = 'rb-' + cname.replace('/', '-')
            index_corpus(base, idx, analysis(im, sm), {'type': 'text', 'analyzer': 'a_i', 'search_analyzer': 'a_q'}, texts)
            runs['results'][f'{name}|{cname}'] = search(base, idx, queries, match('body'))
            print('done', name, cname, flush=True)
        for lname, b_main, b_sub in LAYERED:
            idx = 'rb-layered'
            index_corpus(base, idx, analysis('none', 'none') | {'analysis': {
                'tokenizer': {'t_none': {'type': 'thaibreak', 'decompound_mode': 'none'}, 't_mixed': {'type': 'thaibreak', 'decompound_mode': 'mixed'}},
                'analyzer': {a: {'type': 'custom', 'tokenizer': t, 'filter': ['thaibreak_normalization', 'lowercase']}
                             for a, t in (('a_none', 't_none'), ('a_mixed', 't_mixed'))}}},
                {'type': 'text', 'analyzer': 'a_none', 'fields': {'sub': {'type': 'text', 'analyzer': 'a_mixed', 'search_analyzer': 'a_none'}}}, texts)
            layered = lambda q: {'bool': {'should': [
                {'match': {'body': {'query': q, 'operator': 'and', 'boost': b_main}}},
                {'match': {'body.sub': {'query': q, 'operator': 'and', 'boost': b_sub}}}], 'minimum_should_match': 1}}
            runs['results'][f'{name}|{lname}'] = search(base, idx, queries, layered)
            print('done', name, lname, flush=True)
    json.dump(runs, open(args.out, 'w'), ensure_ascii=False)


if __name__ == '__main__':
    main()
