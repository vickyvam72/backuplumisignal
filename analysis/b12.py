import pandas as pd, numpy as np
df = pd.read_pickle('feat2.pkl')
dates = sorted(df['date'].unique()); L60 = set(dates[-61:-1])
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & df['ret'].notna() & df['next_ret'].notna()
d = df[base].copy()
d['nb'] = d['next_ret'] >= .10
d['gap_next'] = d['next_open']/d['close']-1
A = {
 'ARA hari ini': d['is_ara'],
 'Naik ≥8% (bukan ARA)': (d['ret']>=.08)&~d['is_ara'],
 'Pernah naik ≥10% dalam 10 hari': d['big_in10']>=1,
 'Volume ≥3x rata-rata': d['vol_ratio']>=3,
 'Breakout high 20 hari': d['close']>d['hh20'],
 'Squeeze (Bollinger menyempit)': d['bbw']<=d['bbw_min60']*1.15,
 'Volume naik dominan (U/D >2)': d['udvr10']>2,
 'Volume kering 5 hari': d['dry5']<.7,
 'Di dasar 60 hari (posisi <40%)': d['pos60']<.4,
 'Harga < 200': d['close']<200,
 'Turun ≥3% & tutup di bawah, RSI<40': (d['ret']<=-.03)&(d['close_pos']<=.35)&(d['rsi14']<40),
 'Harga ≥ 1000': d['close']>=1000,
}
p0 = d['nb'].mean()
print(f"base P(next ≥10%) = {p0*100:.2f}%  | 60 hari: {d[d.date.isin(L60)]['nb'].mean()*100:.2f}%")
for k,m in A.items():
    s=d[m]; s60=s[s.date.isin(L60)]
    print(f"{k:38} n{len(s):6}  P={s['nb'].mean()*100:5.2f}% lift {s['nb'].mean()/p0:4.1f}x | 60h P={s60['nb'].mean()*100:5.2f}% | dari gainers 60h: {int(s60['nb'].sum())}")
g = d[d.date.isin(L60) & d['nb']]
print('total gainer-days 60h (likuid):', len(g), ' — open gap distribution of those up days:')
print((g['gap_next']).describe().round(3))
print('share where next open gap >=5%:', round((g['gap_next']>=.05).mean()*100,1))
