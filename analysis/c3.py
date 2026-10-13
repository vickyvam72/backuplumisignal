import pandas as pd, numpy as np, json
exec(open('c2.py').read().split("TRm = ")[0].replace("a1 = pd.read_csv","#").replace("top_pairs = a1","#").replace("cands = ","#"))
TRm = D.isin(TRAIN).to_numpy(); TEm = D.isin(TEST).to_numpy()
o = pd.read_csv('c2.csv').sort_values('score',ascending=False).groupby('cond').head(1)
chosen=[]; masks=[]
for _,r0 in o.iterrows():
    m = base.copy()
    for a in r0.cond.split(' & '): m &= A[a]
    mk = topk(m, ranks[r0['rank']])
    r = simulate(df,F,mk,r0['mode'],**exits[r0.exit])
    tr = np.isfinite(r) & TRm
    if any((tr & mm).sum() > 0.3*min(tr.sum(), mm.sum()) for mm in masks): continue
    chosen.append((r0, r)); masks.append(tr)
    if len(chosen)==5: break
out={}
for i,(r0,r) in enumerate(chosen,1):
    m = base.copy()
    for a in r0.cond.split(' & '): m &= A[a]
    mk = topk(m, ranks[r0['rank']])
    rp = simulate(df,F,mk,r0['mode'],path='pess',**exits[r0.exit])
    a,b,c,d = stats(r,D,TRAIN),stats(r,D,TEST),stats(rp,D,TRAIN),stats(rp,D,TEST)
    print(f"S{i} {r0.cond:34} rank={r0['rank']:8} {r0.exit:10} | TRAIN n{a['n']} win{a['win']} avg{a['avg']:+.2f} tot{a['tot']:+.0f} pess{c['avg']:+.2f} | TEST n{b['n']} win{b['win']} avg{b['avg']:+.2f} tot{b['tot']:+.0f} pess{d['avg']:+.2f} PF{b['pf']}")
    out[i]=dict(cond=r0.cond,rank=r0['rank'],mode=r0['mode'],exit=r0.exit)
json.dump(out,open('c3_pick.json','w'),indent=1)
