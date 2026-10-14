exec(open('x1t.py').read().split("atoms = [")[0])
import json
o = pd.read_pickle('x1t.pkl')
RK = ['volratio', 'lowpos', 'value', 'rsi_low']
rows = []
rng = np.random.default_rng(1); RANKS['rand'] = pd.Series(rng.random(len(df)))
base_rows = []
for tier in ['semua', '250jt']:
    for e in ['tp3/sl8/7h', 'tp4/sl10/7h', 'tp1.0atr/sl2.0atr/7h']:
        bt, _ = run(tier_mask(tier), 'rand', e); s = summarize(bt); base_rows.append((tier, e, s['n'], s['win'], s['total']))
    q = o[(o.tier == tier) & (o.avg > 0.5)].copy(); q['sc'] = q.win + 3 * q.avg
    q['key'] = q.cond + '|' + q.reg
    best_keys = q.sort_values('sc', ascending=False).groupby('key').head(1)
    # 22 best cond|reg, at least 12 non-weak
    nonweak = best_keys[best_keys.reg != 'weak20'].head(45); weak = best_keys[best_keys.reg == 'weak20'].head(10)
    for _, r0 in pd.concat([nonweak, weak]).iterrows():
        exits = q[(q.key == r0.key)].sort_values('sc', ascending=False).exit.head(4).tolist()
        m = mask_of(r0.cond, tier).to_numpy() & REGS[r0.reg]
        for ex in exits:
            for rk in RK:
                tr, _ = run(m, rk, ex)
                if len(tr) < 25: continue
                s = summarize(tr)
                tpr = (tr.pnl >= tr.tp_pct - 1e-6).mean() * 100
                rows.append(dict(tier=tier, cond=r0.cond, reg=r0.reg, exit=ex, rank=rk, n=s['n'], win=s['win'], tp_rate=round(tpr, 1), total=s['total'],
                                 avg=s['avg'], dd=s['maxdd'], worst=s['worst_month'], qpos=sum(x > 0 for x in s['q']), qneg=sum(x < 0 for x in s['q'])))
w = pd.DataFrame(rows); w.to_pickle('x2t_ext.pkl')
print(pd.DataFrame(base_rows, columns=['tier', 'exit', 'n', 'win', 'total']).to_string())
pd.set_option('display.width', 250)
for t in TIERS:
    x = w[(w.tier == t) & (w.total > 0) & (w.qneg <= 1)].assign(sc=lambda d: d.win + 3 * d.avg).sort_values('sc', ascending=False).groupby(['cond', 'reg']).head(1)
    print(t); print(x.head(14).to_string())
