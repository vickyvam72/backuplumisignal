"""10 strategies x 5 liquidity tiers, plus combined top 5 per tier. Writes dashboard CSVs."""
import sys, os, json
import pandas as pd, numpy as np
from engine import *

OUT = sys.argv[1] if len(sys.argv) > 1 else 'out_final2/'
os.makedirs(OUT, exist_ok=True)
b = df['mkt_breadth_ma5'].to_numpy(); r5 = df['mkt_ew_ret5'].to_numpy()
REG = {'ret': r5 > -.03, 'br30': b >= .30, 'br50': b >= .50, 'weak': b < .30}
REG_TXT = {'ret': 'Aktif bila pasar tidak turun lebih dari 3% dalam 5 hari', 'br30': 'Aktif bila minimal 30% saham di atas MA20',
           'br50': 'Aktif bila minimal 50% saham di atas MA20', 'weak': 'Hanya aktif bila kurang dari 30% saham di atas MA20 (pasar lemah)'}
EX_TXT = {'atr2/1.5': 'TP = 2 x ATR, SL = 1,5 x ATR (SL 3-12%)', 'atr3/1.5': 'TP = 3 x ATR, SL = 1,5 x ATR (SL 3-12%)',
          'atr2.5/2': 'TP = 2,5 x ATR, SL = 2 x ATR (SL 3-12%)', 'low5/res': 'SL di bawah low 5 hari, TP di resisten terdekat (high 20/60 hari)',
          'low10/res': 'SL di bawah low 10 hari, TP di resisten terdekat (high 20/60 hari)', 'low5/3atr': 'SL di bawah low 5 hari, TP = 3 x ATR',
          'low5|2atr/res': 'SL di bawah low 5 hari (maks 2 x ATR), TP di resisten terdekat'}
RK_TXT = {'volratio': 'Lonjakan volume tertinggi', 'lowpos': 'Posisi paling dekat dasar 60 hari', 'value': 'Nilai transaksi terbesar',
          'rsi_low': 'RSI paling rendah', 'udvr': 'Rasio volume naik/turun tertinggi'}
CAL = json.load(open('t4_cal.json'))
META = {
 1: ('Oversold + Volume Beli', 'RSI14 di bawah 35; volume hari naik lebih dari 2x volume hari turun (10 hari); harga 61-199'),
 2: ('Pantulan Hijau', 'Naik 3% atau lebih hari ini; RSI14 di bawah 35; harga 61-199'),
 3: ('Naik Pelan dari Dasar', 'Naik 3 hari beruntun; posisi di 30% terbawah range 60 hari; inside day'),
 4: ('Squeeze Naik Beruntun', 'Bollinger menyempit; naik 3 hari beruntun; posisi di 30% terbawah range 60 hari'),
 5: ('Squeeze di MA20', 'Bollinger menyempit; harga dekat MA20 (-3% s.d. +2%); harga 61-199'),
 6: ('Pantulan Pasar Panik', 'Dibuka gap turun 3% atau lebih; ekor bawah panjang (3% atau lebih)'),
 7: ('Reversal dari Dasar', 'Kemarin turun 3% atau lebih; hari ini naik 3% atau lebih dan closing di atas high kemarin; posisi di 30% terbawah range 60 hari'),
 8: ('Engulfing Oversold', 'Tren turun (MA20 di bawah MA50); RSI14 di bawah 35; candle hijau menelan candle merah kemarin (bullish engulfing)'),
 9: ('Squeeze Higher Low', 'Bollinger menyempit; posisi di 30% terbawah range 60 hari; low naik 2 hari berturut-turut'),
 10: ('Ekor Bawah di Range Sempit', 'Ekor bawah panjang (3% atau lebih); posisi di 30% terbawah range 60 hari; range harian tersempit dalam 7 hari (NR7)'),
}
STR = []
for k in range(1, 11):
    c = CAL[str(k)]
    STR.append(dict(id=k, name=META[k][0], syarat=META[k][1], cond=c['cond'], reg=c['reg'], rank=c['rank'], exit=c['exit'],
                    pasar=REG_TXT[c['reg']], ranking=RK_TXT[c['rank']], tpsl=EX_TXT[c['exit']] + ', maks 7 hari'))
