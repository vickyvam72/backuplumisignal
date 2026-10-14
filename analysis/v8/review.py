import json, sys, warnings; warnings.filterwarnings('ignore')
exec(open('x1.py').read().split("atoms = [")[0])
exec(open('p2.py').read().split("choice = {}")[0])
ALLREG.update(REGS)
TE = set(dates[-60:])
def health(specs, tier):
    rows = []; sets = {}
    for s in specs:
        m = mask_of(s['cond'], tier).to_numpy() & ALLREG[s['reg']]
        tr, _ = run(m, s['rank'], s['exit'])
        st = summarize(tr) if len(tr) else dict(n=0)
        if not len(tr): rows.append(dict(cond=s['cond'], n=0)); continue
        a = np.sort(tr.pnl.to_numpy())
        top_t = tr.ticker.value_counts()
        be = None
        if 'tp' in s['exit']:
            tpv, slv = tr.tp_pct.mean(), tr.sl_pct.mean(); be = round(slv / (tpv + slv) * 100, 1)
        rows.append(dict(cond=s['cond'], reg=s['reg'], exit=s['exit'], n=st['n'], win=st['win'], impas=be, total=st['total'], avg=st['avg'],
                         qpos=sum(x > 0 for x in st['q']), tanpa_5_terbaik=round(a[:-5].sum(), 1), saham_teratas=f"{top_t.index[0]} {top_t.iloc[0] / len(tr) * 100:.0f}%",
                         pl_saham_teratas=round(tr[tr.ticker == top_t.index[0]].pnl.sum(), 1), uji60=round(tr[tr.date.isin(TE)].pnl.sum(), 1), worst=st['worst_month']))
        sets[s['cond']] = set(zip(tr.date, tr.ticker))
    for r in rows:
        if r['n'] == 0: continue
        mine = sets[r['cond']]
        r['overlap_max'] = max([round(len(mine & v) / len(mine) * 100) for k, v in sets.items() if k != r['cond']] or [0])
        flags = []
        if r['n'] < 40: flags.append('sampel kecil')
        if r['qpos'] < 3: flags.append('kuartal positif < 3')
        if r['tanpa_5_terbaik'] <= 0: flags.append('bergantung 5 transaksi terbaik')
        if r['pl_saham_teratas'] > 0.4 * r['total'] and r['total'] > 0: flags.append('bergantung 1 saham')
        if r['overlap_max'] > 40: flags.append('tumpang tindih')
        if r['total'] <= 0: flags.append('rugi')
        if r['impas'] is not None and r['win'] - r['impas'] < 4: flags.append('winrate dekat impas')
        if r['uji60'] < 0: flags.append('60 hari terakhir rugi')
        r['catatan'] = ', '.join(flags)
    return rows
if __name__ == '__main__':
    out = []
    for jenis, f in [('profit', 'p2_choice.json'), ('winrate', 'x4_choice.json')]:
        ch = json.load(open(f))
        for tier in TIERS:
            for r in health(ch[tier], tier): out.append(dict(jenis=jenis, tier=tier, **r))
    o = pd.DataFrame(out); o.to_pickle('review_before.pkl')
    pd.set_option('display.width', 300); pd.set_option('display.max_colwidth', 60)
    print(o[['jenis', 'tier', 'cond', 'n', 'win', 'impas', 'total', 'qpos', 'tanpa_5_terbaik', 'saham_teratas', 'pl_saham_teratas', 'overlap_max', 'uji60', 'catatan']].to_string())
