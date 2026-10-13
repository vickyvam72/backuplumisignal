import engine as E
from engine import *
import itertools, time, warnings; warnings.filterwarnings('ignore')
b = df['mkt_breadth_ma5'].to_numpy(); r5 = df['mkt_ew_ret5'].to_numpy()
mkd = mk.copy(); mkd['br5'] = mkd['breadth'].rolling(5, min_periods=3).mean(); mkd['chg'] = mkd['br5'] - mkd['br5'].shift(10)
chg = D.map(mkd['chg']).to_numpy(); GUARD = ~(chg < -.15)
REGS = {'all': GUARD, 'ret': (r5 > -.03) & GUARD, 'br30': (b >= .30) & GUARD, 'weak20': b < .20}
atrp = E.atrp
WEX = {}
for tp in (.02, .03, .04):
    for sl in (.04, .06, .08, .10):
        for h in (3, 5, 7):
            WEX[f'tp{int(tp*100)}/sl{int(sl*100)}/{h}h'] = dict(tp=tp, sl=sl, hold=h)
WEX['tp.5atr/sl2atr/5h'] = dict(tp=np.clip(.5 * atrp, .02, .10), sl=np.clip(2 * atrp, .04, .12), hold=5)
WEX['tp.75atr/sl2.5atr/7h'] = dict(tp=np.clip(.75 * atrp, .02, .12), sl=np.clip(2.5 * atrp, .04, .12), hold=7)
WEX['tp.5atr/low10/7h'] = dict(tp=np.clip(.5 * atrp, .02, .10), sl=.08, hold=7, sl_px=E.low10, sl_cap=(.04, .12))
E.EXITS.update(WEX)
atoms = ['uptrend','downtrend','red','down3','green','up3','upstreak3','red2','rsi<35','rsi<30','rsi50-70','rsi40-60','volhi','vollo','dry5',
 'tight20','squeeze','cpos_hi','cpos_lo','lwick','udvr2','pos60_lo','big10','inside','near_ma20','gapdown_today',
 'reversal','engulf','pullback_up','hl_rise','nr7','gain60_lo','above_ma50','p<200','p>=200']
AM = {a: A[a].to_numpy() for a in atoms}
combos = [(a,) for a in atoms] + list(itertools.combinations(atoms, 2))
OUT = {e: outcomes(e)[0] for e in WEX}
t0 = time.time(); rows = []
for tier in TIERS:
    tm = tier_mask(tier)
    for cb in combos:
        m0 = tm.copy()
        for a in cb: m0 &= AM[a]
        if m0.sum() < 100: continue
        for rg, rm in REGS.items():
            m = m0 & rm
            if m.sum() < 80: continue
            for e, r in OUT.items():
                x = r[m]; x = x[np.isfinite(x)]
                if len(x) < 60: continue
                rows.append((tier, ' & '.join(cb), rg, e, len(x), (x > 0).mean() * 100, x.mean() * 100))
print('rows', len(rows), time.time() - t0)
o = pd.DataFrame(rows, columns=['tier', 'cond', 'reg', 'exit', 'n', 'win', 'avg']); o.to_pickle('w1.pkl')
for t in TIERS:
    q = o[(o.tier == t) & (o.avg > 0.3)].sort_values('win', ascending=False).groupby('cond').head(1)
    print(t); print(q.head(15).to_string())
