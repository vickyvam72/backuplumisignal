"""Healthy re-selection of 5 strategies per tier for both signal types (tick-rounded TP/SL)."""
import json, sys, itertools, warnings; warnings.filterwarnings('ignore')
exec(open('x1.py').read().split("atoms = [")[0])
exec(open('p2.py').read().split("choice = {}")[0])
ALLREG.update(REGS)
TE = set(dates[-60:])
JENIS = sys.argv[1]
def trades_of(s, tier):
    m = mask_of(s['cond'], tier).to_numpy() & ALLREG[s['reg']]
    tr, _ = run(m, s['rank'], s['exit']); return tr
def health(s, tier):
    tr = trades_of(s, tier)
    if len(tr) < 40: return None
    st = summarize(tr); a = np.sort(tr.pnl.to_numpy())
    qa = [tr[tr.date.isin(q)].pnl for q in Q]
    q_neg = sum(1 for x in qa if len(x) >= 5 and x.sum() < 0)
    top = tr.ticker.value_counts().index[0]
    h = dict(n=st['n'], win=st['win'], total=st['total'], avg=st['avg'], dd=st['maxdd'], worst=st['worst_month'], q_neg=q_neg,
             ex5=a[:-5].sum(), top_share=tr[tr.ticker == top].pnl.sum() / max(st['total'], 1e-9), uji60=tr[tr.date.isin(TE)].pnl.sum(),
             impas=float((tr.sl_pct / (tr.tp_pct + tr.sl_pct)).mean() * 100), keys=set(zip(tr.date, tr.ticker)))
    ok = h['total'] > 0 and h['ex5'] > 0 and h['q_neg'] <= 1 and h['top_share'] < 0.4 and h['uji60'] >= 0
    if JENIS == 'winrate': ok &= h['win'] - h['impas'] >= 5
    return h if ok else None
if JENIS == 'profit':
    o = pd.read_pickle('p1t.pkl')
    cands = {t: o[o.tier == t].sort_values('score', ascending=False).to_dict('records') for t in TIERS}
else:
    w = pd.concat([pd.read_pickle('x2t.pkl'), pd.read_pickle('x2t_ext.pkl')]).drop_duplicates(['tier', 'cond', 'reg', 'exit', 'rank'])
    w = w[(w.total > 0) & (w.win >= 70)].assign(sc=lambda d: d.win + 2 * d.avg)
    cands = {t: w[w.tier == t].sort_values('sc', ascending=False).groupby('cond').head(4).to_dict('records') for t in TIERS}
import pandas as _pd
_w=_pd.read_pickle('w20.pkl'); _w=_w[_w.ok].sort_values('tot',ascending=False).groupby('cond').head(1)
cands['20M'] = cands['20M'] + [dict(tier='20M', cond=r.cond, kind='', reg=r.reg, exit=r.exit, rank=r['rank'], n=r.n, total=r.tot) for _, r in _w.iterrows()]
choice = json.load(open('final_profit.json')); report = {}
for tier in ['20M']:
    pool = []; seen = set()
    for c in cands[tier]:
        if c['cond'] in seen: continue
        h = health(c, tier)
        if h is None: continue
        c = dict(c); c['_h'] = h; pool.append(c); seen.add(c['cond'])
        if len(pool) >= 16: break
    normal = [p for p in pool if not p['reg'].startswith('weak')]; weak = [p for p in pool if p['reg'].startswith('weak')]
    pool = normal[:8] + weak[:3]
    print(tier, "sehat:", len(normal), "normal,", len(weak), "lemah", flush=True)
    best = None
    for combo in itertools.combinations(range(len(pool)), 5):
        specs = [pool[k] for k in combo]
        if sum(not s['reg'].startswith('weak') for s in specs) < 3: continue
        bad = False
        for x, y in itertools.combinations(specs, 2):
            inter = len(x['_h']['keys'] & y['_h']['keys'])
            if inter > 0.4 * min(len(x['_h']['keys']), len(y['_h']['keys'])): bad = True; break
        if bad: continue
        s, P = combined(specs, tier, return_trades=True)
        if s['total'] <= 0: continue
        if JENIS == 'profit':
            sc = s['total'] / max(40, -s['maxdd']) - 0.5 * max(0, -s['worst_month'] - 50) / 50 + 0.25 * sum(x > 0 for x in s['q'])
        else:
            sc = s['win'] + 2 * s['avg'] - 0.05 * max(0, -s['maxdd'] - 60) - 0.05 * max(0, -s['worst_month'] - 40)
        if best is None or sc > best[0]: best = (sc, combo, s)
    if best is None:
        print("   tidak ada kombinasi sehat untuk", tier); continue
    sc, combo, s = best
    choice[tier] = [{k: v for k, v in pool[k].items() if k != '_h'} for k in combo]
    print(f"[{JENIS}/{tier}] pool {len(pool)} | top5 n{s['n']} win{s['win']} tot{s['total']:+.1f} avg{s['avg']:+.2f} DD{s['maxdd']} worst{s['worst_month']} q{[round(v) for v in s['q']]}")
    for k in combo:
        p = pool[k]; h = p['_h']
        print(f"     {p['cond']:34} {p['reg']:6} {p['exit']:22} {p['rank']:8} n{h['n']} win{h['win']} impas{h['impas']:.1f} tot{h['total']:+.1f} ex5{h['ex5']:+.1f} 60h{h['uji60']:+.1f}")
json.dump(choice, open(f'final_{JENIS}.json', 'w'), indent=1, default=str)
