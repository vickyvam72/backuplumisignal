import sys; sys.argv=['x','/tmp/m1_unused/']
exec(open('t5.py').read().split("for tier in TIERS:")[0])
bm = pd.Series(df['mkt_breadth_ma5'].to_numpy(), index=df.index)
mkd = mk.copy(); mkd['br5'] = mkd['breadth'].rolling(5, min_periods=3).mean()
mkd['br_chg10'] = mkd['br5'] - mkd['br5'].shift(10)
mkd['ew_ma10'] = mkd['ew_idx'].rolling(10, min_periods=5).mean()
mkd['ew_below10'] = mkd['ew_idx'] < mkd['ew_ma10']
mkd['ew_ret10'] = mkd['ew_idx'] / mkd['ew_idx'].shift(10) - 1
for c in ['br_chg10', 'ew_ret10']: df['mkt_' + c] = D.map(mkd[c]).to_numpy()
df['mkt_below10'] = D.map(mkd['ew_below10']).to_numpy().astype(bool)
chg = df['mkt_br_chg10'].to_numpy(); r10 = df['mkt_ew_ret10'].to_numpy(); below = df['mkt_below10'].to_numpy()
GUARDS = {
 'brchg>-.15': ~(chg < -.15), 'brchg>-.20': ~(chg < -.20), 'brchg>-.25': ~(chg < -.25),
 'brchg>-.10': ~(chg < -.10),
}
S6V = {'weak<30': b < .30, 'weak<20': b < .20, 'weak<15': b < .15}
REG0 = dict(REG)
res = []
for gname, g in GUARDS.items():
    for s6n, s6m in S6V.items():
        REG['ret'] = REG0['ret'] & g; REG['br30'] = REG0['br30'] & g; REG['br50'] = REG0['br50'] & g; REG['weak'] = s6m
        for tier in TIERS:
            s = combined(tier, 'expanding')
            mon = None
            res.append((gname, s6n, tier, s['n'], s['total'], s['maxdd'], s['worst_month'], s['q']))
o = pd.DataFrame(res, columns=['guard', 's6', 'tier', 'n', 'total', 'dd', 'worst', 'q'])
o.to_pickle('m1.pkl')
pv = o.pivot_table(index=['guard', 's6'], columns='tier', values='total')[list(TIERS)]
pd2 = o.pivot_table(index=['guard', 's6'], columns='tier', values='dd')[list(TIERS)]
pd.set_option('display.width', 250)
print(pv.round(0).to_string()); print(pd2.round(0).to_string())
