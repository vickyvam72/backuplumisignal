"""Final pipeline: 6 strategies (top 5 each) + combined top 5 across all strategies.
Writes the dashboard CSVs into OUT."""
import sys, os, json
import pandas as pd, numpy as np
from engine import *

OUT = sys.argv[1] if len(sys.argv) > 1 else 'out_final/'
os.makedirs(OUT, exist_ok=True)
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
tick_arr = df['ticker'].to_numpy(); dates_arr = np.array(dates)
nd = df.groupby('ticker')['date'].shift(-1).to_numpy()

def why(r):
    g = r.pnl / 100 + .004
    if abs(g - r.tp_pct / 100) < 1e-6: return 'TP'
    if abs(g + r.sl_pct / 100) < 1e-6: return 'SL'
    if g < -r.sl_pct / 100: return 'Gap SL'
    if g > r.tp_pct / 100: return 'Gap TP'
    return 'Waktu (7 hari)'

def finish(tr):
    tr = tr.copy()
    tr['keluar'] = tr.apply(why, axis=1)
    tr['tanggal_beli'] = nd[tr.i.values]
    j = tr.i.to_numpy() + tr['hold'].to_numpy()
    tr['tanggal_jual'] = D.to_numpy()[j]
    assert (tick_all[j] == tick_all[tr.i.to_numpy()]).all()
    # trading sessions held, counting the buy day as day 1
    tr['lama_hold'] = tr['hold']
    return tr

allt = []; summ = []
masks = {}
for s in STR:
    m = mask_of(s['cond']).to_numpy() & s['reg']
    masks[s['id']] = m
    tr, rec = run(m, s['rank'], s['exit']); trp, _ = run(m, s['rank'], s['exit'], path='pess')
    tr = finish(tr)
    S_ = summarize(tr); P_ = summarize(trp); te = summarize(tr[tr.date.isin(TE)])
    a = tr.pnl
    summ.append(dict(id=s['id'], strategi=s['name'], syarat=s['syarat'], ranking=s['ranking'], pasar=s['pasar'], exit=s['tpsl'],
        trades=S_['n'], winrate=S_['win'], akumulasi_pl=S_['total'], rata_pl=S_['avg'], rata_menang=round(a[a > 0].mean(), 2), rata_kalah=round(a[a <= 0].mean(), 2),
        profit_factor=S_['pf'], max_drawdown=S_['maxdd'], bulan_terburuk=S_['worst_month'], akumulasi_pl_pesimis=P_['total'],
        uji_trades=te.get('n', 0), uji_winrate=te.get('win'), uji_akumulasi_pl=te.get('total'),
        tp_rata=round(tr.tp_pct.mean(), 1), sl_rata=round(tr.sl_pct.mean(), 1), hold_rata=round(tr.lama_hold.mean(), 1),
        q1=S_['q'][0], q2=S_['q'][1], q3=S_['q'][2], q4=S_['q'][3], direkomendasikan=rec))
    tr['id'] = s['id']; tr['strategi'] = s['name']; allt.append(tr)
    print(f"S{s['id']} {s['name']:24} n{S_['n']} win{S_['win']} tot{S_['total']:+.1f} DD{S_['maxdd']} worstM{S_['worst_month']} | 60h {te.get('n')} {te.get('total')}")

# ---------------- combined: top 5 across all strategies ----------------
OUTC = {s['id']: outcomes(s['exit']) for s in STR}
EXD = {k: exit_di(v[1]) for k, v in OUTC.items()}
cand = []
for s in STR:
    m = masks[s['id']]
    rk = np.asarray(RANKS[s['rank']], dtype=float)
    idx = np.where(m & np.isfinite(rk))[0]
    tmp = pd.DataFrame({'i': idx, 'd': di[idx], 'sid': s['id'], 'r': rk[idx]})
    tmp['pct'] = tmp.groupby('d')['r'].rank(pct=True)   # 1.0 = best of that strategy that day
    cand.append(tmp)
cand = pd.concat(cand).sort_values('d')
hist = {s['id']: [] for s in STR}     # (exit_day, pnl) of closed trades per strategy
held = {}; picked = []
PRIOR_N = 30
for d, day in cand.groupby('d', sort=True):
    # walk-forward quality of each strategy: mean P/L of its last 30 trades closed before today
    q = {}
    for sid, h in hist.items():
        done = [p for (xd, p) in h if xd < d][-PRIOR_N:]
        q[sid] = np.mean(done) if len(done) >= 5 else 0.0
    day = day.assign(q=day.sid.map(q)).sort_values(['q', 'pct'], ascending=[False, False])
    taken = 0; seen = set()
    for _, c in day.iterrows():
        if taken >= 5: break
        t = tick_arr[int(c.i)]
        if t in seen or held.get(t, -1) > d: continue
        seen.add(t); taken += 1
        res, exk, e, tp, sl = OUTC[int(c.sid)]
        i = int(c.i)
        if np.isfinite(res[i]):
            xd = EXD[int(c.sid)][i]
            held[t] = xd
            hist[int(c.sid)].append((xd, res[i] * 100))
            picked.append(dict(i=i, date=dates[d], ticker=t, close=close[i], entry=e[i], tp=e[i] * (1 + tp[i]), sl=e[i] * (1 - sl[i]),
                               tp_pct=tp[i] * 100, sl_pct=sl[i] * 100, pnl=res[i] * 100, hold=exk[i], id=int(c.sid)))
