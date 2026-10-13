from bt import *
d = df[(df['close']>=50)&(df['val_ma20']>=300e6)&(df['trade_days20']>=12)&df['ret'].notna()&df['next_open'].notna()].copy()
g = d.groupby('ticker')
d['gap'] = d['next_open']/d['close']-1
nc = df.groupby('ticker')['close'].shift(-1).loc[d.index]
nh = df.groupby('ticker')['high'].shift(-1).loc[d.index]
d['intra'] = nc/d['next_open']-1
d['next_high_from_close'] = nh/d['close']-1
def show(name, m):
    s=d[m]
    print(f"{name:46} n={len(s):5} gap {s['gap'].mean()*100:+5.2f}% (positif {(s['gap']>0).mean()*100:4.1f}%) | open->close {s['intra'].mean()*100:+5.2f}% | high besok vs close {s['next_high_from_close'].median()*100:+5.2f}% (median)")
show("Semua", d.index==d.index)
show("ARA", d['is_ara'])
show("ARA streak1", d['is_ara']&(d['ara_streak']==1))
show("Hampir ARA bukan ARA", d['near_ara']&~d['is_ara'])
show("Naik 5-10% close pucuk>=0.8", d['ret'].between(.05,.10)&(d['close_pos']>=.8))
show("Naik 2-5% close pucuk>=0.8", d['ret'].between(.02,.05)&(d['close_pos']>=.8))
show("Breakout20 vol2x pucuk", (d['close']>d['hh20'])&(d['vol_ratio']>=2)&(d['close_pos']>=.8)&~d['is_ara'])
show("Gain5 30-80% pucuk", d['gain5'].between(.3,.8)&(d['close_pos']>=.8)&(d['ret']>0)&~d['is_ara'])
show("Turun 3-10% di tren naik vol<1x", (d['gain20']>.3)&d['ret'].between(-.1,-.03)&(d['vol_ratio']<1))
show("Close di dasar range (pos<0.2) turun>3%", (d['close_pos']<.2)&(d['ret']<-.03))
