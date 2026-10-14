"""Daily features, market regime, strategy atoms, exits and ranks.

A line-for-line port of the backtest pipeline (prep.py, feat.py, feat2.py, feat3.py, engine.py and the
winrate exits of x1.py) packed into one function, so the live radar and the backtest give the same answer
for the same data.
"""
import numpy as np
import pandas as pd

from sim import prep, tick

TIERS = {'semua': 0, '250jt': 2.5e8, '1M': 1e9, '5M': 5e9, '20M': 2e10}
TIER_LABEL = {'semua': 'Tanpa minimal', '250jt': '≥ Rp250 juta', '1M': '≥ Rp1 miliar', '5M': '≥ Rp5 miliar', '20M': '≥ Rp20 miliar'}


def build_features(raw):
    """raw: DataFrame date,ticker,open,high,low,close,volume (one row per stock per session)."""
    df = raw[['date', 'ticker', 'open', 'high', 'low', 'close', 'volume']].copy()
    for c in ['open', 'high', 'low', 'close', 'volume']:
        df[c] = pd.to_numeric(df[c], errors='coerce')
    df = df.dropna(subset=['close', 'open', 'high', 'low'])
    df = df.sort_values(['ticker', 'date']).reset_index(drop=True)
    df['volume'] = df['volume'].fillna(0)

    # ---- feat.py
    df = df.dropna(subset=['close']).sort_values(['ticker', 'date']).reset_index(drop=True)
    g = df.groupby('ticker', group_keys=False)
    pc = g['close'].shift(1)
    df['prev_close'] = pc
    df['ret'] = df['close'] / pc - 1
    df['ara_lim'] = np.where(pc <= 200, .35, np.where(pc <= 5000, .25, .20))
    df.loc[df['ret'].abs() > df['ara_lim'] + 0.02, 'ret'] = np.nan   # splits / data errors
    df['value'] = df['close'] * df['volume']
    rng = (df['high'] - df['low']).replace(0, np.nan)
    df['close_pos'] = ((df['close'] - df['low']) / rng).fillna(0.5)
    df['upper_wick'] = (df['high'] - df[['open', 'close']].max(axis=1)) / df['close']
    df['is_ara'] = (df['ret'] >= df['ara_lim'] * 0.88) & (df['close'] >= df['high'] - 1e-9)
    df['near_ara'] = (df['ret'] >= df['ara_lim'] * 0.70) & (df['close_pos'] >= 0.85)
    df['is_arb'] = (df['ret'] <= -df['ara_lim'] * 0.88) | ((df['ret'] <= -0.14) & (df['close'] <= df['low'] + 1e-9))
    for n in (5, 10, 20):
        df[f'vol_ma{n}'] = g['volume'].transform(lambda s: s.shift(1).rolling(n, min_periods=max(3, n // 2)).mean())
        df[f'val_ma{n}'] = g['value'].transform(lambda s: s.shift(1).rolling(n, min_periods=max(3, n // 2)).mean())
        df[f'hh{n}'] = g['high'].transform(lambda s: s.shift(1).rolling(n, min_periods=max(3, n // 2)).max())
        df[f'll{n}'] = g['low'].transform(lambda s: s.shift(1).rolling(n, min_periods=max(3, n // 2)).min())
        df[f'ma{n}'] = g['close'].transform(lambda s: s.rolling(n, min_periods=max(3, n // 2)).mean())
    df['vol_ratio'] = df['volume'] / df['vol_ma20'].replace(0, np.nan)
    df['dry5'] = df['vol_ma5'] / df['vol_ma20'].replace(0, np.nan)
    df['gain5'] = df['close'] / g['close'].shift(5) - 1
    df['gain20'] = df['close'] / g['close'].shift(20) - 1
    df['range20'] = df['hh20'] / df['ll20'] - 1
    df['dist_ma20'] = df['close'] / df['ma20'] - 1
    df['trade_days20'] = g['volume'].transform(lambda s: (s > 0).astype(int).shift(1).rolling(20, min_periods=10).sum())
    tr = pd.concat([df['high'] - df['low'], (df['high'] - pc).abs(), (df['low'] - pc).abs()], axis=1).max(axis=1)
    df['atr14'] = tr.groupby(df['ticker']).transform(lambda s: s.rolling(14, min_periods=7).mean())
    df['up_streak'] = g['ret'].transform(lambda s: s.gt(0).astype(int).groupby((s <= 0).cumsum()).cumsum())

    # ---- feat2.py
    g = df.groupby('ticker', group_keys=False)
    pc = df['prev_close']
    d = g['close'].diff()
    up, dn = d.clip(lower=0), (-d).clip(lower=0)
    au = up.groupby(df['ticker']).transform(lambda s: s.ewm(alpha=1 / 14, min_periods=10).mean())
    ad = dn.groupby(df['ticker']).transform(lambda s: s.ewm(alpha=1 / 14, min_periods=10).mean())
    df['rsi14'] = 100 - 100 / (1 + au / ad.replace(0, np.nan))
    df['ma50'] = g['close'].transform(lambda s: s.rolling(50, min_periods=30).mean())
    df['ma20_slope'] = df['ma20'] / g['ma20'].shift(5) - 1
    df['hh60'] = g['high'].transform(lambda s: s.shift(1).rolling(60, min_periods=30).max())
    df['ll60'] = g['low'].transform(lambda s: s.shift(1).rolling(60, min_periods=30).min())
    df['pos60'] = (df['close'] - df['ll60']) / (df['hh60'] - df['ll60']).replace(0, np.nan)
    sd20 = g['close'].transform(lambda s: s.rolling(20, min_periods=10).std())
    df['bbw'] = 4 * sd20 / df['ma20']
    df['bbw_min60'] = df.groupby('ticker')['bbw'].transform(lambda s: s.shift(1).rolling(60, min_periods=30).min())
    df['lower_wick'] = (df[['open', 'close']].min(axis=1) - df['low']) / df['close']
    df['gap'] = df['open'] / pc - 1
    df['inside'] = (df['high'] <= g['high'].shift(1)) & (df['low'] >= g['low'].shift(1))
    df['red_streak'] = g['ret'].transform(lambda s: s.lt(0).astype(int).groupby((s >= 0).cumsum()).cumsum())
    rng = (df['high'] - df['low']).replace(0, np.nan)
    clv = ((df['close'] - df['low']) - (df['high'] - df['close'])) / rng
    df['clv'] = clv.fillna(0)
    df['adv'] = df['clv'] * df['volume']
    df['ad20'] = g['adv'].transform(lambda s: s.rolling(20, min_periods=10).sum()) / (df['vol_ma20'] * 20).replace(0, np.nan)
    upv = df['volume'].where(df['ret'] > 0, 0)
    dnv = df['volume'].where(df['ret'] < 0, 0)
    df['udvr10'] = upv.groupby(df['ticker']).transform(lambda s: s.rolling(10, min_periods=5).sum()) / \
        dnv.groupby(df['ticker']).transform(lambda s: s.rolling(10, min_periods=5).sum()).replace(0, np.nan)
    df['big_in10'] = (df['ret'] >= 0.10).groupby(df['ticker']).transform(lambda s: s.astype(int).shift(1).rolling(10, min_periods=1).sum())

    # ---- feat3.py
    g = df.groupby('ticker', group_keys=False)
    df['ma5'] = g['close'].transform(lambda s: s.rolling(5, min_periods=3).mean())
    df['ma5_prev'] = g['ma5'].shift(1)
    df['ma20_prev'] = g['ma20'].shift(1)
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
    df['pullback_up'] = (df['ma20'] > df['ma50']) & (df['ma20_slope'] > 0) & df['dist_ma20'].between(-.05, .0) & (df['ret'] < .02)
    df['gain60'] = df['close'] / g['close'].shift(60) - 1
    return df


class Context:
    """Everything the strategies need, computed once per data set (engine.py + x1.py)."""

    def __init__(self, feat):
        df, F = prep(feat)
        self.df, self.F = df, F
        self.dates = sorted(df['date'].unique())
        self.DI = {d: i for i, d in enumerate(self.dates)}
        D = df['date']
        self.D = D
        self.di = D.map(self.DI).to_numpy()
        self.tick_all = df['ticker'].to_numpy()

        # market regime (equal-weight liquid universe)
        liq = (df['close'] > 60) & (df['val_ma20'] >= 2e8) & df['ret'].notna()
        mk = df[liq].groupby('date').agg(ew_ret=('ret', 'mean'))
        mk['breadth'] = df[liq].assign(a=(df['close'] > df['ma20'])).groupby('date')['a'].mean()
        mk['ew_idx'] = (1 + mk['ew_ret'].clip(-.2, .2)).cumprod()
        mk['ew_ma20'] = mk['ew_idx'].rolling(20, min_periods=10).mean()
        mk['ew_ret5'] = mk['ew_idx'] / mk['ew_idx'].shift(5) - 1
        mk['breadth_ma5'] = mk['breadth'].rolling(5, min_periods=3).mean()
        mk['br5'] = mk['breadth'].rolling(5, min_periods=3).mean()
        mk['chg'] = mk['br5'] - mk['br5'].shift(10)
        self.mk = mk
        b = D.map(mk['breadth_ma5']).to_numpy()
        r5 = D.map(mk['ew_ret5']).to_numpy()
        chg = D.map(mk['chg']).to_numpy()
        guard = ~(chg < -.15)
        self.REGS = {'all': guard, 'ret': (r5 > -.03) & guard, 'br30': (b >= .30) & guard, 'br50': (b >= .50) & guard,
                     'weak30': b < .30, 'weak20': b < .20}

        self.base_core = ((df['close'] > 60) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna() & ~df['is_ara']
                          & (df['val_ma20'] > 0))
        squeeze = df['bbw'] <= df['bbw_min60'] * 1.15
        up = (df['ma20'] > df['ma50'])
        self.A = {
            'uptrend': up & (df['close'] > df['ma50']), 'downtrend': ~up,
            'red': df['ret'] < 0, 'down3': df['ret'] <= -.03, 'green': df['ret'] > 0, 'up3': df['ret'] >= .03,
            'upstreak3': df['up_streak'] >= 3, 'red2': df['red_streak'] >= 2,
            'rsi<35': df['rsi14'] < 35, 'rsi<30': df['rsi14'] < 30, 'rsi<40': df['rsi14'] < 40,
            'volhi': df['vol_ratio'] >= 2, 'vollo': df['vol_ratio'] < .8, 'dry5': df['dry5'] < .7,
            'squeeze': squeeze, 'cpos_hi': df['close_pos'] >= .8, 'cpos_lo': df['close_pos'] <= .35, 'lwick': df['lower_wick'] >= .03,
            'udvr2': df['udvr10'] > 2, 'pos60_lo': df['pos60'] < .3, 'big10': df['big_in10'] >= 1, 'inside': df['inside'],
            'near_ma20': df['dist_ma20'].between(-.03, .02), 'gapdown_today': df['gap'] <= -.03,
            'p<200': df['close'] < 200, 'p>=200': df['close'] >= 200,
            'nr7': df['nr7'], 'hl_rise': df['hl_rise'], 'reversal': df['reversal'], 'engulf': df['engulf'], 'pullback_up': df['pullback_up'],
            'gain60_lo': df['gain60'] < -.2, 'above_ma50': df['above_ma50'],
        }
        atrp = (df['atr14'] / df['close']).clip(.015, .25).to_numpy()
        self.atrp = atrp
        tk = tick(df['close'].to_numpy())
        close = df['close'].to_numpy()
        self.close = close
        low5 = np.fmin(df['ll5'].to_numpy(), df['low'].to_numpy()) - tk
        low10 = np.fmin(df['ll10'].to_numpy(), df['low'].to_numpy()) - tk
        hh20 = df['hh20'].to_numpy()
        hh60 = df['hh60'].to_numpy()
        a = close * atrp
        res = np.where(hh20 >= close + a, hh20, np.where(hh60 >= close + a, hh60, close + 2.5 * a))
        self.low5, self.low10, self.res = low5, low10, res
        EX = {
            'atr2/1.5': dict(tp=np.clip(2.0 * atrp, .04, .30), sl=np.clip(1.5 * atrp, .03, .12), hold=7),
            'atr3/1.5': dict(tp=np.clip(3.0 * atrp, .05, .30), sl=np.clip(1.5 * atrp, .03, .12), hold=7),
            'atr2.5/2': dict(tp=np.clip(2.5 * atrp, .05, .30), sl=np.clip(2.0 * atrp, .03, .12), hold=7),
            'low5/res': dict(tp=.1, sl=.07, hold=7, sl_px=low5, tp_px=res, sl_cap=(.03, .12), tp_cap=(.04, .30)),
            'low10/res': dict(tp=.1, sl=.07, hold=7, sl_px=low10, tp_px=res, sl_cap=(.03, .12), tp_cap=(.04, .30)),
            'low5/3atr': dict(tp=np.clip(3.0 * atrp, .05, .30), sl=.07, hold=7, sl_px=low5, sl_cap=(.03, .12)),
        }
        for tp in (.03, .04, .05):
            for sl in (.04, .05, .06, .08, .10):
                if sl > 3 * tp + 1e-9:
                    continue
                for h in (3, 5, 7):
                    EX[f'tp{int(tp * 100)}/sl{int(sl * 100)}/{h}h'] = dict(tp=tp, sl=sl, hold=h, max_ratio=3)
        for k in (.75, 1.0, 1.25):
            for m_ in (1.5, 2.0, 2.5):
                _tp = np.clip(k * atrp, .03, .12)
                EX[f'tp{k}atr/sl{m_}atr/7h'] = dict(tp=_tp, sl=np.minimum(np.clip(m_ * atrp, .04, .12), 3 * _tp), hold=7, max_ratio=3)
            for nm, px in (('low5', low5), ('low10', low10)):
                EX[f'tp{k}atr/{nm}/7h'] = dict(tp=np.clip(k * atrp, .03, .12), sl=.08, hold=7, sl_px=px, sl_cap=(.04, .10), max_ratio=3)
        self.EXITS = EX
        self.RANKS = {'value': df['val_ma20'].to_numpy(dtype=float), 'volratio': df['vol_ratio'].to_numpy(dtype=float),
                      'rsi_low': (-df['rsi14']).to_numpy(dtype=float), 'lowpos': (-df['pos60'].fillna(1)).to_numpy(dtype=float)}

    def tier_mask(self, t):
        return (self.base_core & (self.df['val_ma20'] >= TIERS[t])).to_numpy()

    def mask_of(self, cond, tier):
        m = pd.Series(self.tier_mask(tier), index=self.df.index)
        for a in cond.split(' & '):
            m &= self.A[a]
        return m.to_numpy()
