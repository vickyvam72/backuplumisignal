import pandas as pd, numpy as np, itertools
from sim2 import prep, simulate, stats
df = pd.read_pickle('feat2.pkl'); df, F = prep(df)
dates = sorted(df['date'].unique()); TEST = set(dates[-60:]); TRAIN = set(dates[:-60]); D = df['date']
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna()
A = {
 'uptrend': (df['ma20'] > df['ma50']) & (df['close'] > df['ma50']),
 'ma20up': df['ma20_slope'] > .02,
 'green': df['ret'] > 0, 'up3': df['ret'] >= .03, 'up8': df['ret'] >= .08,
 'ara': df['is_ara'], 'notara': ~df['is_ara'],
 'volhi': df['vol_ratio'] >= 2, 'vol4': df['vol_ratio'] >= 4, 'vollo': df['vol_ratio'] < .8,
 'dry5': df['dry5'] < .7,
 'brk20': df['close'] > df['hh20'], 'brk60': df['close'] > df['hh60'],
 'tight20': df['range20'] < .25,
 'cpos_hi': df['close_pos'] >= .8, 'uwick_lo': df['upper_wick'] < .01,
 'ad20+': df['ad20'] > .15, 'obv+': df['obv_chg10'] > 1, 'udvr>2': df['udvr10'] > 2,
 'gain20<15': df['gain20'] < .15, 'gain5<10': df['gain5'] < .10,
 'rsi50-70': df['rsi14'].between(50, 70),
 'inside': df['inside'], 'big10': df['big_in10'] >= 1,
 'squeeze': df['bbw'] <= df['bbw_min60']*1.15,
 'pos60_lo': df['pos60'] < .4,
}
combos = [(a,) for a in A] + list(itertools.combinations(A, 2))
exits = [(.03,.03,1),(.05,.04,3),(.08,.05,5),(.10,.06,5)]
modes = ['pre0','pre1','pre2','pre3']
rows=[]
for cb in combos:
    m = base.copy()
    for a in cb: m &= A[a]
    if m[D.isin(TRAIN)].sum() < 300: continue
    for mode in modes:
        for tp,sl,h in exits:
            r = simulate(df,F,m,mode,tp,sl,h)
            a = stats(r,D,TRAIN)
            if a.get('n',0) < 120: continue
            rows.append((' & '.join(cb),mode,tp,sl,h,a['n'],a['win'],a['avg'],a['pf']))
o = pd.DataFrame(rows,columns=['cond','mode','tp','sl','hold','n_tr','win_tr','avg_tr','pf_tr'])
o.to_csv('b5_train.csv',index=False)
top = o.sort_values('avg_tr',ascending=False).groupby('cond').head(1).head(40)
out=[]
for _,r0 in top.iterrows():
    m = base.copy()
    for a in r0.cond.split(' & '): m &= A[a]
    r = simulate(df,F,m,r0['mode'],r0.tp,r0.sl,int(r0.hold)); rp = simulate(df,F,m,r0['mode'],r0.tp,r0.sl,int(r0.hold),path='pess')
    b=stats(r,D,TEST); c=stats(rp,D,TRAIN); d=stats(rp,D,TEST)
    out.append(list(r0.values)+[b.get('n',0),b.get('win'),b.get('avg'),c.get('avg'),d.get('avg')])
pd.set_option('display.width',250)
print(pd.DataFrame(out,columns=list(o.columns)+['n_te','win_te','avg_te','pess_tr','pess_te']).to_string())
