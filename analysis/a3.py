from bt import *
liq = (df['close'] >= 50) & (df['val_ma20'] >= 300e6) & (df['trade_days20'] >= 12) & df['ret'].notna()
cands = {
 "ARA (streak 1-2)": liq & df['is_ara'] & (df['ara_streak'] <= 2),
 "Hampir ARA bukan ARA": liq & df['near_ara'] & ~df['is_ara'],
 "Breakout20 + vol2x + close pucuk": liq & (df['close'] > df['hh20']) & (df['vol_ratio'] >= 2) & (df['close_pos'] >= 0.8) & (df['ret'] >= 0.03) & ~df['is_ara'],
 "Momentum gain5 30-80% + close pucuk": liq & df['gain5'].between(0.3, 0.8) & (df['close_pos'] >= 0.8) & (df['ret'] > 0) & ~df['is_ara'],
 "Rebound anjlok (gain20<-20%, naik>3%, vol1.5x)": liq & (df['gain20'] < -0.2) & (df['ret'] > 0.03) & (df['vol_ratio'] >= 1.5) & (df['close_pos'] >= 0.7),
 "Pullback tren naik (gain20>30%, turun 3-10%, vol<1x)": liq & (df['gain20'] > 0.3) & df['ret'].between(-0.10, -0.03) & (df['vol_ratio'] < 1),
 "S1 lama: naik 2%-ARA, close>=0.7, vol>=1.5, >MA20": liq & (df['ret'] >= 0.02) & ~df['is_ara'] & (df['close_pos'] >= 0.7) & (df['vol_ratio'] >= 1.5) & (df['dist_ma20'] > 0),
}
grid = [(tp, sl, h) for tp in (0.05, 0.08, 0.12, 0.20) for sl in (0.05, 0.08) for h in (1, 3, 5)]
for name, m in cands.items():
    best = None
    for tp, sl, h in grid:
        r = run(m, tp, sl, h, period=TRAIN)
        if r['n'] >= 40 and (best is None or r['tot'] / r['n'] > best[0]['tot'] / best[0]['n']):
            best = (r, tp, sl, h)
    if best is None: print(name, "terlalu sedikit sinyal"); continue
    r, tp, sl, h = best
    rt = run(m, tp, sl, h, period=TEST)
    print(f"\n{name}\n  exit terbaik(TRAIN): TP {tp*100:.0f}% SL {sl*100:.0f}% hold {h}\n  TRAIN {fmt(r)}\n  TEST  {fmt(rt)}")
