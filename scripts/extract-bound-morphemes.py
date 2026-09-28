#!/usr/bin/env python3
# หา "คำ" ในพจนานุกรม ThaiBreak ที่ในความเป็นจริง (ตามคลังข้อความ LST20 ที่ตัดคำโดยมนุษย์)
# แทบไม่เคยถูกใช้ "เดี่ยวๆ" เลย แต่มักเป็นส่วนหนึ่ง (prefix/suffix) ของคำอื่นเสมอ
import bisect
import glob
import itertools
import json
import os
import re
import sys
from collections import Counter

LST20_DIR = os.environ.get('LST20_DIR', '/home/kamthorn/code/LST20_Corpus')
DICT_PATH = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/org/opensearch/analysis/thai/words.txt')
TEST_DIR = f'{LST20_DIR}/test'
OUT_PATH = os.path.join(os.path.dirname(__file__), '..', 'build/bound_morphemes_report.json')

THAI_RE = re.compile(r'^[ก-ฺเ-๎]+$')  # อักษร/สระ/วรรณยุกต์ไทยล้วน


def load_gold_tokens():
    files = [f for f in glob.glob(f'{LST20_DIR}/train/*.txt')
                       + glob.glob(f'{LST20_DIR}/eval/*.txt')  # test/ ไม่ใช้ตอนสกัด candidate — เก็บไว้วัดผลแบบ held-out
             if not f.split('/')[-1].startswith('._')]
    counts = Counter()
    total = 0
    for f in files:
        with open(f, encoding='utf-8', errors='ignore') as fh:
            for line in fh:
                line = line.rstrip('\n')
                if not line:
                    continue
                word = line.split('\t', 1)[0]
                if word == '_' or not word:
                    continue
                counts[word] += 1
                total += 1
    return counts, total, len(files)


def load_dict():
    words = {}
    with open(DICT_PATH, encoding='utf-8') as fh:
        for line in fh:
            line = line.rstrip('\n')
            if not line or line.startswith('#'):
                continue
            parts = line.split('\t')
            words[parts[0]] = float(parts[1]) if len(parts) > 1 else 1.0
    return words


class PrefixIndex:
    """เรียงคำ (หรือคำกลับด้าน สำหรับ suffix) แล้วทำ prefix-sum ของ count
    เพื่อ query 'ผลรวม count ของคำที่ขึ้นต้นด้วย w' แบบ O(log n) ต่อคำ."""

    def __init__(self, words_counts):
        pairs = sorted(words_counts.items())
        self.words = [w for w, _ in pairs]
        counts = [c for _, c in pairs]
        self.cum = [0] + list(itertools.accumulate(counts))

    def range_sum(self, prefix):
        lo = bisect.bisect_left(self.words, prefix)
        hi = bisect.bisect_left(self.words, prefix + '\U0010FFFF')
        return self.cum[hi] - self.cum[lo], lo, hi

    def examples(self, prefix, exclude, limit=5):
        _, lo, hi = self.range_sum(prefix)
        out = []
        for w in self.words[lo:hi]:
            if w == exclude:
                continue
            out.append(w)
            if len(out) >= limit:
                break
        return out


def main():
    print('กำลังโหลด LST20 gold corpus...', file=sys.stderr)
    gold, total_tokens, nfiles = load_gold_tokens()
    print(f'  {nfiles} ไฟล์ · {total_tokens:,} โทเคน · {len(gold):,} คำต่าง', file=sys.stderr)

    print('กำลังโหลดพจนานุกรม ThaiBreak...', file=sys.stderr)
    dic = load_dict()
    print(f'  {len(dic):,} คำ', file=sys.stderr)

    print('กำลังสร้างดัชนี prefix/suffix...', file=sys.stderr)
    fwd_idx = PrefixIndex(gold)
    rev_gold = {w[::-1]: c for w, c in gold.items()}
    rev_idx = PrefixIndex(rev_gold)
    dict_fwd_idx = PrefixIndex({w: 1 for w in dic})

    cands = [w for w in dic if 1 <= len(w) <= 4 and THAI_RE.match(w)]
    print(f'พิจารณาคำสั้น (1-4 ตัวอักษรไทยล้วน) {len(cands):,} คำ', file=sys.stderr)

    rows = []
    for w in cands:
        standalone = gold.get(w, 0)
        prefix_freq, *_ = fwd_idx.range_sum(w)
        prefix_freq -= standalone  # ไม่นับตัวมันเอง
        suffix_freq, *_ = rev_idx.range_sum(w[::-1])
        suffix_freq -= standalone
        dict_siblings, *_ = dict_fwd_idx.range_sum(w)
        dict_siblings -= 1
        rows.append({
            'word': w, 'weight': dic[w],
            'standalone': standalone, 'prefix_freq': prefix_freq, 'suffix_freq': suffix_freq,
            'dict_siblings': dict_siblings,
            'prefix_examples': fwd_idx.examples(w, w),
            'suffix_examples': [e[::-1] for e in rev_idx.examples(w[::-1], w[::-1])],
        })

    bound = [r for r in rows if r['standalone'] <= 4 and (r['prefix_freq'] + r['suffix_freq']) >= 15]
    bound.sort(key=lambda r: -(r['prefix_freq'] + r['suffix_freq']))

    print(f'\n=== พบคำต้องสงสัยว่าเป็น bound morpheme (prefix/suffix เท่านั้น): {len(bound)} คำ '
          f'(จาก {len(cands)} คำสั้นที่พิจารณา) ===\n')
    print(f'{"คำ":<8}{"น้ำหนัก":>8}{"ยืนเดี่ยว":>10}{"เป็น prefix":>13}{"เป็น suffix":>13}'
          f'{"คำใน dict ที่ขึ้นต้นด้วยคำนี้":>28}  ตัวอย่าง')
    for r in bound[:100]:
        kind = 'prefix' if r['prefix_freq'] >= r['suffix_freq'] else 'suffix'
        examples = r['prefix_examples'] if kind == 'prefix' else r['suffix_examples']
        print(f"{r['word']:<8}{r['weight']:>8.2f}{r['standalone']:>10}{r['prefix_freq']:>13}"
              f"{r['suffix_freq']:>13}{r['dict_siblings']:>28}  {', '.join(examples)}")

    with open(OUT_PATH, 'w', encoding='utf-8') as fh:
        json.dump(bound, fh, ensure_ascii=False, indent=1)
    print(f'\nบันทึกผลทั้งหมด ({len(bound)} คำ) ที่ {OUT_PATH}')


if __name__ == '__main__':
    main()