C = finish(pd.DataFrame(picked))
names = {s['id']: s['name'] for s in STR}
C['strategi'] = C['id'].map(names)
CS = summarize(C); CT = summarize(C[C.date.isin(TE)])
print('TOP5 GABUNGAN', CS, 'per strategi:', C.id.value_counts().sort_index().to_dict())

# ---------------- export ----------------
T = pd.concat(allt).sort_values(['date', 'id'])
cols = ['tanggal_sinyal', 'tanggal_beli', 'tanggal_jual', 'id', 'strategi', 'saham', 'closing', 'harga_beli', 'tp', 'sl', 'tp_pct', 'sl_pct', 'pl_pct', 'lama_hold', 'keluar']
def tx(x):
    x = x.rename(columns={'date': 'tanggal_sinyal', 'ticker': 'saham', 'close': 'closing', 'entry': 'harga_beli', 'pnl': 'pl_pct'})[cols].copy()
    for c in ['closing', 'harga_beli', 'tp', 'sl']: x[c] = x[c].round(0).astype(int)
    for c in ['tp_pct', 'sl_pct', 'pl_pct']: x[c] = x[c].round(2)
    return x.sort_values(['tanggal_sinyal', 'id', 'saham'])
tT = tx(T); tC = tx(C)
assert not tT.duplicated(['tanggal_sinyal', 'id', 'saham']).any() and not tC.duplicated(['tanggal_sinyal', 'saham']).any()
tT.to_csv(OUT + 'transaksi.csv', index=False)
tC.to_csv(OUT + 'top5_gabungan.csv', index=False)
pd.DataFrame(summ).to_csv(OUT + 'ringkasan.csv', index=False)
eq = tT.groupby(['tanggal_sinyal', 'id'])['pl_pct'].sum().unstack(fill_value=0).cumsum()
eq.columns = [f's{c}' for c in eq.columns]; eq['gabungan'] = eq.sum(axis=1)
eqc = tC.groupby('tanggal_sinyal')['pl_pct'].sum().cumsum().rename('top5')
eq = eq.join(eqc, how='outer').ffill().fillna(0)
eq.reset_index().rename(columns={'index': 'tanggal', 'tanggal_sinyal': 'tanggal'}).round(1).to_csv(OUT + 'kurva.csv', index=False)
mon = tT.assign(bulan=tT.tanggal_sinyal.str[:7]).groupby(['bulan', 'id', 'strategi']).agg(trades=('pl_pct', 'size'), winrate=('pl_pct', lambda x: round((x > 0).mean() * 100, 1)), akumulasi_pl=('pl_pct', lambda x: round(x.sum(), 1))).reset_index()
monc = tC.assign(bulan=tC.tanggal_sinyal.str[:7]).groupby('bulan').agg(trades=('pl_pct', 'size'), winrate=('pl_pct', lambda x: round((x > 0).mean() * 100, 1)), akumulasi_pl=('pl_pct', lambda x: round(x.sum(), 1))).reset_index()
monc['id'] = 0; monc['strategi'] = 'Top 5 gabungan'
pd.concat([mon, monc[mon.columns]]).to_csv(OUT + 'bulanan.csv', index=False)
a = tC.pl_pct
top5 = dict(trades=CS['n'], winrate=CS['win'], akumulasi_pl=CS['total'], rata_pl=CS['avg'], profit_factor=CS['pf'], max_drawdown=CS['maxdd'],
            bulan_terburuk=CS['worst_month'], uji_trades=CT.get('n', 0), uji_winrate=CT.get('win'), uji_akumulasi_pl=CT.get('total'),
            rata_menang=round(a[a > 0].mean(), 2), rata_kalah=round(a[a <= 0].mean(), 2), hold_rata=round(tC.lama_hold.mean(), 1),
            hari_ada_transaksi=int(tC.tanggal_sinyal.nunique()))
pd.DataFrame([top5]).to_csv(OUT + 'top5_ringkasan.csv', index=False)
comp = tC.groupby(['id', 'strategi']).agg(trades=('pl_pct', 'size'), winrate=('pl_pct', lambda x: round((x > 0).mean() * 100, 1)), akumulasi_pl=('pl_pct', lambda x: round(x.sum(), 1))).reset_index()
comp.to_csv(OUT + 'top5_komposisi.csv', index=False)
rng = np.random.default_rng(1); RANKS['rand'] = pd.Series(rng.random(len(df)))
bt, _ = run(base.to_numpy(), 'rand', 'atr2/1.5'); B = summarize(bt)
pd.DataFrame([dict(pembanding='5 saham acak per hari', trades=B['n'], winrate=B['win'], akumulasi_pl=round(B['total'], 1), rata_pl=B['avg'])]).to_csv(OUT + 'pembanding.csv', index=False)
p = mk.reset_index().rename(columns={'date': 'tanggal', 'breadth': 'pct_di_atas_ma20', 'ew_idx': 'indeks_rata', 'ew_ret5': 'ubah_5hari'})[['tanggal', 'pct_di_atas_ma20', 'indeks_rata', 'ubah_5hari']]
p['pct_di_atas_ma20'] = (p['pct_di_atas_ma20'] * 100).round(1); p['indeks_rata'] = (p['indeks_rata'] * 100).round(2); p['ubah_5hari'] = (p['ubah_5hari'] * 100).round(2)
p['breadth_ma5'] = p['pct_di_atas_ma20'].rolling(5, min_periods=3).mean().round(1)
p.to_csv(OUT + 'pasar.csv', index=False)
print('ACAK', B['total'], 'GABUNGAN 6 strategi', round(tT.pl_pct.sum(), 1), len(tT))
print(json.dumps(top5))
