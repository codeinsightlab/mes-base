#!/usr/bin/env python3
"""Export the exact, ordered application migrations; no database access or secrets."""
import argparse
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, default=root / 'target/phase1/sql')
args = parser.parse_args()
files = [root / 'mes-admin/src/main/resources/db/platform/V1__platform.sql']
files += sorted((root / 'mes-admin/src/main/resources/db/system').glob('V*__*.sql'),
                key=lambda path: int(path.name.split('__')[0][1:]))
versions = [int(path.name.split('__')[0][1:]) for path in files]
if versions != list(range(1, len(files) + 1)):
    raise SystemExit('Migration versions must be contiguous and ordered.')
args.output.mkdir(parents=True, exist_ok=True)
header = ('-- SOURCE_PRODUCT_PORT: exact V1..V%d application migration export.\n'
          '-- Use ONLY with an EMPTY, isolated MySQL schema. V2 archives legacy tables.\n'
          '-- Generic product dictionaries/menus are included; no company data or accounts.\n'
          '-- This export does not create Flyway history. Prefer application-managed Flyway.\n'
          '-- After manual import, keep mes.platform.migrate=false; do not reapply migrations.\n\n') % len(files)
content = header + '\n\n'.join('-- ' + str(path.relative_to(root)) + '\n' + path.read_text()
                               for path in files)
artifact = args.output / 'mysql-phase1.sql'
artifact.write_text(content)
manifest = {'format': 'ordered application migrations', 'versions': versions,
            'artifact': artifact.name, 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
            'sources': [{'path': str(path.relative_to(root)),
                         'sha256': hashlib.sha256(path.read_bytes()).hexdigest()} for path in files]}
(args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps({'artifact': str(artifact), 'versions': versions}))
