#!/usr/bin/env python3
"""IDEA formatting for staged/pushed Java; preserve partially staged work."""
import subprocess
import sys

def git(*arguments):
    return subprocess.check_output(['git', *arguments]).decode().split('\0')

if sys.argv[1] == 'commit':
    files = [p for p in git('diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z')
             if p.endswith('.java')]
    dirty = set(git('diff', '--name-only', '-z'))
    if dirty.intersection(files):
        raise SystemExit('Refusing to re-stage partially staged Java; format and stage it explicitly.')
    if files:
        subprocess.run(['./scripts/format-java', *files], check=True)
        subprocess.run(['git', 'add', '--', *files], check=True)
else:
    files = set()
    zero = '0' * 40
    for line in sys.stdin:
        _, local, _, remote = line.split()
        if local == zero:
            continue
        subprocess.run([sys.executable, 'scripts/check-private-config.py', '--revision', local], check=True)
        arguments = ('diff-tree', '--no-commit-id', '--name-only', '-r', local, '-z') if remote == zero else (
            'diff', '--name-only', '--diff-filter=ACMR', remote, local, '-z')
        files.update(p for p in git(*arguments) if p.endswith('.java'))
    from pathlib import Path
    files = sorted(p for p in files if Path(p).is_file())
    if files:
        subprocess.run(['./scripts/format-java', '--check', *files], check=True)
