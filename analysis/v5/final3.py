"""Per tier: the 5 chosen strategies + combined top 5. Writes dashboard CSVs."""
import sys, os, json, warnings; warnings.filterwarnings('ignore')
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
exec(open('p2.py').read().split("choice = {}")[0])
choice = json.load(open('p2_choice.json'))
NAMES = {
 'squeeze & near_ma20 & p<200': ('Squeeze di MA20', 'Bollinger menyempit; harga dekat MA20 (-3% s.d. +2%); harga 61-199'),
 'up3 & rsi<35 & p<200': ('Pantulan Hijau', 'Naik 3% atau lebih hari ini; RSI14 di bawah 35; harga 61-199'),
 'rsi<35 & engulf': ('Engulfing Oversold', 'RSI14 di bawah 35; candle hijau menelan candle merah kemarin (bullish engulfing)'),
 'downtrend & rsi<35 & engulf': ('Engulfing di Tren Turun', 'MA20 di bawah MA50; RSI14 di bawah 35; bullish engulfing'),
 'up3 & squeeze & pos60_lo': ('Squeeze Meledak dari Dasar', 'Bollinger menyempit; naik 3% atau lebih hari ini; posisi di 30% terbawah range 60 hari'),
 'down3 & red2 & rsi<35': ('Jual Panik Beruntun', 'Turun 3% atau lebih hari ini; merah 2 hari atau lebih berturut-turut; RSI14 di bawah 35'),
 'lwick & gapdown_today': ('Pantulan Pasar Panik', 'Dibuka gap turun 3% atau lebih; ekor bawah panjang (3% atau lebih)'),
 'squeeze & upstreak3 & pos60_lo': ('Squeeze Naik Beruntun', 'Bollinger menyempit; naik 3 hari beruntun; posisi di 30% terbawah range 60 hari'),
 'squeeze & pos60_lo & hl_rise': ('Squeeze Higher Low', 'Bollinger menyempit; posisi di 30% terbawah range 60 hari; low naik 2 hari berturut-turut'),
 'dry5 & lwick & nr7': ('Kering + Ekor Bawah', 'Volume 5 hari di bawah 0,7x rata-rata 20 hari; ekor bawah panjang (3% atau lebih); range harian tersempit dalam 7 hari (NR7)'),
 'cpos_lo & gapdown_today': ('Gap Turun Tutup Lemah', 'Dibuka gap turun 3% atau lebih; closing di 35% terbawah range harian'),
 'up3 & pos60_lo & reversal': ('Reversal dari Dasar', 'Kemarin turun 3% atau lebih; hari ini naik 3% atau lebih dan closing di atas high kemarin; posisi di 30% terbawah range 60 hari'),
 'dry5 & near_ma20 & gain60_lo': ('Kering di MA20 setelah Koreksi', 'Turun lebih dari 20% dalam 60 hari; volume 5 hari di bawah 0,7x rata-rata; harga dekat MA20 (-3% s.d. +2%)'),
 'rsi<35 & volhi': ('Oversold Volume Besar', 'RSI14 di bawah 35; volume 2x rata-rata 20 hari atau lebih'),
}
REM = 'tidak ada entry baru saat persentase saham di atas MA20 turun lebih dari 15 poin dalam 10 hari'
REG_TXT = {'all': 'Aktif di semua kondisi pasar; ' + REM, 'ret': 'Aktif bila pasar tidak turun lebih dari 3% dalam 5 hari; ' + REM,
           'br30': 'Aktif bila minimal 30% saham di atas MA20; ' + REM, 'br50': 'Aktif bila minimal 50% saham di atas MA20; ' + REM,
           'weak30': 'Khusus pasar lemah: hanya aktif bila kurang dari 30% saham di atas MA20', 'weak20': 'Khusus pasar lemah: hanya aktif bila kurang dari 20% saham di atas MA20'}
EX_TXT = {'atr2/1.5': 'TP = 2 x ATR, SL = 1,5 x ATR (SL 3-12%)', 'atr3/1.5': 'TP = 3 x ATR, SL = 1,5 x ATR (SL 3-12%)', 'atr2.5/2': 'TP = 2,5 x ATR, SL = 2 x ATR (SL 3-12%)',
          'low5/res': 'SL di bawah low 5 hari, TP di resisten terdekat (high 20/60 hari)', 'low10/res': 'SL di bawah low 10 hari, TP di resisten terdekat (high 20/60 hari)',
          'low5/3atr': 'SL di bawah low 5 hari, TP = 3 x ATR'}
RK_TXT = {'volratio': 'Lonjakan volume tertinggi', 'lowpos': 'Posisi paling dekat dasar 60 hari', 'value': 'Nilai transaksi terbesar', 'rsi_low': 'RSI paling rendah'}
codes = {}
for t in TIERS:
    for s in choice[t]:
        if s['cond'] not in codes: codes[s['cond']] = len(codes) + 1
