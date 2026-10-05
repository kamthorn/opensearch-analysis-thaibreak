#!/usr/bin/env python3
"""Score results.json from retrieval.py: per-group means with bootstrap 95% CIs and paired differences.

  python3 analyze.py results.json
"""
import json
import math
import random
import sys

runs = json.load(open(sys.argv[1], encoding='utf-8'))
queries, results = runs['queries'], runs['results']
random.seed(20261005)
BOOT = 4000
GROUPS = {'NE (450)': [i for i, q in enumerate(queries) if q['group'].startswith('NE')],
          'word (300)': [i for i, q in enumerate(queries) if q['group'] == 'word']}
METRICS = ['F1', 'P', 'R', 'nDCG@10', 'zero']


def ndcg10(ranked, rel):
    dcg = sum(1 / math.log2(i + 2) for i, d in enumerate(ranked[:10]) if d in rel)
    ideal = sum(1 / math.log2(i + 2) for i in range(min(10, len(rel))))
    return dcg / ideal if ideal else 0.0


def per_query(res, key):
    out = []
    for q in queries:
        rel, got = set(q[key]), res[q['q']]
        gs = set(got)
        tp = len(rel & gs)
        p = tp / len(gs) if gs else 0.0
        r = tp / len(rel) if rel else 0.0
        out.append((2 * p * r / (p + r) if p + r else 0.0, p, r, ndcg10(got, rel), 0 if gs else 1))
    return out


PQ = {k: {rel: per_query(v, rel) for rel in ('strict', 'lenient')} for k, v in results.items()}
mean = lambda xs: sum(xs) / len(xs)


def boot(idx, getter):
    n = len(idx)
    out = sorted(mean([getter(idx[random.randrange(n)]) for _ in range(n)]) for _ in range(BOOT))
    return out[int(.025 * BOOT)], out[int(.975 * BOOT)]


def stat(a, rel, grp, m, b=None):
    idx, mi = GROUPS[grp], METRICS.index(m)
    g = (lambda i: PQ[a][rel][i][mi]) if b is None else (lambda i: PQ[a][rel][i][mi] - PQ[b][rel][i][mi])
    return mean([g(i) for i in idx]), boot(idx, g)


names = list(results)
for rel in ('strict', 'lenient'):
    print(f'\n=== {rel} relevance: mean per query (95% bootstrap CI) ===')
    for grp in GROUPS:
        print(f'\n-- {grp}')
        print('config'.ljust(26), 'F1'.ljust(22), 'P'.ljust(7), 'R'.ljust(7), 'nDCG@10'.ljust(8), 'zero-result')
        for k in names:
            f, ci = stat(k, rel, grp, 'F1')
            print(k.ljust(26), f'{f:.3f} [{ci[0]:.3f},{ci[1]:.3f}]'.ljust(22),
                  *(f'{stat(k, rel, grp, m)[0]:.3f}'.ljust(8) for m in ('P', 'R', 'nDCG@10', 'zero')))

clusters = sorted({k.split('|')[0] for k in names})
if len(clusters) == 2:
    a, b = 'merged', 'base'
    print(f'\n=== paired differences, {a} minus {b} (95% CI; * = CI excludes 0) ===')
    for rel in ('strict', 'lenient'):
        for grp in GROUPS:
            for c in sorted({k.split('|')[1] for k in names}):
                d, ci = stat(f'{a}|{c}', rel, grp, 'F1', f'{b}|{c}')
                print(f'{rel:8} {grp:11} {c:16} F1 {d:+.3f} [{ci[0]:+.3f},{ci[1]:+.3f}]', '*' if ci[0] > 0 or ci[1] < 0 else '')
print('\n=== paired differences against none/none, same dictionary ===')
for cl in clusters:
    for rel in ('strict', 'lenient'):
        for grp in GROUPS:
            for c in sorted({k.split('|')[1] for k in names} - {'none/none'}):
                for m in ('F1', 'nDCG@10'):
                    d, ci = stat(f'{cl}|{c}', rel, grp, m, f'{cl}|none/none')
                    print(f'{cl:7} {rel:8} {grp:11} {c:16} {m:8} {d:+.3f} [{ci[0]:+.3f},{ci[1]:+.3f}]', '*' if ci[0] > 0 or ci[1] < 0 else '')
