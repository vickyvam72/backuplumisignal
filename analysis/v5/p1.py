from engine import *
import json, warnings; warnings.filterwarnings('ignore')
b = df['mkt_breadth_ma5'].to_numpy(); r5 = df['mkt_ew_ret5'].to_numpy()
mkd = mk.copy(); mkd['br5'] = mkd['breadth'].rolling(5, min_periods=3).mean(); mkd['chg'] = mkd['br5'] - mkd['br5'].shift(10)
chg = D.map(mkd['chg']).to_numpy()
GUARD = ~(chg < -.15)
REGS = {'all': GUARD.copy(), 'ret': (r5 > -.03) & GUARD, 'br30': (b >= .30) & GUARD, 'br50': (b >= .50) & GUARD}
WEAKS = {'weak30': b < .30, 'weak20': b < .20}
CANDS = [
 ('rsi<35 & udvr2 & p<200','n'), ('up3 & rsi<35 & p<200','n'), ('upstreak3 & pos60_lo & inside','n'), ('squeeze & upstreak3 & pos60_lo','n'),
 ('squeeze & near_ma20 & p<200','n'), ('lwick & gapdown_today','w'), ('up3 & pos60_lo & reversal','n'), ('downtrend & rsi<35 & engulf','n'),
 ('squeeze & pos60_lo & hl_rise','n'), ('lwick & pos60_lo & nr7','n'),
 ('up3 & squeeze & pos60_lo','n'), ('squeeze & pos60_lo & gain60_lo','n'), ('dry5 & near_ma20 & gain60_lo','n'), ('vollo & lwick & hl_rise','n'),
 ('down3 & rsi<35 & big10','n'), ('rsi<35 & engulf','n'), ('dry5 & pos60_lo & hl_rise','n'), ('dry5 & pos60_lo & big10','n'),
 ('up3 & lwick & gain60_lo','n'), ('downtrend & squeeze & big10','n'), ('dry5 & lwick & nr7','n'), ('rsi<35 & volhi','w'),
 ('down3 & red2 & rsi<35','w'), ('cpos_lo & gapdown_today','w'),
]
EXN = ['atr2/1.5','atr3/1.5','atr2.5/2','low5/res','low10/res','low5/3atr']
RKS = ['volratio','lowpos','value','rsi_low']
rows=[]
for tier in TIERS:
    tm = {c: mask_of(c, tier).to_numpy() for c,_ in CANDS}
    for c, kind in CANDS:
        regs = WEAKS if kind == 'w' else REGS
        best=None
        for rg, rm in regs.items():
            m = tm[c] & rm
            if m.sum() < 20: continue
            for ex in EXN:
                for rk in RKS:
                    tr,_ = run(m, rk, ex)
                    if len(tr) < 25: continue
                    trp,_ = run(m, rk, ex, path='pess')
                    s = summarize(tr); p = summarize(trp)
                    qpos = sum(x > 0 for x in p['q'] if x != 0)
                    mon = tr.groupby(tr.date.str[:7])['pnl'].sum()
                    may = mon.get('2026-05', 0.0)
                    score = p['total'] / max(40, -s['maxdd']) * (0.5 + 0.5 * qpos / 4) - 0.3 * max(0, -s['worst_month'] - 60) / 40
                    if best is None or score > best[0]:
                        best = (score, rg, ex, rk, s, p, qpos, may)
        if best:
            score, rg, ex, rk, s, p, qpos, may = best
            rows.append(dict(tier=tier, cond=c, kind=kind, reg=rg, exit=ex, rank=rk, n=s['n'], win=s['win'], total=s['total'], pess=p['total'],
                             dd=s['maxdd'], worst=s['worst_month'], qpos=qpos, may=round(may,1), score=round(score,2)))
o = pd.DataFrame(rows); o.to_pickle('p1.pkl')
pd.set_option('display.width', 250); pd.set_option('display.max_rows', 200)
for t in TIERS:
    print(o[o.tier==t].sort_values('score', ascending=False).head(10).to_string())
