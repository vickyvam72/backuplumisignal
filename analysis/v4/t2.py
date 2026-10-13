from engine import *
import itertools, time
b = df['mkt_breadth_ma5'].to_numpy(); r5 = df['mkt_ew_ret5'].to_numpy()
REG = {'none': np.ones(len(df),bool), 'ret': r5 > -.03, 'br40': b >= .40, 'weak': b < .30}
atoms = ['uptrend','downtrend','red','down3','green','up3','upstreak3','red2','rsi<35','rsi<30','rsi50-70','rsi40-60','volhi','volhi3','vollo','dry5',
 'brk20','tight20','tight10','squeeze','cpos_hi','cpos_lo','lwick','udvr2','ad20+','pos60_lo','big10','inside','near_ma20','gapdown_today',
 'cross5_20','reclaim_ma50','new_hh60','nr7','hl_rise','reversal','engulf','pullback_up','vol_up_close_hi','above_ma50','gain60_lo','gain60_hi']
AM = {a: A[a].to_numpy() for a in atoms}
combos = [(a,) for a in atoms] + list(itertools.combinations(atoms,2)) + list(itertools.combinations(atoms,3))
EXN = ['atr2/1.5','atr2.5/2','atr3/1.5','low5/res','low10/res','low5/3atr']
OUT = {e: outcomes(e) for e in EXN}
OUTP = {e: outcomes(e, path='pess') for e in EXN}
D_arr = D.to_numpy()
rows=[]; t0=time.time()
for tier in ['1M','5M']:
    tm = tier_mask(tier)
    for cb in combos:
        m0 = tm.copy()
        for a in cb: m0 &= AM[a]
        if m0.sum() < 150: continue
        for rg, rm in REG.items():
            m = m0 & rm
            n = m.sum()
            if n < 120: continue
            for e in EXN:
                r = OUT[e][0][m]; rp = OUTP[e][0][m]
                ok = np.isfinite(r)
                if ok.sum() < 100: continue
                rows.append((tier,' & '.join(cb),rg,e,int(ok.sum()),float(np.nanmean(r)*100),float(np.nanmean(rp)*100)))
print('rows',len(rows), time.time()-t0)
o=pd.DataFrame(rows,columns=['tier','cond','reg','exit','n','avg','pavg'])
o.to_pickle('t2.pkl')
o['score']=np.minimum(o.avg,o.pavg)*np.sqrt(o.n)
for tier in ['1M','5M']:
    print(o[o.tier==tier].sort_values('score',ascending=False).groupby('cond').head(1).head(25).to_string())