names = {s['id']: s['name'] for s in STR}
TE = set(dates[-60:])
nd = df.groupby('ticker')['date'].shift(-1).to_numpy()
Darr = D.to_numpy()
OUTC = {e: outcomes(e) for e in set(s['exit'] for s in STR)}
EXD = {e: exit_di(v[1]) for e, v in OUTC.items()}

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
    assert (tick_all[j] == tick_all[tr.i.to_numpy()]).all()
    tr['tanggal_jual'] = Darr[j]
    tr['lama_hold'] = tr['hold']
    tr['nilai_tx_20h'] = df['val_ma20'].to_numpy()[tr.i.values]
    return tr

cols = ['tier', 'tanggal_sinyal', 'tanggal_beli', 'tanggal_jual', 'id', 'strategi', 'saham', 'closing', 'harga_beli', 'tp', 'sl', 'tp_pct', 'sl_pct',
        'pl_pct', 'lama_hold', 'keluar', 'nilai_tx_miliar']
def tx(x, tier):
    x = x.rename(columns={'date': 'tanggal_sinyal', 'ticker': 'saham', 'close': 'closing', 'entry': 'harga_beli', 'pnl': 'pl_pct'}).copy()
    x['tier'] = tier; x['nilai_tx_miliar'] = (x['nilai_tx_20h'] / 1e9).round(2)
    x = x[cols]
    for c in ['closing', 'harga_beli', 'tp', 'sl']: x[c] = x[c].round(0).astype(int)
    for c in ['tp_pct', 'sl_pct', 'pl_pct']: x[c] = x[c].round(2)
    return x.sort_values(['tanggal_sinyal', 'id', 'saham'])

