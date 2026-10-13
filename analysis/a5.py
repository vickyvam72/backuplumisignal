from bt import *
liq = (df['close'] >= 50) & (df['val_ma20'] >= 300e6) & (df['trade_days20'] >= 12) & df['ret'].notna()
cands = {
 "ARA (tidak terkunci seharian), streak 1-2": liq & df['is_ara'] & (df['ara_streak'] <= 2),
 "Hampir ARA bukan ARA": liq & df['near_ara'] & ~df['is_ara'],
 "Naik 5%-ARA, close pucuk>=0.8, vol>=1.5": liq & (df['ret'] >= 0.05) & ~df['is_ara'] & (df['close_pos'] >= 0.8) & (df['vol_ratio'] >= 1.5),
 "Breakout20 + vol2x + close pucuk": liq & (df['close'] > df['hh20']) & (df['vol_ratio'] >= 2) & (df['close_pos'] >= 0.8) & (df['ret'] >= 0.03) & ~df['is_ara'],
 "Momentum gain5 30-80% + close pucuk": liq & df['gain5'].between(0.3, 0.8) & (df['close_pos'] >= 0.8) & (df['ret'] > 0) & ~df['is_ara'],
}
grid = [(tp, sl, h) for tp in (0.03, 0.05, 0.08, 0.12) for sl in (0.04, 0.06, 0.08) for h in (1, 2, 3)]
for name, m in cands.items():
    best = None
    for tp, sl, h in grid:
        r = run_close(m, tp, sl, h, period=TRAIN)
        if r['n'] >= 40 and (best is None or r['avg'] > best[0]['avg']):
            best = (r, tp, sl, h)
    r, tp, sl, h = best
    rt = run_close(m, tp, sl, h, period=TEST)
    print(f"\n{name}\n  exit terbaik(TRAIN): TP {tp*100:.0f}% SL {sl*100:.0f}% hold {h}\n  TRAIN {fmt(r)}\n  TEST  {fmt(rt)}")
