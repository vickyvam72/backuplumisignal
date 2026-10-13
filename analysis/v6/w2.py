exec(open('w1.py').read().split("atoms = [")[0])
import json
o = pd.read_pickle('w1.pkl')
RK = ['volratio', 'lowpos', 'value', 'rsi_low']
rows = []
rng = np.random.default_rng(1); RANKS['rand'] = pd.Series(rng.random(len(df)))
base_rows = []
for tier in TIERS:
    for e in ['tp2/sl10/7h', 'tp3/sl10/7h', 'tp2/sl8/5h']:
        bt, _ = run(tier_mask(tier), 'rand', e); s = summarize(bt); base_rows.append((tier, e, s['n'], s['win'], s['total']))
    q = o[(o.tier == tier) & (o.avg > 0.25)].copy()
    q['key'] = q.cond + '|' + q.reg
    best_keys = q.sort_values('win', ascending=False).groupby('key').head(1)
    # 22 best cond|reg, at least 12 non-weak
    nonweak = best_keys[best_keys.reg != 'weak20'].head(14); weak = best_keys[best_keys.reg == 'weak20'].head(8)
    for _, r0 in pd.concat([nonweak, weak]).iterrows():
        exits = q[(q.key == r0.key)].sort_values('win', ascending=False).exit.head(3).tolist()
        m = mask_of(r0.cond, tier).to_numpy() & REGS[r0.reg]
        for ex in exits:
            for rk in RK:
                tr, _ = run(m, rk, ex)
                if len(tr) < 25: continue
                s = summarize(tr)
                tpr = (tr.pnl >= tr.tp_pct - 1e-6).mean() * 100
                rows.append(dict(tier=tier, cond=r0.cond, reg=r0.reg, exit=ex, rank=rk, n=s['n'], win=s['win'], tp_rate=round(tpr, 1), total=s['total'],
                                 avg=s['avg'], dd=s['maxdd'], worst=s['worst_month'], qpos=sum(x > 0 for x in s['q']), qneg=sum(x < 0 for x in s['q'])))
w = pd.DataFrame(rows); w.to_pickle('w2.pkl')
print(pd.DataFrame(base_rows, columns=['tier', 'exit', 'n', 'win', 'total']).to_string())
pd.set_option('display.width', 250)
for t in TIERS:
    x = w[(w.tier == t) & (w.total > 0) & (w.qneg <= 1)].sort_values(['win', 'total'], ascending=False).groupby(['cond', 'reg']).head(1)
    print(t); print(x.head(14).to_string())
