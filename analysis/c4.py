import pandas as pd, numpy as np, json
exec(open('c3.py').read().split("o = pd.read_csv")[0])
Q = np.array_split(np.array(dates), 4)
qm = [D.isin(set(q)).to_numpy() for q in Q]
o = pd.read_csv('c2.csv')
# for each cond, take the 3 best train configs, then look at quarterly behaviour
o = o.sort_values('score',ascending=False).groupby('cond').head(3)
rows=[]
for _,r0 in o.iterrows():
    m = base.copy()
    for a in r0.cond.split(' & '): m &= A[a]
    mk = topk(m, ranks[r0['rank']])
    r = simulate(df,F,mk,r0['mode'],**exits[r0.exit]); rp = simulate(df,F,mk,r0['mode'],path='pess',**exits[r0.exit])
    qs = [np.nanmean(rp[q]) * 100 for q in qm]
    qn = [int(np.isfinite(rp[q]).sum()) for q in qm]
    a = stats(r); p = stats(rp)
    rows.append((r0.cond, r0['rank'], r0.exit, a['n'], a['win'], a['avg'], a['tot'], p['avg'], sum(x > 0 for x in qs), min(qs), *[round(x,2) for x in qs]))
q = pd.DataFrame(rows, columns=['cond','rank','exit','n','win','avg','tot','pess','q_pos','q_min','q1','q2','q3','q4'])
q.to_csv('c4.csv', index=False)
pd.set_option('display.width',250); pd.set_option('display.max_rows',200)
print(q.sort_values(['q_pos','q_min'],ascending=False).head(40).to_string())
