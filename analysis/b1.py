import pandas as pd, numpy as np, itertools
from sim2 import prep, simulate, stats, fmt
df = pd.read_pickle('feat2.pkl')
df, F = prep(df)
dates = sorted(df['date'].unique()); TEST = set(dates[-60:]); TRAIN = set(dates[:-60])
D = df['date']
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna()

conds = {
 'all': base,
 'ara': base & df['is_ara'],
 'not_ara_up5+': base & ~df['is_ara'] & (df['ret'] >= .05),
 'strong_close_up3-10': base & (df['ret'].between(.03, .10)) & (df['close_pos'] >= .8),
 'vol_spike3_up': base & (df['vol_ratio'] >= 3) & (df['ret'] > .02) & ~df['is_ara'],
 'pullback_ma20_uptrend': base & (df['ma20'] > df['ma50']) & (df['dist_ma20'].between(-.03, .02)) & (df['ret'] < 0),
 'rsi<30': base & (df['rsi14'] < 30),
 'rsi<25_hammer': base & (df['rsi14'] < 30) & (df['lower_wick'] > .03) & (df['close_pos'] > .6),
 'drop>8': base & (df['ret'] <= -.08),
 'drop>8_closepos>.5': base & (df['ret'] <= -.08) & (df['close_pos'] > .5),
 'red3_after_rally': base & (df['red_streak'] >= 3) & (df['gain20'] > .2),
 'pullback_after_ara': base & (df['ara_in10'] >= 1) & (df['ret'] < 0) & (df['vol_ratio'] < 1),
 'squeeze_breakout': base & (df['bbw'] <= df['bbw_min60'] * 1.1) & (df['close'] > df['hh20']),
 'breakout20_vol2': base & (df['close'] > df['hh20']) & (df['vol_ratio'] >= 2) & ~df['is_ara'],
 'breakout60': base & (df['close'] > df['hh60']) & ~df['is_ara'],
 'quiet_acc': base & (df['obv_chg10'] > 2) & (df['gain10'].abs() < .05),
 'ad20_high_flat': base & (df['ad20'] > .3) & (df['gain20'].abs() < .08),
 'inside_after_big': base & df['inside'] & (df['ret1'] >= .1),
 'gap_down_recover': base & (df['gap'] <= -.03) & (df['close'] > df['prev_close']),
 'upper_wick_big': base & (df['upper_wick'] > .05) & (df['ret'] > 0),
 'near_ara': base & df['near_ara'] & ~df['is_ara'],
 'low_pos60_volspike': base & (df['pos60'] < .2) & (df['vol_ratio'] >= 3) & (df['ret'] > 0),
}
modes = ['open', 'lim0', 'lim2', 'lim4']
rows = []
for name, m in conds.items():
    for mode in modes:
        for tp, sl, hold in [(.03, .03, 1), (.05, .04, 3), (.08, .05, 5), (.04, .04, 2)]:
            r = simulate(df, F, m, mode, tp, sl, hold); rp = simulate(df, F, m, mode, tp, sl, hold, path='pess'); c = stats(rp, D, TRAIN); d2 = stats(rp, D, TEST)
            a, b = stats(r, D, TRAIN), stats(r, D, TEST)
            rows.append((name, mode, tp, sl, hold, a.get('n', 0), a.get('win'), a.get('avg'), a.get('pf'), b.get('n', 0), b.get('win'), b.get('avg'), b.get('pf'), c.get('avg'), d2.get('avg')))
out = pd.DataFrame(rows, columns=['cond', 'mode', 'tp', 'sl', 'hold', 'n_tr', 'win_tr', 'avg_tr', 'pf_tr', 'n_te', 'win_te', 'avg_te', 'pf_te', 'pess_tr', 'pess_te'])
pd.set_option('display.width', 250); pd.set_option('display.max_rows', 500)
best = out.sort_values('avg_tr', ascending=False).groupby('cond').head(2)
print(best.sort_values('avg_tr', ascending=False).to_string())
out.to_csv('b1.csv', index=False)
