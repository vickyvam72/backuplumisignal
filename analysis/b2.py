import pandas as pd, numpy as np
from sim2 import prep
df = pd.read_pickle('feat2.pkl')
df, F = prep(df)
dates = sorted(df['date'].unique()); TEST = dates[-60:]
df['tr'] = ~df['date'].isin(TEST)
o1, c1, h1, l1, o2, c3, c2 = F[('open',1)], F[('close',1)], F[('high',1)], F[('low',1)], F[('open',2)], F[('close',3)], F[('close',2)]
df['g1'] = o1/df['close']-1
df['oc1'] = c1/o1-1; df['oo2'] = o2/o1-1; df['oc2'] = c2/o1-1; df['oc3'] = c3/o1-1
df['oh1'] = h1/o1-1
locked = (h1 == l1)
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna() & ~locked & np.isfinite(df['oc1'])
df = df[base].copy()
df['gb'] = pd.cut(df['g1'], [-1, -.05, -.02, -.005, .005, .02, .05, .10, 1])
groups = {
 'all': df.index == df.index,
 'ara': df['is_ara'],
 'up5-ara': (df['ret'] >= .05) & ~df['is_ara'],
 'down5': df['ret'] <= -.05,
 'flat': df['ret'].abs() < .02,
}
pd.set_option('display.width', 250)
for nm, m in groups.items():
    s = df[m]
    t = s.groupby(['tr', 'gb'], observed=True)[['oc1', 'oo2', 'oc2', 'oc3', 'oh1']].agg(['mean']).round(4)*100
    t['n'] = s.groupby(['tr', 'gb'], observed=True).size()
    print('=====', nm); print(t.to_string())
