import pandas as pd, numpy as np
df = pd.read_pickle('feat2.pkl')
g = df.groupby('ticker', group_keys=False)
df['ma5'] = g['close'].transform(lambda s: s.rolling(5, min_periods=3).mean())
df['ma5_prev'] = g['ma5'].shift(1); df['ma20_prev'] = g['ma20'].shift(1)
df['cross5_20'] = (df['ma5'] > df['ma20']) & (df['ma5_prev'] <= df['ma20_prev'])
df['above_ma50'] = df['close'] > df['ma50']
df['above_ma50_prev'] = g['above_ma50'].shift(1).fillna(False).astype(bool)
df['reclaim_ma50'] = df['above_ma50'] & ~df['above_ma50_prev']
df['new_hh60'] = df['close'] > df['hh60']
rng = (df['high'] - df['low'])
df['nr7'] = rng <= rng.groupby(df['ticker']).transform(lambda s: s.rolling(7, min_periods=5).min())
df['hl_rise'] = (df['low'] > g['low'].shift(1)) & (g['low'].shift(1) > g['low'].shift(2))
df['ret1_prev'] = g['ret'].shift(1)
df['reversal'] = (df['ret1_prev'] <= -.03) & (df['ret'] >= .02) & (df['close'] > g['high'].shift(1))
df['engulf'] = (df['ret'] > 0) & (df['open'] <= g['close'].shift(1)) & (df['close'] >= g['open'].shift(1)) & (g['ret'].shift(1) < 0)
df['dist_ma50'] = df['close'] / df['ma50'] - 1
df['pullback_up'] = (df['ma20'] > df['ma50']) & (df['ma20_slope'] > 0) & df['dist_ma20'].between(-.05, .0) & (df['ret'] < .02)
df['vol_up_close_hi'] = (df['vol_ratio'] >= 1.5) & (df['close_pos'] >= .75) & (df['ret'] > .01)
df['gain60'] = df['close'] / g['close'].shift(60) - 1
df.to_pickle('feat3.pkl'); print(df.shape)
