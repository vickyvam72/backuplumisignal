import pandas as pd, numpy as np
df = pd.read_csv('/home/claude/dl/market/yahoo_1y.csv')
for c in ['open','high','low','close','volume']:
    df[c] = pd.to_numeric(df[c], errors='coerce')
df = df.dropna(subset=['close','open','high','low'])
df = df.sort_values(['ticker','date']).reset_index(drop=True)
# Drop non-trading filler rows (Yahoo repeats a stale price with zero volume)
df['volume'] = df['volume'].fillna(0)
g = df.groupby('ticker', group_keys=False)
df['prev_close'] = g['close'].shift(1)
df['ret'] = df['close']/df['prev_close'] - 1
# IDX auto rejection limits
def ara_frac(p):
    return np.where(p<=200, .35, np.where(p<=5000, .25, .20))
df['ara_lim'] = ara_frac(df['prev_close'].fillna(df['close']))
bad = (df['ret'].abs() > df['ara_lim'] + 0.02)  # splits/data errors (also skips new-listing quirks)
print("rows", len(df), "suspicious jumps", int(bad.sum()))
df['bad'] = bad
df.to_parquet('bars.parquet') if False else df.to_pickle('bars.pkl')
dates = sorted(df['date'].unique()); print("sessions", len(dates), dates[0], dates[-1])
print(df.groupby('date').size().tail(5))
