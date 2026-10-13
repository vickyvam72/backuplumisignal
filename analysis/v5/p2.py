from engine import *
import itertools, json, warnings; warnings.filterwarnings('ignore')
exec(open('p1.py').read().split("CANDS = [")[0].split("import json, warnings; warnings.filterwarnings('ignore')")[1])
ALLREG = {**REGS, **WEAKS}
o = pd.read_pickle('p1.pkl')
EXD = {}
def outc(ex):
    if ex not in EXD: r = outcomes(ex); EXD[ex] = (r, exit_di(r[1]))
    return EXD[ex]
def combined(specs, tier, return_trades=False):
    cand = []
    for k, s in enumerate(specs):
        m = mask_of(s['cond'], tier).to_numpy() & ALLREG[s['reg']]
        rk = np.asarray(RANKS[s['rank']], dtype=float); idx = np.where(m & np.isfinite(rk))[0]
        tmp = pd.DataFrame({'i': idx, 'd': di[idx], 'sid': k, 'r': rk[idx]}); tmp['pct'] = tmp.groupby('d')['r'].rank(pct=True); cand.append(tmp)
    cand = pd.concat(cand).sort_values('d')
    hist = {k: [] for k in range(len(specs))}; held = {}; picked = []
    for d, day in cand.groupby('d', sort=True):
        q = {}
        for k, h in hist.items():
            done = [p for (xd, p) in h if xd < d]; q[k] = np.mean(done) if len(done) >= 10 else 0.0
        day = day.assign(q=day.sid.map(q)).sort_values(['q', 'pct'], ascending=[False, False])
        taken = 0; seen = set()
        for i, sid in zip(day.i.to_numpy(), day.sid.to_numpy()):
            if taken >= 5: break
            t = tick_all[i]
            if t in seen or held.get(t, -1) > d: continue
            seen.add(t); taken += 1
            (res, exk, e, tp, sl), xdi = outc(specs[sid]['exit'])
            if np.isfinite(res[i]):
                held[t] = xdi[i]; hist[sid].append((xdi[i], res[i] * 100)); picked.append((i, sid, dates[d], res[i] * 100))
    P = pd.DataFrame(picked, columns=['i', 'sid', 'date', 'pnl'])
    return (summarize(P), P) if return_trades else summarize(P)
choice = {}
for tier in TIERS:
    ot = o[(o.tier == tier) & (o.qpos >= 3)].sort_values('score', ascending=False)
    top = ot.head(8)
    if not (top.kind == 'w').any() and (ot.kind == 'w').any():
        top = pd.concat([top.head(7), ot[ot.kind == 'w'].head(1)])
    specs_all = top.to_dict('records')
    best = None
    for combo in itertools.combinations(range(len(specs_all)), 5):
        specs = [specs_all[k] for k in combo]
        s = combined(specs, tier)
        sc = s['total'] / max(40, -s['maxdd']) - 0.5 * max(0, -s['worst_month'] - 50) / 50 + 0.25 * sum(x > 0 for x in s['q'])
        if best is None or sc > best[0]: best = (sc, combo, s)
    sc, combo, s = best
    choice[tier] = [specs_all[k] for k in combo]
    print(f"[{tier}] score {sc:.2f} top5: n{s['n']} win{s['win']} tot{s['total']:+.1f} DD{s['maxdd']} worstM{s['worst_month']} q{[round(x) for x in s['q']]}")
    for sp in choice[tier]: print('    ', sp['cond'], sp['reg'], sp['exit'], sp['rank'], 'n', sp['n'], 'tot', sp['total'], 'dd', sp['dd'])
json.dump(choice, open('p2_choice.json', 'w'), indent=1, default=str)
