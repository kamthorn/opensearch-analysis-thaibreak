"""Read the LST20 corpus (test split) as articles of (word, POS, NE) sentences.

LST20 is not part of this repository and its agreement forbids redistribution; point LST20_DIR at a
local copy. Do not commit the corpus or anything derived from it (the query list included).
"""
import glob
import os
import re

THAI = re.compile(r'[ก-ฮ]')


def articles(lst20_dir, split='test'):
    out = []
    for f in sorted(glob.glob(os.path.join(lst20_dir, split, '*.txt'))):
        sents = [[]]
        for line in open(f, encoding='utf-8', errors='replace'):
            x = line.rstrip('\n').split('\t')
            if len(x) < 3 or not x[0].strip():
                if sents[-1]:
                    sents.append([])
                continue
            sents[-1].append((' ' if x[0] == '_' else x[0], x[1], x[2]))
        out.append((os.path.basename(f)[:-4], [s for s in sents if s]))
    return out


def text_of(sents):
    return '\n'.join(''.join(w for w, _, _ in s) for s in sents)


def entities(sents):
    ents = []
    for s in sents:
        i = 0
        while i < len(s):
            ne = s[i][2]
            if ne.startswith('B_'):
                typ = ne[2:]
                j = i + 1
                parts = [s[i][0]]
                while j < len(s) and s[j][2] in (f'I_{typ}', f'E_{typ}'):
                    parts.append(s[j][0])
                    end = s[j][2].startswith('E_')
                    j += 1
                    if end:
                        break
                ents.append((typ, ''.join(parts)))
                i = j
            else:
                i += 1
    return ents


def words_of(sents):
    return [(w, pos, ne) for s in sents for w, pos, ne in s if w.strip()]