TE = set(dates[-60:]); nd = df.groupby('ticker')['date'].shift(-1).to_numpy(); Darr = D.to_numpy(); valma = df['val_ma20'].to_numpy()
def mk_trades(rows, ex_of):
    out = []
    for i, code, pnl in rows:
        (res, exk, e, tp, sl), _ = outc(ex_of[code]); j = i + exk[i]
        assert tick_all[j] == tick_all[i]
        g = pnl / 100 + .004
        why = 'TP' if abs(g - tp[i]) < 1e-6 else 'SL' if abs(g + sl[i]) < 1e-6 else 'Gap SL' if g < -sl[i] else 'Gap TP' if g > tp[i] else 'Waktu (7 hari)'
        out.append(dict(tanggal_sinyal=Darr[i], tanggal_beli=nd[i], tanggal_jual=Darr[j], id=code, saham=tick_all[i], closing=int(round(close[i])),
                        harga_beli=int(round(e[i])), tp=int(round(e[i] * (1 + tp[i]))), sl=int(round(e[i] * (1 - sl[i]))), tp_pct=round(tp[i] * 100, 2),
                        sl_pct=round(sl[i] * 100, 2), pl_pct=round(pnl, 2), lama_hold=int(exk[i]), keluar=why, nilai_tx_miliar=round(valma[i] / 1e9, 2)))
    return pd.DataFrame(out)
def stats_of(x):
    a = x.pl_pct.to_numpy(); cum = np.cumsum(a)
    mon = x.groupby(x.tanggal_sinyal.str[:7])['pl_pct'].sum()
    return dict(trades=len(a), winrate=round((a > 0).mean() * 100, 1), akumulasi_pl=round(a.sum(), 1), rata_pl=round(a.mean(), 2),
                rata_menang=round(a[a > 0].mean(), 2), rata_kalah=round(a[a <= 0].mean(), 2), profit_factor=round(a[a > 0].sum() / -a[a <= 0].sum(), 2),
                max_drawdown=round((cum - np.maximum.accumulate(cum)).min(), 1), bulan_terburuk=round(mon.min(), 1), bulan_mei=round(mon.get('2026-05', 0.0), 1),
                hold_rata=round(x.lama_hold.mean(), 1), q=[round(x[x.tanggal_sinyal.isin(q)].pl_pct.sum(), 1) for q in Q])
