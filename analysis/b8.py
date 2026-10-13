import pandas as pd, numpy as np, json
from sim2 import prep, simulate, stats
df = pd.read_pickle('feat2.pkl'); df, F = prep(df)
dates = sorted(df['date'].unique()); TEST = set(dates[-60:]); TRAIN = set(dates[:-60]); D = df['date']
base = (df['close'] >= 50) & (df['val_ma20'] >= 2e8) & (df['trade_days20'] >= 12) & ~df['is_arb'] & df['ret'].notna() & ~df['is_ara']
squeeze = df['bbw'] <= df['bbw_min60']*1.15
atrp = (df['atr14']/df['close']).clip(.02, .20)
S = {
 1: ('Pantulan Panik', base & (df['ret'] <= -.03) & (df['close_pos'] <= .35) & (df['rsi14'] < 40), 'pre5'),
 2: ('Squeeze Akumulasi', base & squeeze & (df['udvr10'] > 2), 'pre0'),
 3: ('Bangun dari Dasar', base & (df['pos60'] < .4) & (df['ret'] >= .03), 'pre1'),
 4: ('Volume Kering', base & (df['dry5'] < .7) & (df['gain20'] < .15) & (df['ret'] > 0), 'pre1'),
 5: ('Rehat Setelah Lonjakan', base & (df['big_in10'] >= 1) & (df['vol_ratio'] < .8) & (df['ret'].abs() < .03), 'pre2'),
}
ranks = {
 'value': df['val_ma20'], 'rsi_low': -df['rsi14'], 'drop': -df['ret'], 'udvr': df['udvr10'], 'ad20': df['ad20'],
 'volratio': df['vol_ratio'], 'ret': df['ret'], 'dry': -df['dry5'], 'cpos': df['close_pos'], 'low_pos60': -df['pos60'],
 'atr': atrp,
}
exitsets = {'fix12/7/7': (.12,.07,7), 'fix10/6/5': (.10,.06,5), 'fix8/5/5': (.08,.05,5),
            'atr2/1.2/7': ('atr',2.0,1.2,7), 'atr1.5/1/5': ('atr',1.5,1.0,5), 'atr2.5/1.5/7': ('atr',2.5,1.5,7)}
def ex(spec):
    if spec[0] == 'atr':
        _, a, b, h = spec
        return (atrp*a).clip(.05,.20).to_numpy(), (atrp*b).clip(.04,.10).to_numpy(), h
    return spec
def topk(m, rk, k=5):
    s = df.loc[m, ['date']].assign(r=rk[m]).sort_values('r', ascending=False).groupby('date').head(k)
    out = np.zeros(len(df), bool); out[s.index] = True; return out
final = {}
for sid,(nm,m,mode) in S.items():
    best=None
    for rn, rk in ranks.items():
        mk = topk(m, rk)
        for en, spec in exitsets.items():
            tp, sl, h = ex(spec)
            a = stats(simulate(df,F,mk,mode,tp,sl,h),D,TRAIN)
            if a.get('n',0) < 25: continue
            p = stats(simulate(df,F,mk,mode,tp,sl,h,path='pess'),D,TRAIN)
            sc = min(a['avg'],p['avg'])*np.sqrt(a['n'])
            if best is None or sc>best[0]: best=(sc,rn,en,a,p)
    _,rn,en,a,p = best
    mk = topk(m, ranks[rn]); tp,sl,h = ex(exitsets[en])
    r = simulate(df,F,mk,mode,tp,sl,h); rp = simulate(df,F,mk,mode,tp,sl,h,path='pess')
    b=stats(r,D,TEST); d=stats(rp,D,TEST)
    sig_tr = mk[D.isin(TRAIN).to_numpy()].sum()
    fill = a['n']/sig_tr*100
    # monthly (all year, ohlc)
    mon = pd.DataFrame({'m':D.str[:7],'r':r}).dropna().groupby('m')['r'].agg(['size','mean'])
    pos_months = int((mon['mean']>0).sum())
    print(f"S{sid} {nm:24} {mode} rank={rn:9} exit={en:12}| fill {fill:4.1f}% | TRAIN n{a['n']} win{a['win']} avg{a['avg']:+.2f} PF{a['pf']} pess{p['avg']:+.2f} | TEST n{b.get('n')} win{b.get('win')} avg{b.get('avg'):+.2f} PF{b.get('pf')} pess{d.get('avg'):+.2f} | bulan+ {pos_months}/{len(mon)}")
    final[sid]=dict(name=nm,mode=mode,rank=rn,exit=en,fill=round(fill,1),train=a,train_pess=p,test=b,test_pess=d,
                    monthly={k:[int(v['size']),round(v['mean']*100,2)] for k,v in mon.iterrows()})
    np.save(f'res_S{sid}.npy', r)
json.dump(final,open('b8.json','w'),indent=1,default=str)
