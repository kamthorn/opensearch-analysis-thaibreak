#!/usr/bin/env python3
"""Build a plugin ZIP whose dictionary is only the base dictionary of thai-break.

The bundled dictionary is the base dictionary plus thai-break-dict-extra. This keeps the plugin's own
words and weights for the base words only, so the two ZIPs differ in the dictionary and nothing else.

  python3 make_base_variant.py analysis-thaibreak-3.9.0.0.zip ../../../thai-break/data/words.txt \\
      analysis-thaibreak-base-3.9.0.0.zip

Needs thai-break/tools/build_dawg.py (set THAIBREAK_DIR to the checkout, default ../../../thai-break).
"""
import io
import os
import subprocess
import sys
import tempfile
import zipfile

src, base_words, dst = sys.argv[1:4]
thaibreak = os.environ.get('THAIBREAK_DIR', os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../thai-break'))
base = {l.split('\t')[0].rstrip('\n') for l in open(base_words, encoding='utf-8') if l.strip() and not l.startswith('#')}
WORDS, DAWG = 'org/opensearch/analysis/thai/words.txt', 'org/opensearch/analysis/thai/words.dawg'


def rewrite_jar(jar_bytes):
    zin = zipfile.ZipFile(io.BytesIO(jar_bytes))
    lines = [l for l in zin.read(WORDS).decode('utf-8').split('\n') if l and (l.startswith('#') or l.split('\t')[0] in base)]
    with tempfile.TemporaryDirectory() as tmp:
        txt, dawg = os.path.join(tmp, 'words.txt'), os.path.join(tmp, 'words.dawg')
        open(txt, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
        subprocess.run([sys.executable, os.path.join(thaibreak, 'tools/build_dawg.py'), txt, dawg], check=True)
        new = {WORDS: open(txt, 'rb').read(), DAWG: open(dawg, 'rb').read()}
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            zout.writestr(item, new.get(item.filename, zin.read(item.filename)))
    print(f'kept {sum(1 for l in lines if not l.startswith("#"))} of the plugin words')
    return out.getvalue()


zin = zipfile.ZipFile(src)
with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        zout.writestr(item, rewrite_jar(data) if item.filename.endswith('.jar') else data)
