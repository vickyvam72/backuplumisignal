import pandas as pd, numpy as np, json
from sim2 import prep, simulate, stats
df = pd.read_pickle('feat2.pkl'); df, F = prep(df)
dates = sorted(df['date'].unique()); TEST = set(dates[-60:]); TRAIN = set(dates[:-60]); D = df['date']
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna() & ~df['is_ara']
squeeze = df['bbw'] <= df['bbw_min60']*1.15
S = {
 1: ('Pantulan Panik', base & (df['ret'] <= -.03) & (df['close_pos'] <= .35) & (df['rsi14'] < 40)),
 2: ('Squeeze Akumulasi', base & squeeze & (df['udvr10'] > 2)),
 3: ('Bangun dari Dasar', base & (df['pos60'] < .4) & (df['ret'] >= .03) & (df['vol_ratio'] >= 1.5)),
 4: ('Kering di Dasar', base & (df['dry5'] < .7) & (df['pos60'] < .4) & (df['udvr10'] > 1.5)),
 5: ('Rehat Setelah Lonjakan', base & (df['big_in10'] >= 1) & (df['vol_ratio'] < .8) & (df['ret'].abs() < .03)),
}
modes = ['pre0','pre1','pre2','pre3','pre4','pre5','pre6']
exits = {'6/5/3':(.06,.05,3),'8/5/3':(.08,.05,3),'10/6/5':(.10,.06,5),'12/7/7':(.12,.07,7)}
final={}; masks={}
for sid,(nm,m) in S.items():
    best=None
    for mo in modes:
        for en,(tp,sl,h) in exits.items():
            a=stats(simulate(df,F,m,mo,tp,sl,h),D,TRAIN)
            if a.get('n',0)<100: continue
            p=stats(simulate(df,F,m,mo,tp,sl,h,path='pess'),D,TRAIN)
            sc=min(a['avg'],p['avg'])*np.sqrt(a['n'])
            if best is None or sc>best[0]: best=(sc,mo,en,a,p)
    _,mo,en,a,p=best; tp,sl,h=exits[en]
    r=simulate(df,F,m,mo,tp,sl,h); rp=simulate(df,F,m,mo,tp,sl,h,path='pess')
    b,d=stats(r,D,TEST),stats(rp,D,TEST)
    mon=pd.DataFrame({'m':D.str[:7],'r':r}).dropna().groupby('m')['r'].agg(['size','mean'])
    sigs=m.sum(); fills=np.isfinite(r).sum()
    trade_days=pd.Series(D[np.isfinite(r)]).nunique()
    print(f"S{sid} {nm:24} {mo} exit {en:7} | sinyal/hari {m[D.isin(TRAIN)].sum()/len(TRAIN):5.1f} terisi {fills/sigs*100:4.1f}% | hari ada trade {trade_days}/{len(dates)}")
    print(f"     TRAIN n{a['n']} win{a['win']}% avg{a['avg']:+.2f}% PF{a['pf']} pess{p['avg']:+.2f} | TEST n{b['n']} win{b['win']}% avg{b['avg']:+.2f}% PF{b['pf']} pess{d['avg']:+.2f} | bulan positif {(mon['mean']>0).sum()}/{len(mon)}")
    final[sid]=dict(name=nm,mode=mo,exit=en,train=a,train_pess=p,test=b,test_pess=d,
        sig_per_day=round(m[D.isin(TRAIN)].sum()/len(TRAIN),1),fill=round(fills/sigs*100,1),
        monthly={k:[int(v['size']),round(v['mean']*100,2)] for k,v in mon.iterrows()})
    masks[sid]=np.isfinite(r)
    df[f'res{sid}']=r
# overlap of filled trades
print('overlap (trades shared):')
for i in S:
    print(i,[int((masks[i]&masks[j]).sum()) for j in S])
json.dump(final,open('final_morning.json','w'),indent=1,default=str)
df[['date','ticker','close','ret']+[f'res{i}' for i in S]].to_pickle('final_res.pkl')
