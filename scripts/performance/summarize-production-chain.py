#!/usr/bin/env python3
"""Summarize real HTTP samples; never counts cached/replayed journal steps as new writes."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
import sqlite3


def percentile(values, percent):
    values = sorted(values)
    if not values:
        return None
    index = (len(values) - 1) * percent / 100
    low, high = int(index), min(int(index) + 1, len(values) - 1)
    return round(values[low] + (values[high] - values[low]) * (index - low), 3)


def category(step):
    for prefix, name in [('report', 'normal-report'), ('assignment', 'assignment'),
                         ('stock', 'zero-stock-create'), ('opening', 'opening-receipt'),
                         ('handover', 'production-handover'), ('accept', 'warehouse-acceptance')]:
        if step.startswith(prefix):
            return name
    return step


def summarize(directory, start=1, end=1000000000, since=0, until=1e20):
    c = sqlite3.connect(directory / 'journal.sqlite')
    c.row_factory = sqlite3.Row
    groups = defaultdict(list)
    rows = list(c.execute('SELECT * FROM timings WHERE job>=? AND job<=? AND started>=? AND started<? ORDER BY id', (start, end, since, until)))
    for row in rows:
        groups[category(row['step'])].append(row)
    output = {}
    for name, data in sorted(groups.items()):
        values = [r['ms'] for r in data if r['status'] == 200]
        output[name] = {'requests': len(data), 'success': len(values),
                        'errors': sum(r['status'] != 200 for r in data),
                        'p50Ms': percentile(values, 50), 'p95Ms': percentile(values, 95),
                        'p99Ms': percentile(values, 99), 'maxMs': max(values) if values else None}
    elapsed = max((r['started'] + r['ms'] / 1000 for r in rows), default=0) - min((r['started'] for r in rows), default=0)
    # Overall span may include deliberate gaps between phases; retain that boundary explicitly.
    output['_overall'] = {'sampleSince': since, 'sampleUntil': until, 'httpRequests': len(rows), 'successfulMutations': sum(r['status'] == 200 and r['method'] in ('POST', 'PUT', 'DELETE') for r in rows),
                          'observedWallSpanSecondsIncludingPhaseGaps': round(elapsed, 3),
                          'completedCycles': len({r['job'] for r in rows if r['step']=='complete' and r['status']==200}),
                          'currentJournalPendingStepsInJobRange': c.execute("SELECT COUNT(*) FROM steps WHERE state<>'SUCCEEDED' AND job>=? AND job<=?", (start, end)).fetchone()[0]}
    (directory / 'http-summary.json').write_text(json.dumps(output, indent=2))
    return output


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('directory', type=Path)
    p.add_argument('--start',type=int,default=1)
    p.add_argument('--end',type=int,default=1000000000)
    p.add_argument('--since',type=float,default=0)
    p.add_argument('--until',type=float,default=1e20)
    a = p.parse_args()
    print(json.dumps(summarize(a.directory, a.start, a.end, a.since, a.until), indent=2))
