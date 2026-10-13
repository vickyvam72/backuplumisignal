import pandas as pd, numpy as np
df = pd.read_pickle('feat.pkl')
dates = sorted(df['date'].unique())
TEST = set(dates[-60:]); TRAIN = set(dates[:-60])
FEE = 0.004
# per-ticker arrays for fast forward simulation
by = {t: g.reset_index(drop=True) for t, g in df.groupby('ticker')}
idx = {}
for t, g in by.items():
    for i, dt in enumerate(g['date']): idx[(t, dt)] = i

def simulate(t, i, tp, sl, hold, gap_max=0.06):
    """Signal at close of bar i. Buy next open; exit on TP/SL/hold. Returns pnl or None (no fill)."""
    g = by[t]
    if i + 1 >= len(g): return None
    e = g.at[i+1, 'open']; c0 = g.at[i, 'close']
    hi, lo = g.at[i+1, 'high'], g.at[i+1, 'low']
    if not e or e <= 0: return None
    if hi == lo and e >= c0 * 1.15: return None          # locked limit-up: no seller, cannot buy
    if e > c0 * (1 + gap_max): return None                # gapped too far: skip (don't chase)
    tpp, slp = e * (1 + tp), e * (1 - sl)
    for k in range(i + 1, min(i + 1 + hold, len(g))):
        h, l, c = g.at[k, 'high'], g.at[k, 'low'], g.at[k, 'close']
        if l <= slp: return -sl - FEE
        if h >= tpp: return tp - FEE
        last = c
    return last / e - 1 - FEE

def run(mask, tp, sl, hold, rank_col=None, top=5, gap_max=0.06, period=None):
    s = df[mask]
    if period is not None: s = s[s['date'].isin(period)]
    if rank_col is not None:
        s = s.sort_values(rank_col, ascending=False).groupby('date').head(top)
    res = []
    for t, dt in zip(s['ticker'], s['date']):
        r = simulate(t, idx[(t, dt)], tp, sl, hold, gap_max)
        if r is not None: res.append(r)
    a = np.array(res)
    if len(a) == 0: return dict(n=0)
    wins, losses = a[a > 0].sum(), -a[a <= 0].sum()
    return dict(n=len(a), win=(a > 0).mean() * 100, avg=a.mean() * 100, tot=a.sum() * 100,
                pf=wins / losses if losses > 0 else np.inf)

def fmt(r):
    if r['n'] == 0: return "n=0"
    return f"n={r['n']:4} win={r['win']:5.1f}% avg={r['avg']:+5.2f}% total={r['tot']:+7.1f}% PF={r['pf']:.2f}"

def simulate_close(t, i, tp, sl, hold):
    """Buy at the close of bar i (pre-closing), sell with TP/SL from the next session; exit at close after `hold`."""
    g = by[t]
    if i + 1 >= len(g): return None
    e = g.at[i, 'close']
    o0, h0, l0 = g.at[i, 'open'], g.at[i, 'high'], g.at[i, 'low']
    if h0 == l0: return None                      # locked the whole day (no trading range): cannot buy
    tpp, slp = e * (1 + tp), e * (1 - sl)
    last = e
    for k in range(i + 1, min(i + 1 + hold, len(g))):
        o, h, l, c = g.at[k, 'open'], g.at[k, 'high'], g.at[k, 'low'], g.at[k, 'close']
        if o >= tpp: return o / e - 1 - FEE       # gap above target: sold at the open
        if o <= slp: return o / e - 1 - FEE       # gap below stop: sold at the open
        if l <= slp: return -sl - FEE
        if h >= tpp: return tp - FEE
        last = c
    return last / e - 1 - FEE

def run_close(mask, tp, sl, hold, period=None, rank_col=None, top=5):
    s = df[mask]
    if period is not None: s = s[s['date'].isin(period)]
    if rank_col is not None:
        s = s.sort_values(rank_col, ascending=False).groupby('date').head(top)
    res = [r for r in (simulate_close(t, idx[(t, dt)], tp, sl, hold) for t, dt in zip(s['ticker'], s['date'])) if r is not None]
    a = np.array(res)
    if len(a) == 0: return dict(n=0)
    wins, losses = a[a > 0].sum(), -a[a <= 0].sum()
    return dict(n=len(a), win=(a > 0).mean() * 100, avg=a.mean() * 100, tot=a.sum() * 100, pf=wins / losses if losses > 0 else np.inf)
