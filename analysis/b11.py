exec(open('b10.py').read().split("S = {")[0])
squeeze = df['bbw'] <= df['bbw_min60']*1.15
up = (df['ma20'] > df['ma50'])
S = {
 '2a sq+udvr2+up': base & squeeze & (df['udvr10'] > 2) & up,
 '2b sq+udvr2+ad': base & squeeze & (df['udvr10'] > 2) & (df['ad20'] > .1),
 '2c sq+udvr1.5+green': base & squeeze & (df['udvr10'] > 1.5) & (df['ret'] > 0),
 '2d tight20+udvr2': base & (df['range20'] < .2) & (df['udvr10'] > 2),
 '4a dry+low+buyclose': base & (df['dry5'] < .7) & (df['pos60'] < .4) & (df['ret'] > 0) & (df['close_pos'] >= .6),
 '4b dry+low+udvr': base & (df['dry5'] < .7) & (df['pos60'] < .4) & (df['udvr10'] > 1.5),
 '4c dry+tight+ad': base & (df['dry5'] < .7) & (df['range20'] < .25) & (df['ad20'] > .1),
}
modes = ['pre0','pre1','pre2','pre3','pre5']
exits = {'6/5/3':(.06,.05,3),'8/5/3':(.08,.05,3),'10/6/5':(.10,.06,5),'12/7/7':(.12,.07,7)}
for nm,m in S.items():
    best=None
    for mo in modes:
        for en,(tp,sl,h) in exits.items():
            a=stats(simulate(df,F,m,mo,tp,sl,h),D,TRAIN)
            if a.get('n',0)<100: continue
            p=stats(simulate(df,F,m,mo,tp,sl,h,path='pess'),D,TRAIN)
            sc=min(a['avg'],p['avg'])*np.sqrt(a['n'])
            if best is None or sc>best[0]: best=(sc,mo,en,a,p)
    if best is None: print(nm,'n too small'); continue
    _,mo,en,a,p=best; tp,sl,h=exits[en]
    r=simulate(df,F,m,mo,tp,sl,h); rp=simulate(df,F,m,mo,tp,sl,h,path='pess')
    b,d=stats(r,D,TEST),stats(rp,D,TEST)
    mon=pd.DataFrame({'m':D.str[:7],'r':r}).dropna().groupby('m')['r'].mean()
    print(f"{nm:22} {mo} {en:7} sig/d {m[D.isin(TRAIN)].sum()/len(TRAIN):5.1f} | TRAIN n{a['n']} win{a['win']} avg{a['avg']:+.2f} pess{p['avg']:+.2f} | TEST n{b['n']} win{b['win']} avg{b['avg']:+.2f} pess{d['avg']:+.2f} | mon+ {(mon>0).sum()}/{len(mon)}")
