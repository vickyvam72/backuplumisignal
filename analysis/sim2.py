"""Vectorised next-morning simulator.

Signal at close of day t (known after 16:30). Entry on day t+1 morning:
  mode 'open'       : buy at the open (pre-opening IEP)
  mode 'lim{k}'     : limit buy at close*(1-k%), rounded to tick. Filled if day t+1 low <= limit
                      (at open if open <= limit). Unfilled = no trade.
Exit: TP / SL on the entry price, conservative: if a bar touches both, SL counts first.
On the entry day itself the TP/SL check uses the entry-day high/low too (SL first).
Time exit at the close of the `hold`-th session (entry day = session 1).
Locked limit-up open (high == low on t+1, gap >= 15%) -> no fill.
Fees 0.4% round trip.
"""
import numpy as np, pandas as pd
FEE = 0.004

def tick(p):
    return np.where(p < 200, 1, np.where(p < 500, 2, np.where(p < 2000, 5, np.where(p < 5000, 10, 25))))

def prep(df, H=7):
    df = df.sort_values(['ticker', 'date']).reset_index(drop=True)
    g = df.groupby('ticker')
    F = {}
    for k in range(1, H + 1):
        for c in ('open', 'high', 'low', 'close'):
            F[(c, k)] = g[c].shift(-k).to_numpy()
    return df, F

def simulate(df, F, mask, mode='open', tp=0.05, sl=0.04, hold=3, max_gap=None, path='ohlc'):
    m = np.asarray(mask)
    c0 = df['close'].to_numpy()
    o1, h1, l1 = F[('open', 1)], F[('high', 1)], F[('low', 1)]
    if mode == 'open':
        e = o1.copy(); filled = ~np.isnan(o1)
    elif mode.startswith('pre'):
        # order queued in pre-opening at close*(1-k): fills only if the opening price is at/below it
        k = float(mode[3:]) / 100
        lim = c0 * (1 - k)
        lim = np.floor(lim / tick(lim)) * tick(lim)
        filled = o1 <= lim
        e = o1.copy()
    else:
        k = float(mode[3:]) / 100
        lim = c0 * (1 - k)
        lim = np.floor(lim / tick(lim)) * tick(lim)
        filled = l1 <= lim
        e = np.where(o1 <= lim, o1, lim)
    locked = (h1 == l1) & (o1 >= c0 * 1.15)
    ok = m & filled & ~locked & (e > 0)
    if max_gap is not None:
        ok &= (o1 / c0 - 1) <= max_gap
    tp = np.broadcast_to(np.asarray(tp, dtype=float), e.shape)
    sl = np.broadcast_to(np.asarray(sl, dtype=float), e.shape)
    tpp, slp = e * (1 + tp), e * (1 - sl)
    res = np.full(len(df), np.nan)
    done = ~ok
    for k in range(1, hold + 1):
        o, h, l, c = F[('open', k)], F[('high', k)], F[('low', k)], F[('close', k)]
        avail = ~np.isnan(c)
        act = ~done & avail
        if k > 1:  # gaps beyond stops on later days fill at the open
            gsl = act & (o <= slp); res[gsl] = o[gsl] / e[gsl] - 1; done |= gsl; act &= ~gsl
            gtp = act & (o >= tpp); res[gtp] = o[gtp] / e[gtp] - 1; done |= gtp; act &= ~gtp
        if path == 'pess':
            hs = act & (l <= slp); res[hs] = -sl[hs]; done |= hs; act &= ~hs
            if k == 1 and mode.startswith('lim'):
                ht = act & (c >= tpp); res[ht] = c[ht] / e[ht] - 1
            else:
                ht = act & (h >= tpp); res[ht] = tp[ht]
            done |= ht; act &= ~ht
        else:
            # OHLC path model: green bar = O->L->H->C, red bar = O->H->L->C
            bull = c >= o
            fillday_bear = (k == 1) & (mode.startswith('lim')) & ~bull   # high printed before the fill
            # bullish: low first
            hs = act & bull & (l <= slp); res[hs] = -sl[hs]; done |= hs; act &= ~hs
            ht = act & bull & (h >= tpp); res[ht] = tp[ht]; done |= ht; act &= ~ht
            # bearish: high first (unless the fill came after the high)
            ht = act & ~bull & ~fillday_bear & (h >= tpp); res[ht] = tp[ht]; done |= ht; act &= ~ht
            hs = act & ~bull & (l <= slp); res[hs] = -sl[hs]; done |= hs; act &= ~hs
        if k == hold:
            res[act] = c[act] / e[act] - 1; done |= act
        # data ran out (most recent signals): leave open -> NaN
    res = res - FEE
    res[~ok] = np.nan
    return res

def stats(r, dates=None, period=None):
    r = pd.Series(r)
    if period is not None:
        r = r[dates.isin(period).to_numpy()]
    a = r.dropna().to_numpy()
    if len(a) == 0: return dict(n=0)
    w, l = a[a > 0].sum(), -a[a <= 0].sum()
    return dict(n=len(a), win=round((a > 0).mean() * 100, 1), avg=round(a.mean() * 100, 2),
                tot=round(a.sum() * 100, 1), pf=round(w / l, 2) if l > 0 else 99)

def fmt(s):
    if s['n'] == 0: return 'n=0'
    return f"n={s['n']:5} win={s['win']:5.1f}% avg={s['avg']:+6.2f}% tot={s['tot']:+8.1f}% PF={s['pf']:.2f}"
