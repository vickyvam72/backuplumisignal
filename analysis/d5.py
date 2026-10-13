from engine import *
import json
b = df['mkt_breadth_ma5'].to_numpy(); r5 = df['mkt_ew_ret5'].to_numpy()
F_RET = r5 > -.03; F_BR30 = b >= .30; F_BR50 = b >= .50; WEAK = b < .30
STR = [
 dict(id=1, name='Oversold + Volume Beli', cond='rsi<35 & udvr2 & p<200', rank='udvr', exit='atr2.5/2', reg=F_RET,
      syarat='RSI14 di bawah 35; volume hari naik lebih dari 2x volume hari turun (10 hari); harga 61-199',
      ranking='Rasio volume naik/turun tertinggi', pasar='Aktif bila pasar tidak turun lebih dari 3% dalam 5 hari',
      tpsl='TP = 2,5 x ATR, SL = 2 x ATR dari harga beli (SL 3-12%), maks 7 hari'),
 dict(id=2, name='Pantulan Hijau', cond='up3 & rsi<35 & p<200', rank='rsi_low', exit='atr2/1.5', reg=F_RET,
      syarat='Naik 3% atau lebih hari ini; RSI14 di bawah 35; harga 61-199', ranking='RSI paling rendah',
      pasar='Aktif bila pasar tidak turun lebih dari 3% dalam 5 hari', tpsl='TP = 2 x ATR, SL = 1,5 x ATR (SL 3-12%), maks 7 hari'),
 dict(id=3, name='Naik Pelan dari Dasar', cond='upstreak3 & pos60_lo & inside', rank='lowpos', exit='low10/res', reg=F_BR30,
      syarat='Naik 3 hari beruntun; posisi di 30% terbawah range 60 hari; inside day', ranking='Posisi paling dekat dasar',
      pasar='Aktif bila minimal 30% saham di atas MA20', tpsl='SL di bawah low 10 hari, TP di resisten terdekat (high 20/60 hari), maks 7 hari'),
 dict(id=4, name='Squeeze Naik Beruntun', cond='squeeze & upstreak3 & pos60_lo', rank='volratio', exit='low5/3atr', reg=F_RET,
      syarat='Bollinger menyempit; naik 3 hari beruntun; posisi di 30% terbawah range 60 hari', ranking='Lonjakan volume tertinggi',
      pasar='Aktif bila pasar tidak turun lebih dari 3% dalam 5 hari', tpsl='SL di bawah low 5 hari, TP = 3 x ATR, maks 7 hari'),
 dict(id=5, name='Squeeze di MA20', cond='squeeze & near_ma20 & p<200', rank='volratio', exit='low5/res', reg=F_BR50,
      syarat='Bollinger menyempit; harga dekat MA20 (-3% s.d. +2%); harga 61-199', ranking='Lonjakan volume tertinggi',
      pasar='Aktif bila minimal 50% saham di atas MA20', tpsl='SL di bawah low 5 hari, TP di resisten terdekat (high 20/60 hari), maks 7 hari'),
 dict(id=6, name='Pantulan Pasar Panik', cond='lwick & gapdown_today', rank='volratio', exit='atr2.5/2', reg=WEAK,
      syarat='Dibuka gap turun 3% atau lebih; ekor bawah panjang (3% atau lebih)', ranking='Lonjakan volume tertinggi',
      pasar='Hanya aktif bila kurang dari 30% saham di atas MA20 (pasar lemah)', tpsl='TP = 2,5 x ATR, SL = 2 x ATR (SL 3-12%), maks 7 hari'),
]
TE = set(dates[-60:])
allt=[]; summ=[]
for s in STR:
    m = mask_of(s['cond']).to_numpy() & s['reg']
    tr, rec = run(m, s['rank'], s['exit']); trp,_ = run(m, s['rank'], s['exit'], path='pess')
    # old version (no market filter, fixed 12/7) for comparison
    EXITS['FIX'] = dict(tp=.12, sl=.07, hold=7)
    old,_ = run(mask_of(s['cond']).to_numpy(), s['rank'], 'FIX') if s['id']<=5 else (pd.DataFrame(),0)
    def why(r):
        g = r.pnl/100 + .004
        if abs(g - r.tp_pct/100) < 1e-6: return 'TP'
        if abs(g + r.sl_pct/100) < 1e-6: return 'SL'
        if g < -r.sl_pct/100: return 'Gap SL'
        if g > r.tp_pct/100: return 'Gap TP'
        return 'Waktu (7 hari)'
    tr['keluar'] = tr.apply(why, axis=1)
    nd = df.groupby('ticker')['date'].shift(-1).to_numpy()
    tr['tanggal_beli'] = nd[tr.i.values]
    S_ = summarize(tr); P_ = summarize(trp); te = summarize(tr[tr.date.isin(TE)]); O_ = summarize(old) if len(old) else dict(n=0)
    a = tr.pnl
    summ.append(dict(id=s['id'], strategi=s['name'], syarat=s['syarat'], ranking=s['ranking'], pasar=s['pasar'], exit=s['tpsl'],
        trades=S_['n'], winrate=S_['win'], akumulasi_pl=S_['total'], rata_pl=S_['avg'], rata_menang=round(a[a>0].mean(),2), rata_kalah=round(a[a<=0].mean(),2),
        profit_factor=S_['pf'], max_drawdown=S_['maxdd'], bulan_terburuk=S_['worst_month'], akumulasi_pl_pesimis=P_['total'],
        uji_trades=te.get('n',0), uji_winrate=te.get('win'), uji_akumulasi_pl=te.get('total'),
        tp_rata=round(tr.tp_pct.mean(),1), sl_rata=round(tr.sl_pct.mean(),1),
        lama_pl=O_.get('total'), lama_dd=O_.get('maxdd'), lama_bulan_terburuk=O_.get('worst_month'),
        q1=S_['q'][0], q2=S_['q'][1], q3=S_['q'][2], q4=S_['q'][3], direkomendasikan=rec))
    tr['id']=s['id']; tr['strategi']=s['name']; allt.append(tr)
    print(f"S{s['id']} {s['name']:24} n{S_['n']} win{S_['win']} tot{S_['total']:+.1f} DD{S_['maxdd']} worstM{S_['worst_month']} pess{P_['total']:+.1f} q{S_['q']} | 60h n{te.get('n')} {te.get('total')} | TP~{tr.tp_pct.mean():.1f}% SL~{tr.sl_pct.mean():.1f}% | lama {O_.get('total')} DD{O_.get('maxdd')} worst{O_.get('worst_month')}")
