import pandas as pd, numpy as np, json
exec(open('c3.py').read().split("o = pd.read_csv")[0])
C = [
 ('rsi<35 & udvr2 & p<200','udvr','f12/7/7'),
 ('up3 & rsi<35 & p<200','rsi_low','sLL5/12/7'),
 ('upstreak3 & pos60_lo & inside','lowpos','sLL5/12/7'),
 ('squeeze & upstreak3 & pos60_lo','volratio','sLL5/12/7'),
 ('squeeze & upstreak3 & downtrend','volratio','sLL5/12/7'),
 ('rsi<35 & vollo & gapdown_today','ret','sLL5/12/7'),
 ('rsi<35 & p<200 & gapdown_today','lowpos','sLL5/12/7'),
 ('squeeze & near_ma20 & p<200','volratio','f12/7/7'),
 ('near_ma20 & inside & p<200','atr','f12/7/7'),
 ('upstreak3 & pos60_lo & dry5','lowpos','sLL5/12/7'),
]
M=[]
for c,rk,ex in C:
    m = base.copy()
    for a in c.split(' & '): m &= A[a]
    r = simulate(df,F,topk(m,ranks[rk]),'zone1',**exits[ex]); M.append(np.isfinite(r))
print('overlap % of row-strategy trades shared with column')
for i in range(len(C)):
    print(i, [int(100*(M[i]&M[j]).sum()/M[i].sum()) for j in range(len(C))])
