import pandas as pd, numpy as np
df = pd.read_pickle('bars.pkl')
df = df[['date','ticker','open','high','low','close','volume']]
df = df.dropna(subset=['close']).sort_values(['ticker','date']).reset_index(drop=True)
df['volume'] = df['volume'].fillna(0)
g = df.groupby('ticker', group_keys=False)
pc = g['close'].shift(1)
df['prev_close'] = pc
df['ret'] = df['close']/pc - 1
df['ara_lim'] = np.where(pc<=200, .35, np.where(pc<=5000, .25, .20))
df.loc[df['ret'].abs() > df['ara_lim'] + 0.02, 'ret'] = np.nan   # splits / data errors
df['value'] = df['close']*df['volume']
rng = (df['high']-df['low']).replace(0, np.nan)
df['close_pos'] = ((df['close']-df['low'])/rng).fillna(0.5)
df['upper_wick'] = (df['high']-df[['open','close']].max(axis=1))/df['close']
df['is_ara'] = (df['ret'] >= df['ara_lim']*0.88) & (df['close'] >= df['high']-1e-9)
df['near_ara'] = (df['ret'] >= df['ara_lim']*0.70) & (df['close_pos'] >= 0.85)
df['is_arb'] = (df['ret'] <= -df['ara_lim']*0.88) | ((df['ret'] <= -0.14) & (df['close'] <= df['low']+1e-9))
for n in (5, 10, 20):
    df[f'vol_ma{n}'] = g['volume'].transform(lambda s: s.shift(1).rolling(n, min_periods=max(3, n//2)).mean())
    df[f'val_ma{n}'] = g['value'].transform(lambda s: s.shift(1).rolling(n, min_periods=max(3, n//2)).mean())
    df[f'hh{n}'] = g['high'].transform(lambda s: s.shift(1).rolling(n, min_periods=max(3, n//2)).max())
    df[f'll{n}'] = g['low'].transform(lambda s: s.shift(1).rolling(n, min_periods=max(3, n//2)).min())
    df[f'ma{n}'] = g['close'].transform(lambda s: s.rolling(n, min_periods=max(3, n//2)).mean())
df['vol_ratio'] = df['volume']/df['vol_ma20'].replace(0, np.nan)
df['dry5'] = df['vol_ma5']/df['vol_ma20'].replace(0, np.nan)          # volume of the 5 days before vs 20-day avg
df['gain5'] = df['close']/g['close'].shift(5) - 1
df['gain20'] = df['close']/g['close'].shift(20) - 1
df['range20'] = df['hh20']/df['ll20'] - 1                               # how tight the base was
df['dist_ma20'] = df['close']/df['ma20'] - 1
df['trade_days20'] = g['volume'].transform(lambda s: (s>0).astype(int).shift(1).rolling(20, min_periods=10).sum())
tr = pd.concat([df['high']-df['low'], (df['high']-pc).abs(), (df['low']-pc).abs()], axis=1).max(axis=1)
df['atr14'] = tr.groupby(df['ticker']).transform(lambda s: s.rolling(14, min_periods=7).mean())
df['up_streak'] = g['ret'].transform(lambda s: s.gt(0).astype(int).groupby((s<=0).cumsum()).cumsum())
df['ara_streak'] = g['is_ara'].transform(lambda s: s.astype(int).groupby((~s).cumsum()).cumsum())
# forward outcomes
for k in (1, 2, 3, 5):
    df[f'fwd{k}'] = g['close'].shift(-k)/df['close'] - 1
df['next_open'] = g['open'].shift(-1)
df['next_ret'] = g['ret'].shift(-1)
df['next_big'] = df['next_ret'] >= 0.10
df.to_pickle('feat.pkl')
dates = sorted(df['date'].unique())
print("sessions", len(dates), "last", dates[-3:], "rows", len(df))
