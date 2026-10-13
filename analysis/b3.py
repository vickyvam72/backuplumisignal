import pandas as pd, numpy as np
from sim2 import prep, simulate, stats
df = pd.read_pickle('feat2.pkl'); df, F = prep(df)
dates = sorted(df['date'].unique()); TEST = set(dates[-60:]); TRAIN = set(dates[:-60]); D = df['date']
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna()
up = df['ma20'] > df['ma50']
conds = {
 'all': base,
 'p100': base & (df['close'] >= 100),
 'uptrend': base & up,
 'down5': base & (df['ret'] <= -.05),
 'down3_uptrend': base & (df['ret'] <= -.03) & up,
 'red2+': base & (df['red_streak'] >= 2),
 'pullback_after_ara': base & (df['ara_in10'] >= 1) & (df['ret'] < 0),
 'after_big10': base & (df['big_in10'] >= 1),
 'acc_ad20': base & (df['ad20'] > .2),
 'acc_obv': base & (df['obv_chg10'] > 1),
 'rsi<35': base & (df['rsi14'] < 35),
 'strong_close': base & (df['close_pos'] >= .8) & (df['ret'] > .02) & ~df['is_ara'],
 'vol_spike_up': base & (df['vol_ratio'] >= 2.5) & (df['ret'] > .03) & ~df['is_ara'],
 'ara': base & df['is_ara'],
}
rows=[]
for name,m in conds.items():
  for mode in ['pre0','pre2','pre3','pre5']:
    for tp,sl,hold in [(.03,.03,1),(.04,.04,2),(.05,.04,3),(.06,.05,3),(.08,.05,5),(.02,.03,1)]:
      r=simulate(df,F,m,mode,tp,sl,hold); rp=simulate(df,F,m,mode,tp,sl,hold,path='pess')
      a,b,c,d=stats(r,D,TRAIN),stats(r,D,TEST),stats(rp,D,TRAIN),stats(rp,D,TEST)
      rows.append((name,mode,tp,sl,hold,a.get('n',0),a.get('win'),a.get('avg'),b.get('n',0),b.get('win'),b.get('avg'),c.get('avg'),d.get('avg')))
o=pd.DataFrame(rows,columns=['cond','mode','tp','sl','hold','n_tr','win_tr','avg_tr','n_te','win_te','avg_te','pess_tr','pess_te'])
o.to_csv('b3.csv',index=False)
pd.set_option('display.width',250); pd.set_option('display.max_rows',500)
print(o.sort_values('avg_tr',ascending=False).groupby('cond').head(2).to_string())