T = pd.concat(allt).sort_values(['date','id'])
# duplicates check: same ticker held in same strategy simultaneously
dup = 0
for sid,g in T.groupby('id'):
    g = g.sort_values('date')
    held = {}
    for _,r in g.iterrows():
        d = DI[r.date]
        if held.get(r.ticker,-1) > d: dup += 1
        held[r.ticker] = d + r.hold
print('duplikat hold:', dup, ' harga<=60:', int((T.close<=60).sum()))
cum = T.groupby('date')['pnl'].sum().cumsum()
mon = T.groupby(T.date.str[:7])['pnl'].sum()
print('GABUNGAN n',len(T),'win',round((T.pnl>0).mean()*100,1),'tot',round(T.pnl.sum(),1),'DD',round((cum-cum.cummax()).min(),1))
print(mon.round(0).to_dict())
T.to_pickle('final_T.pkl'); pd.DataFrame(summ).to_pickle('final_S.pkl')
# baseline: random 5/day, same rules (price>60, no rehold, atr2/1.5)
rng = np.random.default_rng(1); rnd = pd.Series(rng.random(len(df)))
RANKS['rand'] = rnd
bt,_ = run(base.to_numpy(), 'rand', 'atr2/1.5'); B = summarize(bt)
print('ACAK', B)
json.dump(dict(n=B['n'], win=B['win'], total=B['total'], avg=B['avg']), open('final_B.json','w'))
mkd = mk[['breadth','ew_idx','ew_ret5']].copy(); mkd.to_pickle('final_mk.pkl')
