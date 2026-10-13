from bt import *
liq = (df['close'] >= 50) & (df['val_ma20'] >= 300e6) & (df['trade_days20'] >= 12) & df['ret'].notna()
ara = liq & df['is_ara'] & (df['ara_streak'] <= 2)
c = {
 # ---- ARA family (BSJP)
 "ARA streak1": liq & df['is_ara'] & (df['ara_streak']==1),
 "ARA streak2": liq & df['is_ara'] & (df['ara_streak']==2),
 "ARA streak>=3": liq & df['is_ara'] & (df['ara_streak']>=3),
 "ARA, gain20<30% (ARA segar)": ara & (df['gain20']<0.3),
 "ARA, gain20>=30% (sudah lari)": ara & (df['gain20']>=0.3),
 "ARA, vol>=3x": ara & (df['vol_ratio']>=3),
 "ARA, vol<3x": ara & (df['vol_ratio']<3),
 "ARA, buka di bawah 5% (open<prev*1.05)": ara & (df['open'] < df['prev_close']*1.05),
 "ARA, buka gap >=5%": ara & (df['open'] >= df['prev_close']*1.05),
 "ARA, harga<200": ara & (df['prev_close']<=200),
 "ARA, harga>=200": ara & (df['prev_close']>200),
 # ---- strong close non-ARA
 "Naik>=15% close==high (bukan ARA)": liq & (df['ret']>=0.15) & ~df['is_ara'] & (df['close_pos']>=0.99),
 "Naik 10-15% close==high": liq & df['ret'].between(0.10,0.15) & ~df['is_ara'] & (df['close_pos']>=0.99),
 "Gain5 30-80% pucuk": liq & df['gain5'].between(0.3, 0.8) & (df['close_pos'] >= 0.8) & (df['ret'] > 0) & ~df['is_ara'],
 "Gain5>50% pucuk naik>3%": liq & (df['gain5']>0.5) & (df['close_pos'] >= 0.8) & (df['ret'] > 0.03) & ~df['is_ara'],
 "Kemarin ARA, hari ini naik>0 close pucuk (bukan ARA)": liq & df.groupby('ticker')['is_ara'].shift(1).fillna(False).astype(bool) & (df['ret']>0) & (df['close_pos']>=0.8) & ~df['is_ara'],
}
for tp, sl, h in [(0.03,0.04,3),(0.05,0.05,2)]:
    print(f"\n===== BSJP: beli close, TP {tp*100:.0f}% SL {sl*100:.0f}% hold {h} =====")
    for name, m in c.items():
        r, rt = run_close(m, tp, sl, h, period=TRAIN), run_close(m, tp, sl, h, period=TEST)
        print(f"{name:52} TRAIN {fmt(r)}\n{'':52} TEST  {fmt(rt)}")
