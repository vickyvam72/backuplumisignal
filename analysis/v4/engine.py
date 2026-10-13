"""Strategy engine v2: top-5 per day, never re-recommend a ticker that is still held,
adaptive (per-stock) TP/SL, price > 60."""
import pandas as pd, numpy as np
from sim2 import prep, simulate, tick

df = pd.read_pickle('feat3.pkl'); df, F = prep(df)
dates = sorted(df['date'].unique()); DI = {d: i for i, d in enumerate(dates)}
D = df['date']; di = D.map(DI).to_numpy()
TEST = set(dates[-60:]); TRAIN = set(dates[:-60])
Q = [set(q) for q in np.array_split(np.array(dates), 4)]

# market regime (equal-weight liquid universe)
liq = (df['close'] > 60) & (df['val_ma20'] >= 2e8) & df['ret'].notna()
mk = df[liq].groupby('date').agg(breadth=('close', lambda s: np.nan), ew_ret=('ret', 'mean'))
mk['breadth'] = df[liq].assign(a=(df['close'] > df['ma20'])).groupby('date')['a'].mean()
mk['ew_idx'] = (1 + mk['ew_ret'].clip(-.2, .2)).cumprod()
mk['ew_ma20'] = mk['ew_idx'].rolling(20, min_periods=10).mean()
mk['ew_ret5'] = mk['ew_idx'] / mk['ew_idx'].shift(5) - 1
mk['ew_ret20'] = mk['ew_idx'] / mk['ew_idx'].shift(20) - 1
mk['breadth_ma5'] = mk['breadth'].rolling(5, min_periods=3).mean()
for c in ['breadth', 'breadth_ma5', 'ew_ret5', 'ew_ret20']:
    df['mkt_' + c] = D.map(mk[c]).to_numpy()
df['mkt_up'] = D.map(mk['ew_idx'] > mk['ew_ma20']).to_numpy().astype(bool)

base_core = (df['close'] > 60) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna() & ~df['is_ara'] & (df['val_ma20'] > 0)
TIERS = {'semua': 0, '250jt': 2.5e8, '1M': 1e9, '5M': 5e9, '20M': 2e10}
TIER_LABEL = {'semua': 'Tanpa minimal', '250jt': '≥ Rp250 juta', '1M': '≥ Rp1 miliar', '5M': '≥ Rp5 miliar', '20M': '≥ Rp20 miliar'}
def tier_mask(t): return (base_core & (df['val_ma20'] >= TIERS[t])).to_numpy()
base = base_core & (df['val_ma20'] >= 2e8)
squeeze = df['bbw'] <= df['bbw_min60'] * 1.15
up = (df['ma20'] > df['ma50'])
A = {
 'uptrend': up & (df['close'] > df['ma50']), 'downtrend': ~up,
 'red': df['ret'] < 0, 'down3': df['ret'] <= -.03, 'green': df['ret'] > 0, 'up3': df['ret'] >= .03,
 'upstreak3': df['up_streak'] >= 3, 'red2': df['red_streak'] >= 2,
 'rsi<35': df['rsi14'] < 35, 'rsi<40': df['rsi14'] < 40, 'rsi50-70': df['rsi14'].between(50, 70),
 'volhi': df['vol_ratio'] >= 2, 'vollo': df['vol_ratio'] < .8, 'dry5': df['dry5'] < .7,
 'brk20': df['close'] > df['hh20'], 'tight20': df['range20'] < .2, 'squeeze': squeeze,
 'cpos_hi': df['close_pos'] >= .8, 'cpos_lo': df['close_pos'] <= .35, 'lwick': df['lower_wick'] >= .03,
 'udvr2': df['udvr10'] > 2, 'ad20+': df['ad20'] > .15,
 'pos60_lo': df['pos60'] < .3, 'big10': df['big_in10'] >= 1, 'inside': df['inside'],
 'near_ma20': df['dist_ma20'].between(-.03, .02), 'gapdown_today': df['gap'] <= -.03,
 'p<200': df['close'] < 200, 'p>=200': df['close'] >= 200,
 'mkt_up': df['mkt_up'], 'mkt_down': ~df['mkt_up'],
 'mkt_weak': df['mkt_breadth_ma5'] < .35, 'mkt_ok': df['mkt_breadth_ma5'] >= .35,
 'cross5_20': df['cross5_20'], 'reclaim_ma50': df['reclaim_ma50'], 'new_hh60': df['new_hh60'], 'nr7': df['nr7'],
 'hl_rise': df['hl_rise'], 'reversal': df['reversal'], 'engulf': df['engulf'], 'pullback_up': df['pullback_up'],
 'vol_up_close_hi': df['vol_up_close_hi'], 'above_ma50': df['above_ma50'], 'rsi40-60': df['rsi14'].between(40, 60),
 'gain60_lo': df['gain60'] < -.2, 'gain60_hi': df['gain60'] > .3, 'p>=500': df['close'] >= 500, 'rsi<30': df['rsi14'] < 30,
 'volhi3': df['vol_ratio'] >= 3, 'tight10': (df['hh10'] / df['ll10'] - 1) < .1,
}
atrp = (df['atr14'] / df['close']).clip(.015, .25).to_numpy()
tk = tick(df['close'].to_numpy())
close = df['close'].to_numpy()
low5 = np.fmin(df['ll5'].to_numpy(), df['low'].to_numpy()) - tk
low10 = np.fmin(df['ll10'].to_numpy(), df['low'].to_numpy()) - tk
hh20 = df['hh20'].to_numpy(); hh60 = df['hh60'].to_numpy()

