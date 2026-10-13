import pandas as pd, numpy as np, itertools
from sim2 import prep, simulate, stats, tick
df = pd.read_pickle('feat2.pkl'); df, F = prep(df)
dates = sorted(df['date'].unique()); TEST = set(dates[-60:]); TRAIN = set(dates[:-60]); D = df['date']
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna() & ~df['is_ara']
up = (df['ma20'] > df['ma50'])
A = {
 'uptrend': up & (df['close'] > df['ma50']), 'downtrend': ~up,
 'ma20up': df['ma20_slope'] > .02,
 'red': df['ret'] < 0, 'down3': df['ret'] <= -.03, 'down6': df['ret'] <= -.06,
 'green': df['ret'] > 0, 'up3': df['ret'] >= .03, 'up8': df['ret'] >= .08, 'flat': df['ret'].abs() < .015,
 'red2': df['red_streak'] >= 2, 'upstreak3': df['up_streak'] >= 3,
 'rsi<35': df['rsi14'] < 35, 'rsi50-70': df['rsi14'].between(50, 70), 'rsi>70': df['rsi14'] > 70,
 'volhi': df['vol_ratio'] >= 2, 'vol4': df['vol_ratio'] >= 4, 'vollo': df['vol_ratio'] < .8,
 'dry5': df['dry5'] < .7, 'brk20': df['close'] > df['hh20'], 'brk60': df['close'] > df['hh60'],
 'tight20': df['range20'] < .2, 'squeeze': df['bbw'] <= df['bbw_min60']*1.15,
 'cpos_hi': df['close_pos'] >= .8, 'cpos_lo': df['close_pos'] <= .3,
 'lwick': df['lower_wick'] >= .03, 'uwick': df['upper_wick'] >= .04,
 'ad20+': df['ad20'] > .15, 'udvr2': df['udvr10'] > 2, 'udvr<.7': df['udvr10'] < .7,
 'gain20>30': df['gain20'] > .3, 'gain20<0': df['gain20'] < 0,
 'pos60_lo': df['pos60'] < .3, 'pos60_hi': df['pos60'] > .85,
 'big10': df['big_in10'] >= 1, 'inside': df['inside'],
 'near_ma20': df['dist_ma20'].between(-.03, .02), 'far_ma20': df['dist_ma20'] > .25,
 'gapdown_today': df['gap'] <= -.03, 'gapup_today': df['gap'] >= .03,
 'p<200': df['close'] < 200, 'p>=500': df['close'] >= 500, 'liq1M': df['val_ma20'] >= 1e9,
}
combos = [(a,) for a in A] + list(itertools.combinations(A, 2))
exits = [(.06,.05,3),(.10,.06,5),(.12,.07,7)]
rows=[]
for cb in combos:
    m = base.copy()
    for a in cb: m &= A[a]
    ntr = int(m[D.isin(TRAIN)].sum())
    if ntr < 250: continue
    for mode in ['zone1']:
        for tp,sl,h in exits:
            r = simulate(df,F,m,mode,tp,sl,h)
            a = stats(r,D,TRAIN)
            rows.append((' & '.join(cb),mode,tp,sl,h,ntr,a['n'],a['win'],a['avg'],a['pf']))
o = pd.DataFrame(rows,columns=['cond','mode','tp','sl','hold','sig','n_tr','win_tr','avg_tr','pf_tr'])
o.to_csv('c1.csv',index=False)
pd.set_option('display.width',250); pd.set_option('display.max_rows',200)
print(o.sort_values('avg_tr',ascending=False).groupby('cond').head(1).head(60).to_string())
