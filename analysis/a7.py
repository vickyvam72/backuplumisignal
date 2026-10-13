from bt import *
liq = (df['close'] >= 50) & (df['val_ma20'] >= 300e6) & (df['trade_days20'] >= 12) & df['ret'].notna()

def simulate_limit(t, i, lim_mult, tp, sl, hold):
    """Signal at close of bar i; next session place a buy limit at close*lim_mult (fills if low<=limit)."""
    g = by[t]
    if i + 1 >= len(g): return None
    lim = g.at[i, 'close'] * lim_mult
    o, h, l, c = g.at[i+1,'open'], g.at[i+1,'high'], g.at[i+1,'low'], g.at[i+1,'close']
    if l > lim: return None
    e = min(o, lim)
    tpp, slp = e*(1+tp), e*(1-sl)
    # entry bar: after fill, conservative: SL first
    if l <= slp: return -sl - FEE
    if h >= tpp and o <= lim: return tp - FEE      # filled at open, then rallied
    last = c
    for k in range(i+2, min(i+1+hold, len(g))):
        o, h, l, c = g.at[k,'open'], g.at[k,'high'], g.at[k,'low'], g.at[k,'close']
        if o <= slp: return o/e-1-FEE
        if o >= tpp: return o/e-1-FEE
        if l <= slp: return -sl - FEE
        if h >= tpp: return tp - FEE
        last = c
    return last/e - 1 - FEE

def run_limit(mask, lim, tp, sl, hold, period):
    s = df[mask & df['date'].isin(period)]
    a = np.array([r for r in (simulate_limit(t, idx[(t,dt)], lim, tp, sl, hold) for t,dt in zip(s['ticker'], s['date'])) if r is not None])
    if len(a)==0: return dict(n=0)
    w,l = a[a>0].sum(), -a[a<=0].sum()
    return dict(n=len(a), win=(a>0).mean()*100, avg=a.mean()*100, tot=a.sum()*100, pf=w/l if l>0 else np.inf)

ara = liq & df['is_ara'] & (df['ara_streak']<=2)
print("== Beli BESOK dengan limit, untuk saham yang ARA hari ini ==")
for lim in (1.00, 0.97, 0.94, 0.90):
    for tp, sl, h in [(0.05,0.05,2),(0.08,0.06,3)]:
        print(f"limit {lim:.2f}x close, TP{tp*100:.0f} SL{sl*100:.0f} H{h}  TRAIN {fmt(run_limit(ara, lim, tp, sl, h, TRAIN))}")
        print(f"{'':37}TEST  {fmt(run_limit(ara, lim, tp, sl, h, TEST))}")

print("\n== Top 5 per hari (peringkat) untuk ARA BSJP, TP3 SL4 H3 ==")
df['score_fresh'] = -df['gain20'].fillna(0) - 0.05*df['vol_ratio'].fillna(0)
for name, m, col in [("ARA streak1-2, peringkat ARA paling segar", ara, 'score_fresh')]:
    print(name, "TRAIN", fmt(run_close(m, .03, .04, 3, period=TRAIN, rank_col=col, top=5)))
    print(name, "TEST ", fmt(run_close(m, .03, .04, 3, period=TEST, rank_col=col, top=5)))
# how many ARA candidates per day
cnt = df[ara].groupby('date').size()
print("\nKandidat ARA per hari: median", cnt.median(), "| hari tanpa ARA:", len(set(df['date'].unique())-set(cnt.index)), "dari", df['date'].nunique())
