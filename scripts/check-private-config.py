#!/usr/bin/env python3
"""Check selected configuration and obvious source secrets without printing values."""
import argparse
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
scope = parser.add_mutually_exclusive_group()
scope.add_argument('--working-tree', action='store_true', help='Inspect tracked and untracked working files')
scope.add_argument('--staged', action='store_true')
scope.add_argument('--revision', help='Inspect a committed tree instead of the index')
args = parser.parse_args()

def git(*arguments):
    return subprocess.check_output(['git', *arguments])

if args.revision:
    paths = git('ls-tree', '-r', '--name-only', '-z', args.revision)
elif args.staged:
    paths = git('diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z')
elif args.working_tree:
    paths = git('ls-files', '-co', '--exclude-standard', '-z')
else:
    paths = git('ls-files', '-z')

errors = []
for raw in paths.split(b'\0'):
    if not raw:
        continue
    path = raw.decode()
    if args.working_tree and not Path(path).is_file():
        continue
    if path in ('mes-admin/src/main/resources/application-dev.yml',
                'mes-admin/src/main/resources/application-prod.yml'):
        errors.append(path + ': private profile must not be tracked')
        continue
    if path.endswith('.java') and '/src/main/java/' in path:
        source = (Path(path).read_text() if args.working_tree else
                  git('show', (args.revision or '') + ':' + path).decode())
        patterns = [
            r'new\s+DatasourcePasswordCipher\s*\(\s*"[^"\n]+"',
            r'(?i)(?:String|var)\s+\w*(?:password|secret|datasourceKey|privateKey)\w*\s*=\s*"[^"\n]{8,}"',
        ]
        lines = {source.count('\n', 0, match.start()) + 1
                 for pattern in patterns for match in re.finditer(pattern, source)}
        for number in sorted(lines):
            errors.append(path + ':' + str(number) + ': possible hardcoded secret (value redacted)')
        continue
    if not (path.startswith('config/') or '/src/main/resources/' in path):
        continue
    if not path.endswith(('.yml', '.yaml', '.properties')):
        continue
    text = (Path(path).read_text() if args.working_tree else
            git('show', (args.revision or '') + ':' + path).decode())
    for number, line in enumerate(text.splitlines(), 1):
        match = re.match(r'\s*(?:[\w-]+\.)*(password|datasource-key|secret-key|access-key)\s*[:=]\s*(.*?)\s*$', line)
        if not match:
            continue
        value = match[2].split(' #', 1)[0].strip().strip('\"\'')
        placeholder = re.fullmatch(r'\$\{[A-Za-z_][A-Za-z0-9_]*:?\}', value)
        if value and not value.startswith('#') and not placeholder:
            errors.append(path + ':' + str(number) + ': nonempty credential literal')
if errors:
    raise SystemExit('\n'.join(errors))
print('Private configuration check passed (selected tree only; history not checked).')
