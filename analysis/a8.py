from bt import *
liq = (df['close'] >= 50) & (df['val_ma20'] >= 300e6) & (df['trade_days20'] >= 12) & df['ret'].notna()
df['dow'] = pd.to_datetime(df['date']).dt.dayofweek
hit_ara_fail = (df['high'] >= df['prev_close']*(1+df['ara_lim']*0.97)) & ~df['is_ara']
c = {
 "ARB tidak terkunci (beli close)": liq & df['is_arb'] & (df['high']>df['low']),
 "Sempat ARA tapi gagal (high=ARA, close<high)": liq & hit_ara_fail,
 "Saham likuid (>=10M/hari) naik 3-8% close pucuk": (df['close']>=50)&(df['val_ma20']>=10e9)&df['ret'].between(.03,.08)&(df['close_pos']>=.9),
 "Saham likuid turun 4-8% close di dasar": (df['close']>=50)&(df['val_ma20']>=10e9)&df['ret'].between(-.08,-.04)&(df['close_pos']<=.15),
 "Jumat: naik 3%-ARA close pucuk": liq & (df['dow']==4) & (df['ret']>=.03) & ~df['is_ara'] & (df['close_pos']>=.9),
 "Volume kering lalu ARA (dry5<0.7)": liq & df['is_ara'] & (df['dry5']<0.7) & (df['ara_streak']<=2),
 "Breakout high60? (close>hh20 & gain20<20% & ret 5%-ARA, vol2x)": liq & (df['close']>df['hh20']) & (df['gain20']<0.2) & (df['ret']>=0.05) & ~df['is_ara'] & (df['vol_ratio']>=2) & (df['close_pos']>=0.9),
}
for name, m in c.items():
    for tp, sl, h in [(0.03,0.04,3)]:
        print(f"{name:62} BSJP TRAIN {fmt(run_close(m,tp,sl,h,period=TRAIN))}")
        print(f"{'':62} BSJP TEST  {fmt(run_close(m,tp,sl,h,period=TEST))}")
        print(f"{'':62} OPEN TRAIN {fmt(run(m,0.05,0.05,2,period=TRAIN))}")
        print(f"{'':62} OPEN TEST  {fmt(run(m,0.05,0.05,2,period=TEST))}")
