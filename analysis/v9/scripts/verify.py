import sys, pandas as pd, numpy as np
D=sys.argv[1]
T=pd.read_csv(D+'transaksi.csv'); C=pd.read_csv(D+'top5_gabungan.csv'); R=pd.read_csv(D+'ringkasan.csv'); K=pd.read_csv(D+'kurva.csv'); TR=pd.read_csv(D+'top5_ringkasan.csv')
def tick(p): return np.where(p<200,1,np.where(p<500,2,np.where(p<2000,5,np.where(p<5000,10,25))))
P=pd.read_csv('/home/claude/dl/market/tv_1y.csv'); P=P.set_index(['ticker','date']).sort_index()
def replay(r):
    x=P.loc[r.saham]; x=x[x.index>=r.tanggal_beli]
    e,tp,sl=r.harga_beli,r.tp,r.sl
    ci=float(P.loc[(r.saham,r.tanggal_sinyal)].close)
    o0=x.iloc[0].open; ef=round(o0/tick(o0))*tick(o0)
    intraday = ef > e + 1e-9   # bought at zone top during the day
    for k in range(min(7,len(x))):
        o,h,l,c=x.iloc[k][['open','high','low','close']]
        if k>0 and o<=sl: return 'SL',x.index[k]
        if k>0 and o>=tp: return 'TP',x.index[k]
        if c>=o:
            if l<=sl: return 'SL',x.index[k]
            if h>=tp: return 'TP',x.index[k]
        else:
            if not (k==0 and intraday) and h>=tp: return 'TP',x.index[k]
            if l<=sl: return 'SL',x.index[k]
        if k==6: return 'Waktu',x.index[k]
    return 'open',None
for nm,X in [('transaksi',T),('gabungan',C)]:
    print(nm, len(X), 'tp tick', ((X.tp % tick(X.tp))==0).mean(), 'sl tick', ((X.sl % tick(X.sl))==0).mean(), 'buy tick', ((X.harga_beli % tick(X.harga_beli))==0).mean())
    w=X[X.jenis=='winrate']; print('  winrate tp>=3', (w.tp_pct>=2.995).mean(), 'SL<=3TP (harga)', ((w.harga_beli-w.sl)<=3*(w.tp-w.harga_beli)+1e-9).mean())
    rt=(X.tp/X.harga_beli-1)*100; rs=(1-X.sl/X.harga_beli)*100
    print('  tp_pct==harga', (abs(rt-X.tp_pct)<0.006).mean(), 'sl_pct==harga', (abs(rs-X.sl_pct)<0.006).mean())
    tpx=X.keluar.str.startswith('TP'); slx=X.keluar.str.startswith('SL')
    print('  TP exits pl==tp', (abs(X.pl_pct[tpx]-X.tp_pct[tpx])<0.006).mean(), 'SL exits pl==-sl', (abs(X.pl_pct[slx]+X.sl_pct[slx])<0.006).mean(), X.keluar.str.split(' ').str[0].value_counts().to_dict())
    print('  price>60', (X.closing>60).mean(), 'hold<=7', (X.lama_hold<=7).mean())
    key=['jenis','tier']+(['id'] if nm=='transaksi' else [])
    bad=0
    for g,x in X.groupby(key+['saham']):
        x=x.sort_values('tanggal_beli'); b=x.tanggal_beli.values; s=x.tanggal_jual.values
        bad+=int((b[1:]<=s[:-1]).sum())
    print('  re-entry while held', bad, 'max/day', X.groupby(key+['tanggal_sinyal']).size().max())
    S=X.sample(300, random_state=1); ok=0; bads=[]
    for _,r in S.iterrows():
        k,d=replay(r)
        if k==r.keluar.split(' ')[0] and d==r.tanggal_jual: ok+=1
        else: bads.append((r.saham,r.tanggal_beli,r.keluar,r.tanggal_jual,k,d))
    print('  replay match', ok,'/300', bads[:3])
for (j,t,i),x in T.groupby(['jenis','tier','id']):
    r=R[(R.jenis==j)&(R.tier==t)&(R.id==i)].iloc[0]
    assert abs(x.pl_pct.sum()-r.akumulasi_pl)<0.6 and len(x)==r.trades, (j,t,i)
for (j,t),x in C.groupby(['jenis','tier']):
    r=TR[(TR.jenis==j)&(TR.tier==t)].iloc[0]; assert abs(x.pl_pct.sum()-r.akumulasi_pl)<0.6, (j,t)
    k=K[(K.jenis==j)&(K.tier==t)&(K.seri=='top5')]; assert abs(k.akumulasi_pl.iloc[-1]-r.akumulasi_pl)<0.6
print('ringkasan/kurva == jumlah P/L real transaksi: OK')
print('strategi 60 hari negatif:', len(R[R.uji_akumulasi_pl<0]), '| strategi total negatif:', len(R[R.akumulasi_pl<=0]))
print('kolom:', [c for c in R.columns if any(z in c for z in ['pesimis','harga_buka','sebelum','dibuka'])])
