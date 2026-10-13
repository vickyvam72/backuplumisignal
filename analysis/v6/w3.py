exec(open('w1.py').read().split("atoms = [")[0])
import itertools, json
ALLREG = dict(REGS)
EXD = {}
def outc(ex):
    if ex not in EXD: r = outcomes(ex); EXD[ex] = (r, exit_di(r[1]))
    return EXD[ex]
src = open('p2.py').read()
exec(src[src.index("def combined("):src.index("choice = {}")])
w = pd.read_pickle('w2.pkl')
choice = {}
for tier in TIERS:
    x = w[(w.tier == tier) & (w.total > 0) & (w.qneg <= 1) & (w.n >= 30)].copy()
    x['score'] = x.win + 0.02 * x.total
    x = x.sort_values('score', ascending=False).groupby('cond').head(1)   # one variant per setup
    normal = x[x.reg != 'weak20'].head(7); weak = x[x.reg == 'weak20'].head(3)
    pool = pd.concat([normal, weak]).to_dict('records')
    best = None
    for combo in itertools.combinations(range(len(pool)), 5):
        specs = [pool[k] for k in combo]
        if sum(s['reg'] != 'weak20' for s in specs) < 3: continue
        s, P = combined(specs, tier, return_trades=True)
        if s['total'] <= 0: continue
        sc = s['win'] + 0.01 * s['total'] - 0.05 * max(0, -s['maxdd'] - 60)
        if best is None or sc > best[0]: best = (sc, combo, s)
    sc, combo, s = best
    choice[tier] = [pool[k] for k in combo]
    print(f"[{tier}] top5 n{s['n']} win{s['win']} tot{s['total']:+.1f} avg{s['avg']:+.2f} DD{s['maxdd']} worst{s['worst_month']} q{[round(v) for v in s['q']]}")
    for sp in choice[tier]: print('    ', sp['cond'], sp['reg'], sp['exit'], sp['rank'], 'n', sp['n'], 'win', sp['win'], 'tot', sp['total'])
json.dump(choice, open('w3_choice.json', 'w'), indent=1, default=str)
