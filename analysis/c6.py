import pandas as pd, numpy as np, json
exec(open('c3.py').read().split("o = pd.read_csv")[0])
STRATS = [
 dict(id=1, name='Oversold + Volume Beli', cond='rsi<35 & udvr2 & p<200', rank='udvr', exit='f12/7/7'),
 dict(id=2, name='Pantulan Hijau', cond='up3 & rsi<35 & p<200', rank='rsi_low', exit='sLL5/12/7'),
 dict(id=3, name='Naik Pelan dari Dasar', cond='upstreak3 & pos60_lo & inside', rank='lowpos', exit='sLL5/12/7'),
 dict(id=4, name='Squeeze Naik Beruntun', cond='squeeze & upstreak3 & pos60_lo', rank='volratio', exit='sLL5/12/7'),
 dict(id=5, name='Squeeze di MA20', cond='squeeze & near_ma20 & p<200', rank='volratio', exit='f12/7/7'),
]
c0 = df['close'].to_numpy(); o1=F[('open',1)]; l1=F[('low',1)]
top = np.floor(c0*1.01/tick(c0*1.01))*tick(c0*1.01); top=np.maximum(top,c0)
entry = np.where(o1<=top, o1, top)
nd = df.groupby('ticker')['date'].shift(-1).to_numpy()
Q = np.array_split(np.array(dates), 4)
report = {'period': [dates[0], dates[-1]], 'n_days': len(dates), 'test_from': dates[-60], 'strategies': []}
allr = []
for s in STRATS:
    m = base.copy()
    for a in s['cond'].split(' & '): m &= A[a]
    mk = topk(m, ranks[s['rank']])
    kw = exits[s['exit']]
    r = simulate(df,F,mk,'zone1',**kw); rp = simulate(df,F,mk,'zone1',path='pess',**kw)
    sl_used = np.clip(1-kw['sl_px']/entry, .03, .10) if 'sl_px' in kw else np.full(len(df), kw['sl'])
    ok = np.isfinite(r)
    t = pd.DataFrame({'date':D[ok].values,'buy_date':nd[ok],'ticker':df['ticker'][ok].values,'close':c0[ok],'entry':entry[ok],
                      'tp':entry[ok]*(1+kw['tp']),'sl':entry[ok]*(1-sl_used[ok]),'pnl':r[ok]*100,'pnl_pess':rp[ok]*100}).sort_values('date')
    def why(row):
        g = row.pnl/100 + .004
        if abs(g - kw['tp']) < 1e-6: return 'TP'
        if g <= -(row.entry - row.sl)/row.entry + 1e-6 and g < 0 and abs(g + (row.entry-row.sl)/row.entry) < 1e-6: return 'SL'
        if g < -(row.entry - row.sl)/row.entry: return 'Gap SL'
        if g > kw['tp']: return 'Gap TP'
        return f"Waktu ({kw['hold']} hari)"
    t['exit'] = t.apply(why, axis=1)
    allr.append(t.assign(strategy=s['id']))
    cum = t['pnl'].cumsum(); dd = (cum - cum.cummax()).min()
    daily = t.groupby('date')['pnl'].sum().cumsum()
    mon = t.assign(m=t.date.str[:7]).groupby('m').agg(n=('pnl','size'), win=('pnl', lambda x: round((x>0).mean()*100,1)), pl=('pnl','sum'))
    tr, te = t[t.date < dates[-60]], t[t.date >= dates[-60]]
    sig_days = pd.Series(D[mk]).nunique()
    def st(x, col='pnl'):
        a = x[col].to_numpy()
        if len(a)==0: return dict(n=0)
        w=a[a>0]; l=a[a<=0]
        return dict(n=len(a), win=round((a>0).mean()*100,1), avg=round(a.mean(),2), total=round(a.sum(),1),
                    avg_win=round(w.mean(),2) if len(w) else 0, avg_loss=round(l.mean(),2) if len(l) else 0,
                    pf=round(w.sum()/-l.sum(),2) if l.sum()<0 else None)
    rec = dict(s, all=st(t), all_pess=st(t,'pnl_pess'), train=st(tr), test=st(te), test_pess=st(te,'pnl_pess'),
               maxdd=round(dd,1), signals=int(mk.sum()), fill_pct=round(len(t)/mk.sum()*100,1),
               trades_per_day=round(len(t)/len(dates),2),
               quarters=[round(t[t.date.isin(set(q))]['pnl_pess'].sum(),1) for q in Q],
               exits=t['exit'].value_counts().to_dict(),
               monthly=[dict(m=k, n=int(v.n), win=v.win, pl=round(v.pl,1)) for k,v in mon.iterrows()],
               curve=[[d, round(v,1)] for d,v in daily.items()],
               recent=t.tail(15)[['date','ticker','close','entry','tp','sl','pnl','exit']].round(1).to_dict('records'))
    report['strategies'].append(rec)
    a=rec['all']; p=rec['all_pess']
    print(f"S{s['id']} {s['name']:24} n{a['n']} win{a['win']}% total{a['total']:+.1f}% avg{a['avg']:+.2f}% PF{a['pf']} DD{rec['maxdd']} | pess total{p['total']:+.1f} | test n{rec['test']['n']} win{rec['test']['win']} total{rec['test']['total']:+.1f} | fill {rec['fill_pct']}% | exits {rec['exits']}")
A_ = pd.concat(allr)
cum = A_.sort_values('date').groupby('date')['pnl'].sum().cumsum()
report['combined'] = dict(n=len(A_), win=round((A_.pnl>0).mean()*100,1), total=round(A_.pnl.sum(),1), curve=[[d,round(v,1)] for d,v in cum.items()],
                          maxdd=round((cum-cum.cummax()).min(),1))
print('GABUNGAN', report['combined']['n'], report['combined']['win'], report['combined']['total'], report['combined']['maxdd'])
# baseline: random 5 per day from the base universe with same entry/exit
rng = np.random.default_rng(0)
bm = base.to_numpy() & np.isfinite(o1)
idx = pd.Series(np.arange(len(df))[bm]).groupby(D[bm].values).apply(lambda s: s.sample(min(5,len(s)), random_state=1)).values
mk = np.zeros(len(df),bool); mk[idx]=True
rb = simulate(df,F,mk,'zone1',.12,.07,7); b=stats(rb)
report['baseline'] = dict(n=b['n'], win=b['win'], total=b['tot'], avg=b['avg'])
print('BASELINE acak 5/hari', b)
json.dump(report, open('report.json','w'), default=str)
A_.to_csv('trades_all.csv', index=False)
