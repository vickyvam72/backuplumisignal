import pandas as pd, numpy as np, json
from sim2 import prep, simulate, stats
df = pd.read_pickle('feat2.pkl'); df, F = prep(df)
dates = sorted(df['date'].unique()); TEST = set(dates[-60:]); TRAIN = set(dates[:-60]); D = df['date']
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna() & ~df['is_ara']
squeeze = df['bbw'] <= df['bbw_min60']*1.15
S = {
 'S1 Pantulan Panik': base & (df['ret'] <= -.03) & (df['close_pos'] <= .35) & (df['rsi14'] < 40),
 'S1b': base & (df['red_streak'] >= 2) & (df['gain20'] < 0) & (df['close_pos'] <= .5),
 'S2 Squeeze Akumulasi': base & squeeze & (df['udvr10'] > 2),
 'S2b': base & squeeze & (df['udvr10'] > 1.5) & (df['ad20'] > 0),
 'S3 Bangun dari Dasar': base & (df['pos60'] < .4) & (df['ret'] >= .03),
 'S3b': base & (df['pos60'] < .4) & (df['ret'] >= .03) & (df['vol_ratio'] >= 1.5),
 'S4 Volume Kering': base & (df['dry5'] < .7) & (df['gain20'] < .15) & (df['ret'] > 0),
 'S4b': base & (df['dry5'] < .7) & (df['gain20'] < .15) & (df['close_pos'] >= .6),
 'S5 Koreksi Uptrend': base & (df['ma20'] > df['ma50']) & (df['close'] > df['ma50']) & (df['ret'] < 0) & (df['vol_ratio'] < .8),
 'S5b': base & (df['ma20'] > df['ma50']) & (df['red_streak'] >= 2) & (df['dist_ma20'].between(-.05, .03)),
 'S5c': base & (df['gain20'] > .2) & (df['red_streak'] >= 2) & (df['vol_ratio'] < 1),
}
grid_modes = ['pre0','pre1','pre2','pre3','pre4','pre5','pre6']
grid_exits = [(.03,.03,1),(.04,.04,2),(.05,.04,3),(.06,.05,3),(.08,.05,5),(.10,.06,5),(.12,.07,7)]
res = {}
for nm, m in S.items():
    best=None
    for mode in grid_modes:
        for tp,sl,h in grid_exits:
            a = stats(simulate(df,F,m,mode,tp,sl,h),D,TRAIN)
            if a.get('n',0) < 100: continue
            # robust objective: avg under both path models
            p = stats(simulate(df,F,m,mode,tp,sl,h,path='pess'),D,TRAIN)
            score = min(a['avg'], p['avg']) * np.sqrt(a['n'])
            if best is None or score > best[0]: best=(score,mode,tp,sl,h,a,p)
    _,mode,tp,sl,h,a,p = best
    r=simulate(df,F,m,mode,tp,sl,h); rp=simulate(df,F,m,mode,tp,sl,h,path='pess')
    b=stats(r,D,TEST); d=stats(rp,D,TEST)
    sig_day = m[D.isin(TRAIN)].sum()/len(TRAIN)
    print(f"{nm:24} {mode} TP{tp:.0%} SL{sl:.0%} H{h} | sinyal/hari {sig_day:4.1f} | TRAIN n{a['n']} win{a['win']} avg{a['avg']:+.2f} PF{a['pf']} pess{p['avg']:+.2f} | TEST n{b.get('n')} win{b.get('win')} avg{b.get('avg'):+.2f} PF{b.get('pf')} pess{d.get('avg'):+.2f}")
    res[nm]=dict(mode=mode,tp=tp,sl=sl,hold=h,train=a,train_pess=p,test=b,test_pess=d,sig_per_day=round(sig_day,1))
json.dump(res,open('b6.json','w'),indent=1,default=str)
