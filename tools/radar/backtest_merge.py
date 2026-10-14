"""Adds finished live trades (TP or SL hit) of the published radar to the monthly backtest files.

The backtest on market-data (radar/backtest/<YYYY-MM>.json + index.json) starts as the calibrated
historical simulation. Every run after the close, the live ledgers (ledger_<tier>.json) are read and
each pick that has since touched its TP or SL is appended to the month of its signal date, in the
same row format. Rows already present (same signal date, tier, type, top-5/strategy, strategy id and
ticker) are never added twice, and open, cancelled or time-exit trades are left out.

Row: [jenis, tier, g, sid, ticker, signal, buy, sell, close, entry, tp, sl, pnl, hold, exit]
"""
import argparse
import glob
import json
import os
import shutil

MONTHS = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September',
          'Oktober', 'November', 'Desember']
SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des']


def short(iso):
    y, m, d = iso.split('-')
    return f'{int(d)} {SHORT[int(m) - 1]} {y}'


def key(r):
    return (r[5], r[1], r[0], int(r[2]), int(r[3]), r[4])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ledgers', required=True, help='folder with ledger_<tier>.json')
    ap.add_argument('--dir', required=True, help='backtest folder to update')
    ap.add_argument('--seed', help='backtest folder to copy from when --dir does not exist yet')
    a = ap.parse_args()

    if not os.path.exists(os.path.join(a.dir, 'index.json')):
        if not a.seed or not os.path.exists(os.path.join(a.seed, 'index.json')):
            print('no backtest to update')
            return
        shutil.copytree(a.seed, a.dir, dirs_exist_ok=True)

    index = json.load(open(os.path.join(a.dir, 'index.json')))
    months = {}
    for f in glob.glob(os.path.join(a.dir, '????-??.json')):
        m = json.load(open(f))
        months[m['month']] = m['rows']
    have = {key(r) for rows in months.values() for r in rows}

    added = 0
    for f in sorted(glob.glob(os.path.join(a.ledgers, 'ledger_*.json'))):
        tier = os.path.basename(f)[len('ledger_'):-len('.json')]
        for x in json.load(open(f)).get('rows', []):
            if x.get('status') not in ('TP', 'SL') or not x.get('exit_date'):
                continue
            r = [x['jenis'], tier, 1 if x['g'] else 0, int(x['sid']), x['ticker'], x['date'], x['entry_date'],
                 x['exit_date'], float(x['close']), float(x['entry']), float(x['tp']), float(x['sl']),
                 float(x['pnl']), int(x['hold']), x['status']]
            if key(r) in have:
                continue
            have.add(key(r))
            months.setdefault(x['date'][:7], []).append(r)
            added += 1

    if not added:
        print('backtest: nothing new')
        return

    info = {m['month']: m for m in index.get('months', [])}
    for month, rows in months.items():
        rows.sort(key=lambda r: (r[5], r[0], r[1], r[2], r[3], r[4]))
        json.dump(dict(month=month, rows=rows), open(os.path.join(a.dir, f'{month}.json'), 'w'),
                  ensure_ascii=False, separators=(',', ':'))
        y, mm = month.split('-')
        info[month] = dict(month=month, label=f'{MONTHS[int(mm) - 1]} {y}',
                           start=min(r[5] for r in rows), end=max(r[5] for r in rows), trades=len(rows))
    index['months'] = sorted(info.values(), key=lambda m: m['month'], reverse=True)
    last = max(r[7] for rows in months.values() for r in rows)
    start = index.get('period', '').split('–')[0].strip()
    index['period'] = f'{start} – {short(last)}' if start else short(last)
    index['updated'] = last
    json.dump(index, open(os.path.join(a.dir, 'index.json'), 'w'), ensure_ascii=False, separators=(',', ':'))
    print(f'backtest: +{added} trades, period {index["period"]}')


if __name__ == '__main__':
    main()