ALL_T, ALL_C, SUMM, TOP, COMP, CURVE, MON, BASE = [], [], [], [], [], [], [], []
rng = np.random.default_rng(1); RANKS['rand'] = pd.Series(rng.random(len(df)))
for tier in TIERS:
    allt = []; masks = {}
    for s in STR:
        m = mask_of(s['cond'], tier).to_numpy() & REG[s['reg']]
        masks[s['id']] = m
        tr, rec = run(m, s['rank'], s['exit']); trp, _ = run(m, s['rank'], s['exit'], path='pess')
        row = dict(tier=tier, id=s['id'], strategi=s['name'], syarat=s['syarat'], ranking=s['ranking'], pasar=s['pasar'], exit=s['tpsl'], direkomendasikan=rec)
        if len(tr):
            tr = finish(tr); S_ = summarize(tr); P_ = summarize(trp); te = summarize(tr[tr.date.isin(TE)]); a = tr.pnl
            row.update(trades=S_['n'], winrate=S_['win'], akumulasi_pl=S_['total'], rata_pl=S_['avg'],
                       rata_menang=round(a[a > 0].mean(), 2) if (a > 0).any() else None, rata_kalah=round(a[a <= 0].mean(), 2) if (a <= 0).any() else None,
                       profit_factor=S_['pf'], max_drawdown=S_['maxdd'], bulan_terburuk=S_['worst_month'], akumulasi_pl_pesimis=P_['total'],
                       uji_trades=te.get('n', 0), uji_winrate=te.get('win'), uji_akumulasi_pl=te.get('total'),
                       tp_rata=round(tr.tp_pct.mean(), 1), sl_rata=round(tr.sl_pct.mean(), 1), hold_rata=round(tr.lama_hold.mean(), 1),
                       q1=S_['q'][0], q2=S_['q'][1], q3=S_['q'][2], q4=S_['q'][3])
            tr['id'] = s['id']; tr['strategi'] = s['name']; allt.append(tr)
        else:
            row.update(trades=0)
        SUMM.append(row)
    T = pd.concat(allt)
    # ---- combined top 5 for this tier ----
    cand = []
    for s in STR:
        rk = np.asarray(RANKS[s['rank']], dtype=float); idx = np.where(masks[s['id']] & np.isfinite(rk))[0]
        tmp = pd.DataFrame({'i': idx, 'd': di[idx], 'sid': s['id'], 'r': rk[idx]})
        tmp['pct'] = tmp.groupby('d')['r'].rank(pct=True); cand.append(tmp)
    cand = pd.concat(cand).sort_values('d')
    sexit = {s['id']: s['exit'] for s in STR}
    hist = {s['id']: [] for s in STR}; held = {}; picked = []
    for d, day in cand.groupby('d', sort=True):
        q = {}
        for sid, h in hist.items():
            done = [p for (xd, p) in h if xd < d]
            q[sid] = np.mean(done) if len(done) >= 10 else 0.0
        day = day.assign(q=day.sid.map(q)).sort_values(['q', 'pct'], ascending=[False, False])
        taken = 0; seen = set()
        for _, c in day.iterrows():
            if taken >= 5: break
            i = int(c.i); t = tick_all[i]
            if t in seen or held.get(t, -1) > d: continue
            seen.add(t); taken += 1
            ex = sexit[int(c.sid)]; res, exk, e, tp, sl = OUTC[ex]
            if np.isfinite(res[i]):
                xd = EXD[ex][i]; held[t] = xd; hist[int(c.sid)].append((xd, res[i] * 100))
                picked.append(dict(i=i, date=dates[d], ticker=t, close=close[i], entry=e[i], tp=e[i] * (1 + tp[i]), sl=e[i] * (1 - sl[i]),
                                   tp_pct=tp[i] * 100, sl_pct=sl[i] * 100, pnl=res[i] * 100, hold=exk[i], id=int(c.sid)))
    C = finish(pd.DataFrame(picked)); C['strategi'] = C['id'].map(names)
    CS = summarize(C); CT = summarize(C[C.date.isin(TE)]); a = C.pnl
    tT = tx(T, tier); tC = tx(C, tier)
    assert not tT.duplicated(['tanggal_sinyal', 'id', 'saham']).any() and not tC.duplicated(['tanggal_sinyal', 'saham']).any()
    ALL_T.append(tT); ALL_C.append(tC)
    TOP.append(dict(tier=tier, trades=CS['n'], winrate=CS['win'], akumulasi_pl=CS['total'], rata_pl=CS['avg'], profit_factor=CS['pf'],
                    max_drawdown=CS['maxdd'], bulan_terburuk=CS['worst_month'], uji_trades=CT.get('n', 0), uji_winrate=CT.get('win'),
                    uji_akumulasi_pl=CT.get('total'), rata_menang=round(a[a > 0].mean(), 2), rata_kalah=round(a[a <= 0].mean(), 2),
                    hold_rata=round(C.lama_hold.mean(), 1), hari_ada_transaksi=int(C.date.nunique()), saham_unik=int(C.ticker.nunique()),
                    semua_trades=len(T), semua_akumulasi_pl=round(T.pnl.sum(), 1)))
    comp = tC.groupby(['id', 'strategi']).agg(trades=('pl_pct', 'size'), winrate=('pl_pct', lambda x: round((x > 0).mean() * 100, 1)),
                                              akumulasi_pl=('pl_pct', lambda x: round(x.sum(), 1))).reset_index()
    comp.insert(0, 'tier', tier); COMP.append(comp)
    eq = tT.groupby(['tanggal_sinyal', 'id'])['pl_pct'].sum().unstack(fill_value=0).reindex(columns=range(1, 11), fill_value=0).cumsum()
    eq.columns = [f's{c}' for c in eq.columns]; eq['gabungan'] = eq.sum(axis=1)
    eq = eq.join(tC.groupby('tanggal_sinyal')['pl_pct'].sum().cumsum().rename('top5'), how='outer').ffill().fillna(0)
    eq = eq.reset_index().rename(columns={'index': 'tanggal', 'tanggal_sinyal': 'tanggal'}).round(1); eq.insert(0, 'tier', tier); CURVE.append(eq)
    mon = tT.assign(bulan=tT.tanggal_sinyal.str[:7]).groupby(['bulan', 'id', 'strategi']).agg(trades=('pl_pct', 'size'), winrate=('pl_pct', lambda x: round((x > 0).mean() * 100, 1)), akumulasi_pl=('pl_pct', lambda x: round(x.sum(), 1))).reset_index()
    monc = tC.assign(bulan=tC.tanggal_sinyal.str[:7]).groupby('bulan').agg(trades=('pl_pct', 'size'), winrate=('pl_pct', lambda x: round((x > 0).mean() * 100, 1)), akumulasi_pl=('pl_pct', lambda x: round(x.sum(), 1))).reset_index()
    monc['id'] = 0; monc['strategi'] = 'Top 5 gabungan'
    mm = pd.concat([mon, monc[mon.columns]]); mm.insert(0, 'tier', tier); MON.append(mm)
    bt, _ = run(tier_mask(tier), 'rand', 'atr2/1.5'); B = summarize(bt)
    BASE.append(dict(tier=tier, pembanding='5 saham acak per hari', trades=B['n'], winrate=B['win'], akumulasi_pl=round(B['total'], 1), rata_pl=B['avg']))
    print(f"[{tier}] top5: n{CS['n']} win{CS['win']} tot{CS['total']:+.1f} avg{CS['avg']:+.2f} DD{CS['maxdd']} worst{CS['worst_month']} | 10 strategi n{len(T)} tot{T.pnl.sum():+.1f} | acak {B['total']:+.1f}")

