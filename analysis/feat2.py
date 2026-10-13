"""Extra technical features + accumulation proxies (no broker data available historically)."""
import pandas as pd, numpy as np
df = pd.read_pickle('feat.pkl')
g = df.groupby('ticker', group_keys=False)
pc = df['prev_close']

# RSI14 (Wilder)
d = g['close'].diff()
up, dn = d.clip(lower=0), (-d).clip(lower=0)
au = up.groupby(df['ticker']).transform(lambda s: s.ewm(alpha=1/14, min_periods=10).mean())
ad = dn.groupby(df['ticker']).transform(lambda s: s.ewm(alpha=1/14, min_periods=10).mean())
df['rsi14'] = 100 - 100/(1 + au/ad.replace(0, np.nan))
df['ma50'] = g['close'].transform(lambda s: s.rolling(50, min_periods=30).mean())
df['ma20_slope'] = df['ma20']/g['ma20'].shift(5) - 1
df['hh60'] = g['high'].transform(lambda s: s.shift(1).rolling(60, min_periods=30).max())
df['ll60'] = g['low'].transform(lambda s: s.shift(1).rolling(60, min_periods=30).min())
df['hh120'] = g['high'].transform(lambda s: s.shift(1).rolling(120, min_periods=60).max())
df['pos60'] = (df['close']-df['ll60'])/(df['hh60']-df['ll60']).replace(0, np.nan)
# Bollinger width (squeeze)
sd20 = g['close'].transform(lambda s: s.rolling(20, min_periods=10).std())
df['bbw'] = 4*sd20/df['ma20']
df['bbw_min60'] = df.groupby('ticker')['bbw'].transform(lambda s: s.shift(1).rolling(60, min_periods=30).min())
# candle anatomy
body = (df['close']-df['open'])
rng = (df['high']-df['low']).replace(0, np.nan)
df['body_pct'] = body/pc
df['lower_wick'] = (df[['open','close']].min(axis=1)-df['low'])/df['close']
df['gap'] = df['open']/pc - 1
df['range_pct'] = (df['high']-df['low'])/pc
df['inside'] = (df['high'] <= g['high'].shift(1)) & (df['low'] >= g['low'].shift(1))
df['ret1'] = g['ret'].shift(1); df['ret2'] = g['ret'].shift(2)
df['vol_ratio1'] = g['vol_ratio'].shift(1)
df['red_streak'] = g['ret'].transform(lambda s: s.lt(0).astype(int).groupby((s>=0).cumsum()).cumsum())
# Accumulation proxies (Accumulation/Distribution: close location x volume)
clv = ((df['close']-df['low'])-(df['high']-df['close']))/rng
df['clv'] = clv.fillna(0)
df['adv'] = df['clv']*df['volume']
df['ad10'] = g['adv'].transform(lambda s: s.rolling(10, min_periods=5).sum())/(df['vol_ma10']*10).replace(0, np.nan)
df['ad20'] = g['adv'].transform(lambda s: s.rolling(20, min_periods=10).sum())/(df['vol_ma20']*20).replace(0, np.nan)
# up-volume / down-volume ratio over 10 days
upv = df['volume'].where(df['ret'] > 0, 0); dnv = df['volume'].where(df['ret'] < 0, 0)
df['udvr10'] = upv.groupby(df['ticker']).transform(lambda s: s.rolling(10, min_periods=5).sum()) / \
               dnv.groupby(df['ticker']).transform(lambda s: s.rolling(10, min_periods=5).sum()).replace(0, np.nan)
# OBV slope 10d vs price change 10d (rising OBV with flat price = quiet accumulation)
obv = (np.sign(df['ret'].fillna(0))*df['volume'])
df['obv'] = obv.groupby(df['ticker']).cumsum()
df['obv_chg10'] = (df['obv'] - g['obv'].shift(10))/(df['vol_ma20']*10).replace(0, np.nan)
df['gain10'] = df['close']/g['close'].shift(10) - 1
df['ara_in10'] = g['is_ara'].transform(lambda s: s.astype(int).shift(1).rolling(10, min_periods=1).sum())
df['big_in10'] = (df['ret'] >= 0.10).groupby(df['ticker']).transform(lambda s: s.astype(int).shift(1).rolling(10, min_periods=1).sum())
df['days_since_hh'] = 0
df['next_high'] = g['high'].shift(-1); df['next_low'] = g['low'].shift(-1); df['next_close'] = g['close'].shift(-1)
df.to_pickle('feat2.pkl')
print(df.shape)
