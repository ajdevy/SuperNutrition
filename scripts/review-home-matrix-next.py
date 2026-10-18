#!/usr/bin/env python3
"""Capture exactly one Home matrix case, requiring visual acknowledgement before advancing."""
import argparse
import datetime
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / '_bmad-output/design-review/home-no-scroll-2026-10-17'
PARSER = argparse.ArgumentParser(description=__doc__)
PARSER.add_argument('--review-previous', help='Actual visual observation after viewing the prior original PNG')
PARSER.add_argument('--finish', action='store_true', help='Acknowledge the final image without another UI action')
ARGS = PARSER.parse_args()
PLAN = json.loads((OUTPUT / 'matrix-plan.json').read_text())['cases']
CURRENT = OUTPUT / 'current-capture.json'
JOURNAL = OUTPUT / 'visual-inspection.jsonl'
inspections = [json.loads(line) for line in JOURNAL.read_text().splitlines()] if JOURNAL.exists() else []
seen = {entry['png'] for entry in inspections}

if CURRENT.exists():
    previous = json.loads(CURRENT.read_text())
    png = Path(previous['png'])
    if str(png.relative_to(OUTPUT)) not in seen:
        assert png.exists(), 'Previous capture failed; diagnose and retain its evidence before advancing'
        assert ARGS.review_previous, 'View the prior PNG and provide a visual observation before another UI action'
        data = png.read_bytes()
        assert data[:8] == b'\x89PNG\r\n\x1a\n'
        width, height = struct.unpack('>II', data[16:24])
        record = {
            'inspected_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'png': str(png.relative_to(OUTPUT)), 'sha256': hashlib.sha256(data).hexdigest(),
            'actual_png_width': width, 'actual_png_height': height,
            'geometry': previous['geometry'], 'font_scale': previous['font_scale'],
            'visual_observation': ARGS.review_previous,
        }
        with JOURNAL.open('a') as file:
            file.write(json.dumps(record, ensure_ascii=False) + '\n')
        seen.add(record['png'])

if ARGS.finish:
    print(json.dumps({'inspected_cases': len(seen), 'planned_cases': len(PLAN)}))
    sys.exit(0)

for index, case in enumerate(PLAN):
    leaf = OUTPUT / 'after' / case['geometry'] / ('font-' + str(round(case['font_scale'] * 100)))
    png = leaf / 'home.png'
    if png.exists():
        assert str(png.relative_to(OUTPUT)) in seen, 'An existing native image has not been visually inspected'
        continue
    assert not leaf.exists(), 'A failed partial capture must be diagnosed; never overwrite prior evidence'
    current = {'index': index + 1, 'planned_cases': len(PLAN), 'geometry': case['geometry'],
               'font_scale': case['font_scale'], 'png': str(png)}
    CURRENT.write_text(json.dumps(current, indent=2) + '\n')
    print(json.dumps(current), flush=True)
    result = subprocess.run([sys.executable, str(ROOT / 'scripts/review-home-viewport.py'), 'capture',
                             '--phase', 'after', '--geometry', case['geometry'],
                             '--font', str(case['font_scale'])])
    if result.returncode:
        sys.exit(result.returncode)
    # No more guest interaction; the caller must immediately view this PNG.
    print('CAPTURE_PATH:' + str(png), flush=True)
    sys.exit(0)

print(json.dumps({'complete': True, 'inspected_cases': len(seen), 'planned_cases': len(PLAN)}))
