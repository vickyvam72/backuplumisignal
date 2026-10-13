import pandas as pd, numpy as np
from sim2 import prep, simulate, stats
exec(open('b6.py').read().split("S = {")[0])
squeeze = df['bbw'] <= df['bbw_min60']*1.15
S = {
 'BASE all': base,
 'BASE all p>=100': base & (df['close']>=100),
 'X strong close': base & (df['close_pos'] >= .8) & (df['ret'] >= .03),
 'X strong close uptrend': base & (df['close_pos'] >= .8) & (df['ret'] >= .02) & (df['ma20'] > df['ma50']),
 'X acc udvr ad': base & (df['udvr10'] > 2) & (df['ad20'] > .15) & (df['gain20'] < .2),
 'X uptrend pullback ma20': base & (df['ma20'] > df['ma50']) & (df['dist_ma20'].between(-.04, .01)) & (df['ma20_slope']>0),
 'X after big10 rest': base & (df['big_in10'] >= 1) & (df['vol_ratio'] < .8) & (df['ret'].abs() < .03),
 'X inside after up': base & df['inside'] & (df['ret1'] >= .05),
 'X hammer': base & (df['lower_wick'] >= .04) & (df['close_pos'] >= .6),
}
grid_modes = ['pre0','pre1','pre2','pre3','pre4','pre5','pre6']
grid_exits = [(.03,.03,1),(.05,.04,3),(.08,.05,5),(.10,.06,5),(.12,.07,7)]
for nm, m in S.items():
    for fixed in ([None] if not nm.startswith('BASE') else ['pre0','pre1','pre3','pre5']):
        best=None
        for mode in (grid_modes if fixed is None else [fixed]):
            for tp,sl,h in grid_exits:
                a = stats(simulate(df,F,m,mode,tp,sl,h),D,TRAIN)
                if a.get('n',0) < 100: continue
                p = stats(simulate(df,F,m,mode,tp,sl,h,path='pess'),D,TRAIN)
                score = min(a['avg'], p['avg']) * np.sqrt(a['n'])
                if best is None or score > best[0]: best=(score,mode,tp,sl,h,a,p)
        _,mode,tp,sl,h,a,p = best
        b=stats(simulate(df,F,m,mode,tp,sl,h),D,TEST); d=stats(simulate(df,F,m,mode,tp,sl,h,path='pess'),D,TEST)
        print(f"{nm:26} {mode} TP{tp:.0%} SL{sl:.0%} H{h} | TRAIN n{a['n']} win{a['win']} avg{a['avg']:+.2f} pess{p['avg']:+.2f} | TEST n{b.get('n')} win{b.get('win')} avg{b.get('avg'):+.2f} pess{d.get('avg'):+.2f}")
