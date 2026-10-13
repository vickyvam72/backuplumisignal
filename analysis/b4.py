import pandas as pd, numpy as np, itertools
from sim2 import prep, simulate, stats
df = pd.read_pickle('feat2.pkl'); df, F = prep(df)
dates = sorted(df['date'].unique()); TEST = set(dates[-60:]); TRAIN = set(dates[:-60]); D = df['date']
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna() & ~df['is_ara']
A = {
 'uptrend': (df['ma20'] > df['ma50']) & (df['close'] > df['ma50']),
 'downtrend': df['ma20'] < df['ma50'],
 'red': df['ret'] < 0,
 'down3': df['ret'] <= -.03,
 'down6': df['ret'] <= -.06,
 'green': df['ret'] > 0,
 'up3': df['ret'] >= .03,
 'red2': df['red_streak'] >= 2,
 'rsi<40': df['rsi14'] < 40,
 'rsi>60': df['rsi14'] > 60,
 'volhi': df['vol_ratio'] >= 2,
 'vollo': df['vol_ratio'] < .8,
 'big10': df['big_in10'] >= 1,
 'gain20>20': df['gain20'] > .2,
 'gain20<0': df['gain20'] < 0,
 'ad20+': df['ad20'] > .15,
 'udvr>1.5': df['udvr10'] > 1.5,
 'cpos_hi': df['close_pos'] >= .7,
 'cpos_lo': df['close_pos'] <= .3,
 'lwick': df['lower_wick'] >= .03,
 'pos60_lo': df['pos60'] < .3,
 'pos60_hi': df['pos60'] > .8,
 'p>=100': df['close'] >= 100,
 'p<200': df['close'] < 200,
 'liq1M': df['val_ma20'] >= 1e9,
}
combos = [(a,) for a in A] + list(itertools.combinations(A, 2))
exits = [(.03,.03,1),(.05,.04,3),(.08,.05,5)]
modes = ['pre2','pre3','pre5']
rows=[]
for cb in combos:
    m = base.copy()
    for a in cb: m &= A[a]
    if m[D.isin(TRAIN)].sum() < 400: continue
    for mode in modes:
        for tp,sl,h in exits:
            r = simulate(df,F,m,mode,tp,sl,h)
            a = stats(r,D,TRAIN)
            if a.get('n',0) < 120: continue
            rows.append((' & '.join(cb),mode,tp,sl,h,a['n'],a['win'],a['avg'],a['pf']))
o = pd.DataFrame(rows,columns=['cond','mode','tp','sl','hold','n_tr','win_tr','avg_tr','pf_tr'])
o.to_csv('b4_train.csv',index=False)
top = o.sort_values('avg_tr',ascending=False).groupby('cond').head(1).head(40)
# evaluate test + pessimistic only for the selected
out=[]
for _,r0 in top.iterrows():
    m = base.copy()
    for a in r0.cond.split(' & '): m &= A[a]
    r = simulate(df,F,m,r0['mode'],r0.tp,r0.sl,int(r0.hold)); rp = simulate(df,F,m,r0['mode'],r0.tp,r0.sl,int(r0.hold),path='pess')
    b=stats(r,D,TEST); c=stats(rp,D,TRAIN); d=stats(rp,D,TEST)
    out.append(list(r0.values)+[b.get('n',0),b.get('win'),b.get('avg'),c.get('avg'),d.get('avg')])
pd.set_option('display.width',250)
print(pd.DataFrame(out,columns=list(o.columns)+['n_te','win_te','avg_te','pess_tr','pess_te']).to_string())