pd.concat(ALL_T).to_csv(OUT + 'transaksi.csv', index=False)
pd.concat(ALL_C).to_csv(OUT + 'top5_gabungan.csv', index=False)
pd.DataFrame(SUMM).to_csv(OUT + 'ringkasan.csv', index=False)
pd.DataFrame(TOP).to_csv(OUT + 'top5_ringkasan.csv', index=False)
pd.concat(COMP).to_csv(OUT + 'top5_komposisi.csv', index=False)
pd.concat(CURVE).to_csv(OUT + 'kurva.csv', index=False)
pd.concat(MON).to_csv(OUT + 'bulanan.csv', index=False)
pd.DataFrame(BASE).to_csv(OUT + 'pembanding.csv', index=False)
pd.DataFrame([dict(tier=t, label=TIER_LABEL[t], minimal_rp=TIERS[t], saham_terakhir=int(((df['date'] == dates[-1]).to_numpy() & tier_mask(t)).sum())) for t in TIERS]).to_csv(OUT + 'tingkat.csv', index=False)
p = mk.reset_index().rename(columns={'date': 'tanggal', 'breadth': 'pct_di_atas_ma20', 'ew_idx': 'indeks_rata', 'ew_ret5': 'ubah_5hari'})[['tanggal', 'pct_di_atas_ma20', 'indeks_rata', 'ubah_5hari']]
p['pct_di_atas_ma20'] = (p['pct_di_atas_ma20'] * 100).round(1); p['indeks_rata'] = (p['indeks_rata'] * 100).round(2); p['ubah_5hari'] = (p['ubah_5hari'] * 100).round(2)
p['breadth_ma5'] = p['pct_di_atas_ma20'].rolling(5, min_periods=3).mean().round(1)
p.to_csv(OUT + 'pasar.csv', index=False)
print(pd.DataFrame(SUMM).pivot(index='id', columns='tier', values='akumulasi_pl')[list(TIERS)].to_string())