def resistance(min_gap_atr=1.0, fallback_atr=2.5):
    """Nearest swing resistance above the close (20d high, else 60d high) at least
    `min_gap_atr` ATR away; otherwise close + fallback ATR."""
    a = close * atrp
    r = np.where(hh20 >= close + min_gap_atr * a, hh20, np.where(hh60 >= close + min_gap_atr * a, hh60, close + fallback_atr * a))
    return r

EXITS = {
 # volatility based: TP / SL as multiples of the stock's own ATR
 'atr2/1.5': dict(tp=np.clip(2.0 * atrp, .04, .30), sl=np.clip(1.5 * atrp, .03, .12), hold=7),
 'atr3/1.5': dict(tp=np.clip(3.0 * atrp, .05, .30), sl=np.clip(1.5 * atrp, .03, .12), hold=7),
 'atr2.5/2': dict(tp=np.clip(2.5 * atrp, .05, .30), sl=np.clip(2.0 * atrp, .03, .12), hold=7),
 # structure: SL under the 5- or 10-day low, TP at the nearest resistance
 'low5/res': dict(tp=.1, sl=.07, hold=7, sl_px=low5, tp_px=resistance(), sl_cap=(.03, .12), tp_cap=(.04, .30)),
 'low10/res': dict(tp=.1, sl=.07, hold=7, sl_px=low10, tp_px=resistance(), sl_cap=(.03, .12), tp_cap=(.04, .30)),
 # structure, but never wider than 2 ATR (the tighter of the two)
 'low5|2atr/res': dict(tp=.1, sl=.07, hold=7, sl_px=np.fmax(low5, close * (1 - 2 * atrp)), tp_px=resistance(), sl_cap=(.03, .12), tp_cap=(.04, .30)),
 'low5/3atr': dict(tp=np.clip(3.0 * atrp, .05, .30), sl=.07, hold=7, sl_px=low5, sl_cap=(.03, .12)),
}
RANKS = {'value': df['val_ma20'], 'volratio': df['vol_ratio'], 'ret': df['ret'], 'rsi_low': -df['rsi14'],
         'udvr': df['udvr10'].fillna(0), 'atr': pd.Series(atrp), 'lowpos': -df['pos60'].fillna(1), 'cpos': df['close_pos']}

_cache = {}
def outcomes(exit_name, mode='zone1', path='ohlc'):
    key = (exit_name, mode, path)
    if key not in _cache:
        _cache[key] = simulate(df, F, np.ones(len(df), bool), mode, path=path, return_exit=True, **EXITS[exit_name])
    return _cache[key]

def exit_di(exk):
    """Global session index of the exit day, counted on the stock's own trading rows."""
    n = len(df); j = np.arange(n) + np.maximum(exk, 0)
    j = np.minimum(j, n - 1)
    same = tick_all[j] == tick_all
    return np.where((exk > 0) & same, di[j], -1)

tick_all = df['ticker'].to_numpy()

def run(mask, rank, exit_name, top=5, mode='zone1', path='ohlc', no_rehold=True):
    """Day by day: rank today's candidates, skip tickers whose position is still open,
    take the top `top`. Returns DataFrame of trades (filled only) and recommended count."""
    res, exk, e, tp, sl = outcomes(exit_name, mode, path)
    m = np.asarray(mask) & np.isfinite(np.asarray(RANKS[rank], dtype=float))
    idx = np.where(m)[0]
    if len(idx) == 0: return pd.DataFrame(), 0
    rk = np.asarray(RANKS[rank], dtype=float)[idx]
    order = np.lexsort((-rk, di[idx]))
    idx = idx[order]
    tick_arr = df['ticker'].to_numpy()
    held = {}
    ex_di = exit_di(exk)
    out = []; rec = 0
    cur = -1; taken = 0
    for i in idx:
        d = di[i]
        if d != cur: cur, taken = d, 0
        if taken >= top: continue
        t = tick_arr[i]
        if no_rehold and held.get(t, -1) > d: continue
        taken += 1; rec += 1
        if np.isfinite(res[i]):
            held[t] = ex_di[i]
            out.append(i)
    out = np.array(out, dtype=int)
    tr = pd.DataFrame({'i': out, 'date': D.to_numpy()[out], 'ticker': tick_arr[out], 'close': close[out], 'entry': e[out],
                       'tp': e[out] * (1 + tp[out]), 'sl': e[out] * (1 - sl[out]), 'tp_pct': tp[out] * 100, 'sl_pct': sl[out] * 100,
                       'pnl': res[out] * 100, 'hold': exk[out]})
    return tr, rec

def summarize(tr):
    if len(tr) == 0: return dict(n=0)
    a = tr['pnl'].to_numpy(); w, l = a[a > 0], a[a <= 0]
    cum = np.cumsum(a)
    return dict(n=len(a), win=round((a > 0).mean() * 100, 1), avg=round(a.mean(), 2), total=round(a.sum(), 1),
                pf=round(w.sum() / -l.sum(), 2) if l.sum() < 0 else 99, maxdd=round((cum - np.maximum.accumulate(cum)).min(), 1),
                q=[round(tr[tr.date.isin(q)]['pnl'].sum(), 1) for q in Q],
                months_neg100=int((tr.groupby(tr.date.str[:7])['pnl'].sum() < -100).sum()),
                worst_month=round(tr.groupby(tr.date.str[:7])['pnl'].sum().min(), 1))

def mask_of(cond, tier=None):
    m = base.copy() if tier is None else pd.Series(tier_mask(tier), index=df.index)
    for a in cond.split(' & '): m &= A[a]
    return m