SUMM, TT, CC, TOP, COMP, CURVE, MON, BASE = [], [], [], [], [], [], [], []
v4 = pd.read_csv('/tmp/claude-0/-home-claude-backuplumisignal/7364f86a-c865-5ff2-954d-daf29079ded0/scratchpad/dash/v4/top5_ringkasan.csv').set_index('tier')
rng = np.random.default_rng(1); RANKS['rand'] = pd.Series(rng.random(len(df)))
for tier in TIERS:
    specs = choice[tier]; ex_of = {codes[s['cond']]: s['exit'] for s in specs}
    per = []
    for s in specs:
        code = codes[s['cond']]
        m = mask_of(s['cond'], tier).to_numpy() & ALLREG[s['reg']]
        tr, rec = run(m, s['rank'], s['exit']); trp, _ = run(m, s['rank'], s['exit'], path='pess')
        x = mk_trades([(i, code, p) for i, p in zip(tr.i, tr.pnl)], ex_of); x['strategi'] = NAMES[s['cond']][0]
        st = stats_of(x); te = x[x.tanggal_sinyal.isin(TE)]
        SUMM.append(dict(tier=tier, id=code, strategi=NAMES[s['cond']][0], syarat=NAMES[s['cond']][1], pasar=REG_TXT[s['reg']], ranking=RK_TXT[s['rank']],
                         exit=EX_TXT[s['exit']] + ', maks 7 hari', jenis='Pasar lemah' if s['reg'].startswith('weak') else 'Pasar normal',
                         **{k: v for k, v in st.items() if k != 'q'}, q1=st['q'][0], q2=st['q'][1], q3=st['q'][2], q4=st['q'][3],
                         akumulasi_pl_pesimis=round(trp.pnl.sum(), 1), uji_trades=len(te), uji_winrate=round((te.pl_pct > 0).mean() * 100, 1) if len(te) else None,
                         uji_akumulasi_pl=round(te.pl_pct.sum(), 1), tp_rata=round(x.tp_pct.mean(), 1), sl_rata=round(x.sl_pct.mean(), 1)))
        x.insert(0, 'tier', tier); per.append(x)
    T = pd.concat(per)
    sc, P = combined([dict(s) for s in specs], tier, return_trades=True)
    C = mk_trades([(i, codes[specs[sid]['cond']], p) for i, sid, p in zip(P.i, P.sid, P.pnl)], ex_of)
    C['strategi'] = [dict((codes[s['cond']], NAMES[s['cond']][0]) for s in specs)[k] for k in C.id]; C.insert(0, 'tier', tier)
    assert not T.duplicated(['tanggal_sinyal', 'id', 'saham']).any() and not C.duplicated(['tanggal_sinyal', 'saham']).any()
    TT.append(T); CC.append(C)
    cs = stats_of(C); te = C[C.tanggal_sinyal.isin(TE)]
    TOP.append(dict(tier=tier, **{k: v for k, v in cs.items() if k != 'q'}, q1=cs['q'][0], q2=cs['q'][1], q3=cs['q'][2], q4=cs['q'][3],
                    uji_trades=len(te), uji_winrate=round((te.pl_pct > 0).mean() * 100, 1), uji_akumulasi_pl=round(te.pl_pct.sum(), 1),
                    hari_ada_transaksi=int(C.tanggal_sinyal.nunique()), saham_unik=int(C.saham.nunique()),
                    semua_trades=len(T), semua_akumulasi_pl=round(T.pl_pct.sum(), 1),
                    lama_akumulasi_pl=v4.loc[tier, 'akumulasi_pl'], lama_max_drawdown=v4.loc[tier, 'max_drawdown'], lama_bulan_terburuk=v4.loc[tier, 'bulan_terburuk']))
    comp = C.groupby(['id', 'strategi']).agg(trades=('pl_pct', 'size'), winrate=('pl_pct', lambda x: round((x > 0).mean() * 100, 1)), akumulasi_pl=('pl_pct', lambda x: round(x.sum(), 1))).reset_index()
    comp.insert(0, 'tier', tier); COMP.append(comp)
    ids = [codes[s['cond']] for s in specs]
    eq = T.groupby(['tanggal_sinyal', 'id'])['pl_pct'].sum().unstack(fill_value=0).reindex(columns=ids, fill_value=0).cumsum()
    eq.columns = [f'k{c}' for c in eq.columns]
    eq = eq.join(C.groupby('tanggal_sinyal')['pl_pct'].sum().cumsum().rename('top5'), how='outer').ffill().fillna(0).reset_index().rename(columns={'tanggal_sinyal': 'tanggal'})
    eq = eq.melt(id_vars='tanggal', var_name='seri', value_name='akumulasi_pl'); eq['akumulasi_pl'] = eq['akumulasi_pl'].round(1); eq.insert(0, 'tier', tier); CURVE.append(eq)
    mon = T.assign(bulan=T.tanggal_sinyal.str[:7]).groupby(['bulan', 'id', 'strategi']).agg(trades=('pl_pct', 'size'), winrate=('pl_pct', lambda x: round((x > 0).mean() * 100, 1)), akumulasi_pl=('pl_pct', lambda x: round(x.sum(), 1))).reset_index()
    monc = C.assign(bulan=C.tanggal_sinyal.str[:7]).groupby('bulan').agg(trades=('pl_pct', 'size'), winrate=('pl_pct', lambda x: round((x > 0).mean() * 100, 1)), akumulasi_pl=('pl_pct', lambda x: round(x.sum(), 1))).reset_index()
    monc['id'] = 0; monc['strategi'] = 'Top 5 gabungan'
    mm = pd.concat([mon, monc[mon.columns]]); mm.insert(0, 'tier', tier); MON.append(mm)
    bt, _ = run(tier_mask(tier), 'rand', 'atr2/1.5'); B = summarize(bt)
    BASE.append(dict(tier=tier, pembanding='5 saham acak per hari', trades=B['n'], winrate=B['win'], akumulasi_pl=round(B['total'], 1), rata_pl=B['avg']))
    print(f"[{tier}] top5 n{cs['trades']} win{cs['winrate']} tot{cs['akumulasi_pl']:+.1f} DD{cs['max_drawdown']} worst{cs['bulan_terburuk']} mei{cs['bulan_mei']} | 60h {round(te.pl_pct.sum(),1)} | strategi {ids}")
pd.concat(TT).to_csv(OUT + 'transaksi.csv', index=False); pd.concat(CC).to_csv(OUT + 'top5_gabungan.csv', index=False)
pd.DataFrame(SUMM).to_csv(OUT + 'ringkasan.csv', index=False); pd.DataFrame(TOP).to_csv(OUT + 'top5_ringkasan.csv', index=False)
pd.concat(COMP).to_csv(OUT + 'top5_komposisi.csv', index=False); pd.concat(CURVE).to_csv(OUT + 'kurva.csv', index=False)
pd.concat(MON).to_csv(OUT + 'bulanan.csv', index=False); pd.DataFrame(BASE).to_csv(OUT + 'pembanding.csv', index=False)
pd.DataFrame([dict(tier=t, label=TIER_LABEL[t], minimal_rp=TIERS[t], saham_terakhir=int(((df['date'] == dates[-1]).to_numpy() & tier_mask(t)).sum())) for t in TIERS]).to_csv(OUT + 'tingkat.csv', index=False)
p = mkd.reset_index().rename(columns={'date': 'tanggal'})
p = pd.DataFrame({'tanggal': p['tanggal'], 'pct_di_atas_ma20': (p['breadth'] * 100).round(1), 'breadth_ma5': (p['br5'] * 100).round(1),
                  'ubah_10hari_poin': (p['chg'] * 100).round(1), 'ubah_5hari': (p['ew_ret5'] * 100).round(2)})
p['rem_aktif'] = (p['ubah_10hari_poin'] < -15).astype(int)
p.to_csv(OUT + 'pasar.csv', index=False)
json.dump(codes, open(OUT + 'codes.json', 'w'), indent=1)
