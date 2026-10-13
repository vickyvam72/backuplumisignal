import json, warnings; warnings.filterwarnings('ignore')
exec(open('p2.py').read().split("choice = {}")[0])
choice = json.load(open('p2_choice.json'))
base4 = [s for s in choice['1M'] if not s['reg'].startswith('weak')]
alts = []
for c in ['lwick & gapdown_today','cpos_lo & gapdown_today','down3 & red2 & rsi<35','rsi<35 & volhi']:
    for rg in ['weak20','weak30']:
        for ex in ['atr3/1.5','low5/res','low5/3atr','atr2.5/2']:
            for rk in ['volratio','value','rsi_low']:
                alts.append(dict(cond=c, reg=rg, exit=ex, rank=rk))
res=[]
for a in alts:
    s = combined(base4 + [a], '1M', return_trades=True)
    st, P = s
    mon = P.groupby(P.date.str[:7])['pnl'].sum()
    res.append((a['cond'], a['reg'], a['exit'], a['rank'], st['total'], st['maxdd'], st['worst_month'], round(mon.get('2026-05', 0), 1), sum(x > 0 for x in st['q'])))
o = pd.DataFrame(res, columns=['cond','reg','exit','rank','total','dd','worst','may','qpos'])
o['score'] = o.total / (-o.dd).clip(lower=40) - .5 * (-o.worst - 50).clip(lower=0) / 50 + .25 * o.qpos
print(o.sort_values('score', ascending=False).head(12).to_string())
