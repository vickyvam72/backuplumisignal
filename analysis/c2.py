import pandas as pd, numpy as np, itertools, json
from sim2 import prep, simulate, stats, tick
exec(open('c1.py').read().split("combos = ")[0].split("from sim2")[1].split("\n",1)[1])
a1 = pd.read_csv('c1.csv').sort_values('avg_tr',ascending=False).groupby('cond').head(1)
top_pairs = a1.head(45)['cond'].tolist()
atoms = ['up3','rsi<35','squeeze','upstreak3','pos60_lo','udvr2','vollo','near_ma20','inside','p<200','dry5','green','tight20','brk20','cpos_hi','lwick','ad20+','uptrend','downtrend','gapdown_today']
triples = [' & '.join(t) for t in itertools.combinations(atoms,3)]
cands = list(dict.fromkeys(top_pairs + triples))
atrp = (df['atr14']/df['close']).clip(.02,.2)
tk = tick(df['close'].to_numpy())
SLPX = {'low1': df['low'].to_numpy() - tk, 'll5': np.minimum(df['ll5'].to_numpy(), df['low'].to_numpy()) - tk}
exits = {
 'f6/5/3':dict(tp=.06,sl=.05,hold=3), 'f8/5/3':dict(tp=.08,sl=.05,hold=3), 'f10/6/5':dict(tp=.10,sl=.06,hold=5), 'f12/7/7':dict(tp=.12,sl=.07,hold=7),
 'sLow/8/5':dict(tp=.08,sl=.05,hold=5,sl_px=SLPX['low1']), 'sLL5/10/5':dict(tp=.10,sl=.06,hold=5,sl_px=SLPX['ll5']),
 'sLL5/12/7':dict(tp=.12,sl=.07,hold=7,sl_px=SLPX['ll5']),
}
ranks = {'value':df['val_ma20'],'volratio':df['vol_ratio'],'ret':df['ret'],'rsi_low':-df['rsi14'],'udvr':df['udvr10'].fillna(0),'atr':atrp,'lowpos':-df['pos60'].fillna(1),'cpos':df['close_pos']}
def topk(m, rk, k=5):
    s = pd.DataFrame({'date':D[m],'r':rk[m]}).sort_values('r',ascending=False).groupby('date').head(k)
    out = np.zeros(len(df),bool); out[s.index]=True; return out
TRm = D.isin(TRAIN).to_numpy()
# stage B1: all-signal edge for the 3-way combos (cheap filter)
keep=[]
for c in cands:
    m = base.copy()
    for a in c.split(' & '): m &= A[a]
    if m.to_numpy()[TRm].sum() < 150: continue
    r = simulate(df,F,m,'zone1',.12,.07,7); a=stats(r,D,TRAIN)
    if a['n']>=150 and a['avg']>0.5: keep.append((c,a['avg'],a['n']))
keep = sorted(keep,key=lambda x:-x[1])[:40]
print('kandidat',len(keep))
rows=[]
for c,_,_ in keep:
    m = base.copy()
    for a in c.split(' & '): m &= A[a]
    for rn,rk in ranks.items():
        mk = topk(m,rk)
        for mode in ['zone1']:
            for en,kw in exits.items():
                r = simulate(df,F,mk,mode,**kw); a = stats(r,D,TRAIN)
                if a.get('n',0) < 100: continue
                p = stats(simulate(df,F,mk,mode,path='pess',**kw),D,TRAIN)
                rows.append((c,rn,mode,en,a['n'],a['win'],a['avg'],a['tot'],p['avg'],min(a['avg'],p['avg'])*np.sqrt(a['n'])))
o = pd.DataFrame(rows,columns=['cond','rank','mode','exit','n_tr','win_tr','avg_tr','tot_tr','pess_tr','score'])
o.to_csv('c2.csv',index=False)
pd.set_option('display.width',250); pd.set_option('display.max_rows',200)
print(o.sort_values('score',ascending=False).groupby('cond').head(1).head(40).to_string())
