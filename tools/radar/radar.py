"""Radar Besok: daily screening of every IDX stock with the calibrated strategies.

Same engine as the backtest (features.py + sim.py): signal at the close, buy next morning inside the zone
close .. close+1%, TP/SL on IDX price fractions, top 5 per strategy per day, a stock is never recommended
again while the previous pick is still held, and a combined top 5 across the five strategies of a tier.

Inputs : a bars CSV (date,ticker,open,high,low,close,volume), optional names CSV (ticker,name),
         and the folder of earlier published picks (frozen, so history never changes after the fact).
Outputs: latest.json (today's radar), stocks.json (per-stock strategy check), ledger_<tier>.json (live
         results of every published pick) and picks/<date>.json (the frozen picks of this run).
"""
import argparse
import glob
import json
import math
import os
import re
from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd

import sim
from features import TIERS, TIER_LABEL, Context, build_features
from sim import simulate, tick

HERE = os.path.dirname(os.path.abspath(__file__))
INF = 10 ** 9


def fmt_pct(x, dec=1, signed=True):
    s = f"{x * 100:+.{dec}f}%" if signed else f"{x * 100:.{dec}f}%"
    return s.replace('.', ',')


def fmt_num(x, dec=1):
    return f"{x:.{dec}f}".replace('.', ',')


def fmt_px(x):
    return f"{x:,.0f}".replace(',', '.')


def tick1(p):
    return float(tick(np.array([p]))[0])


def snap(p):
    t = tick1(p)
    return round(p / t) * t


class Engine:
    def __init__(self, ctx):
        self.ctx = ctx
        self._out = {}
        n = len(ctx.df)
        self.ndate = ctx.df.groupby('ticker')['date'].shift(-1).to_numpy()

    def outcomes(self, ex):
        if ex not in self._out:
            c = self.ctx
            res, exk, e, tp, sl = simulate(c.df, c.F, np.ones(len(c.df), bool), 'zone1', path='ohlc', return_exit=True, **c.EXITS[ex])
            ok = sim.LAST['ok'].copy()
            n = len(c.df)
            j = np.minimum(np.arange(n) + np.maximum(exk, 0), n - 1)
            same = c.tick_all[j] == c.tick_all
            xdi = np.where((exk > 0) & same, c.di[j], -1)
            self._out[ex] = dict(res=res, exk=exk, e=e, tp=tp, sl=sl, ok=ok, xdi=xdi)
        return self._out[ex]

    def held_until(self, o, i):
        """Session index until which a pick blocks its ticker (INF while still open)."""
        if not o['ok'][i]:
            return -1
        if np.isfinite(o['res'][i]):
            return o['xdi'][i]
        return INF

    def row_of(self, date, ticker):
        c = self.ctx
        m = np.where((c.D.to_numpy() == date) & (c.tick_all == ticker))[0]
        return int(m[0]) if len(m) else None

    def strategy(self, spec, tier, frozen=None, top=5):
        """Day by day top `top` of one strategy; returns list of (row, day) picks (filled or not)."""
        c = self.ctx
        o = self.outcomes(spec['exit'])
        rk = c.RANKS[spec['rank']]
        m = c.mask_of(spec['cond'], tier) & c.REGS[spec['reg']] & np.isfinite(rk)
        idx = np.where(m)[0]
        order = np.lexsort((-rk[idx], c.di[idx]))
        idx = idx[order]
        by_day = {}
        for i in idx:
            by_day.setdefault(int(c.di[i]), []).append(int(i))
        frozen = frozen or {}
        days = sorted(set(by_day) | {c.DI[d] for d in frozen if d in c.DI})
        held, picks = {}, []
        for d in days:
            date = c.dates[d]
            if date in frozen:
                chosen = [r for r in (self.row_of(date, t) for t in frozen[date]) if r is not None]
            else:
                chosen = []
                for i in by_day.get(d, []):
                    if len(chosen) >= top:
                        break
                    if held.get(c.tick_all[i], -1) > d:
                        continue
                    chosen.append(i)
            for i in chosen:
                picks.append((i, d))
                h = self.held_until(o, i)
                if h >= 0:
                    held[c.tick_all[i]] = h
        return picks, held

    def combined(self, specs, tier, frozen=None):
        """Combined top 5 of a tier (backtest p2.combined) with frozen days and open positions held."""
        c = self.ctx
        cand = []
        for k, s in enumerate(specs):
            rk = c.RANKS[s['rank']]
            m = c.mask_of(s['cond'], tier) & c.REGS[s['reg']] & np.isfinite(rk)
            idx = np.where(m)[0]
            tmp = pd.DataFrame({'i': idx, 'd': c.di[idx], 'sid': k, 'r': rk[idx]})
            tmp['pct'] = tmp.groupby('d')['r'].rank(pct=True)
            cand.append(tmp)
        cand = pd.concat(cand).sort_values('d')
        groups = {int(d): g for d, g in cand.groupby('d', sort=True)}
        frozen = frozen or {}
        sid_of = {s['id']: k for k, s in enumerate(specs)}
        days = sorted(set(groups) | {c.DI[d] for d in frozen if d in c.DI})
        hist = {k: [] for k in range(len(specs))}
        held, picks = {}, []
        for d in days:
            date = c.dates[d]
            if date in frozen:
                chosen = []
                for t, sid in frozen[date]:
                    r = self.row_of(date, t)
                    if r is not None and sid in sid_of:
                        chosen.append((r, sid_of[sid]))
            else:
                q = {}
                for k, h in hist.items():
                    done = [p for (xd, p) in h if xd < d]
                    q[k] = np.mean(done) if len(done) >= 10 else 0.0
                day = groups.get(d)
                chosen = []
                if day is not None:
                    day = day.assign(q=day.sid.map(q)).sort_values(['q', 'pct'], ascending=[False, False])
                    seen = set()
                    for i, sid in zip(day.i.to_numpy(), day.sid.to_numpy()):
                        if len(chosen) >= 5:
                            break
                        t = c.tick_all[i]
                        if t in seen or held.get(t, -1) > d:
                            continue
                        seen.add(t)
                        chosen.append((int(i), int(sid)))
            for i, k in chosen:
                o = self.outcomes(specs[k]['exit'])
                picks.append((i, d, k))
                h = self.held_until(o, i)
                if h >= 0:
                    held[c.tick_all[i]] = h
                if o['ok'][i] and np.isfinite(o['res'][i]):
                    hist[k].append((o['xdi'][i], o['res'][i] * 100))
        return picks, held


# ------------------------------------------------------------------------------------------- texts

def why(c, cond, i):
    r = c.df.iloc[i]
    out = []
    for a in cond.split(' & '):
        if a == 'squeeze':
            out.append(f"Bollinger menyempit: lebar {fmt_pct(r.bbw, 1, False)}, dekat titik tersempit 60 hari ({fmt_pct(r.bbw_min60, 1, False)})")
        elif a == 'near_ma20':
            out.append(f"Harga {fmt_pct(r.dist_ma20)} dari MA20 ({fmt_px(r.ma20)})")
        elif a in ('p<200', 'p>=200'):
            out.append(f"Harga {fmt_px(r.close)} (rentang 61–199)" if a == 'p<200' else f"Harga {fmt_px(r.close)}")
        elif a in ('up3', 'down3', 'green', 'red'):
            out.append(f"{'Naik' if r.ret >= 0 else 'Turun'} {fmt_pct(abs(r.ret), 1, False)} hari ini")
        elif a in ('rsi<35', 'rsi<30', 'rsi<40'):
            out.append(f"RSI14 {fmt_num(r.rsi14)} (oversold)")
        elif a == 'engulf':
            out.append("Bullish engulfing: candle hijau menelan candle merah kemarin")
        elif a == 'cpos_lo':
            out.append(f"Closing di {fmt_pct(r.close_pos, 0, False)} range harian (tutup lemah)")
        elif a == 'cpos_hi':
            out.append(f"Closing di {fmt_pct(r.close_pos, 0, False)} range harian (tutup kuat)")
        elif a == 'gapdown_today':
            out.append(f"Dibuka gap {fmt_pct(r.gap)} dari closing kemarin")
        elif a == 'red2':
            out.append(f"Merah {int(r.red_streak)} hari beruntun")
        elif a == 'upstreak3':
            out.append(f"Naik {int(r.up_streak)} hari beruntun")
        elif a == 'pos60_lo':
            out.append(f"Posisi di {fmt_pct(r.pos60, 0, False)} range 60 hari (dekat dasar)")
        elif a == 'inside':
            out.append("Inside day: high dan low masih di dalam range kemarin")
        elif a == 'lwick':
            out.append(f"Ekor bawah panjang {fmt_pct(r.lower_wick, 1, False)} (ada pembeli di bawah)")
        elif a in ('volhi', 'vollo'):
            out.append(f"Volume {fmt_num(r.vol_ratio)}x rata-rata 20 hari")
        elif a == 'dry5':
            out.append(f"Volume 5 hari terakhir {fmt_num(r.dry5, 2)}x rata-rata 20 hari (mengering)")
        elif a == 'hl_rise':
            out.append("Low naik 2 hari berturut-turut (higher low)")
        elif a == 'downtrend':
            out.append(f"MA20 ({fmt_px(r.ma20)}) di bawah MA50 ({fmt_px(r.ma50)})")
        elif a == 'nr7':
            out.append("Range harian tersempit dalam 7 hari (NR7)")
        elif a == 'gain60_lo':
            out.append(f"Sudah turun {fmt_pct(abs(r.gain60), 0, False)} dalam 60 hari")
        elif a == 'reversal':
            out.append(f"Reversal: kemarin {fmt_pct(r.ret1_prev)}, hari ini {fmt_pct(r.ret)} dan tutup di atas high kemarin")
        elif a == 'pullback_up':
            out.append(f"Uptrend (MA20 di atas MA50 dan naik), koreksi {fmt_pct(r.dist_ma20)} ke MA20")
        elif a == 'udvr2':
            out.append(f"Volume hari naik {fmt_num(r.udvr10)}x volume hari turun (10 hari)")
    return out


def regime_note(c, reg, d):
    mk = c.mk.loc[c.dates[d]]
    b, r5, chg = mk['breadth_ma5'], mk['ew_ret5'], mk['chg']
    guard = not (chg < -.15)
    if reg in ('weak30', 'weak20'):
        lim = .30 if reg == 'weak30' else .20
        if b < lim:
            return True, f"Pasar lemah: {fmt_pct(b, 0, False)} saham di atas MA20"
        return False, f"Khusus pasar lemah (di bawah {fmt_pct(lim, 0, False)} saham di atas MA20); sekarang {fmt_pct(b, 0, False)}"
    if not guard:
        return False, f"Rem pasar aktif: saham di atas MA20 turun {fmt_num(abs(chg) * 100, 0)} poin dalam 10 hari"
    if reg == 'ret' and not (r5 > -.03):
        return False, f"Pasar turun {fmt_pct(abs(r5), 1, False)} dalam 5 hari (batas 3%)"
    if reg == 'br30' and not (b >= .30):
        return False, f"Hanya {fmt_pct(b, 0, False)} saham di atas MA20 (minimal 30%)"
    if reg == 'br50' and not (b >= .50):
        return False, f"Hanya {fmt_pct(b, 0, False)} saham di atas MA20 (minimal 50%)"
    return True, "Kondisi pasar mendukung"


# ------------------------------------------------------------------------------------------- outputs

def exit_rule(c, ex, i):
    """The exit parameters of one signal row, enough to rebuild TP/SL from any real entry price."""
    p = c.EXITS[ex]

    def val(x):
        if x is None:
            return None
        v = float(np.asarray(x)[i]) if np.ndim(x) else float(x)
        return v if math.isfinite(v) else None

    rule = dict(tp=val(p['tp']), sl=val(p['sl']), hold=int(p['hold']),
                sl_px=val(p.get('sl_px')), tp_px=val(p.get('tp_px')),
                sl_cap=list(p.get('sl_cap', (0.03, 0.10))), tp_cap=list(p.get('tp_cap', (0.03, 0.25))),
                max_ratio=p.get('max_ratio'))
    return rule


def levels(e, r):
    """TP/SL prices for entry e (sim.simulate, TICK_ROUND=True) — mirrored by the app."""
    tp, sl = r['tp'], r['sl']
    if r['sl_px'] is not None:
        sl = min(max(1 - r['sl_px'] / e, r['sl_cap'][0]), r['sl_cap'][1])
    if r['tp_px'] is not None:
        tp = min(max(r['tp_px'] / e - 1, r['tp_cap'][0]), r['tp_cap'][1])
    tpp, slp = e * (1 + tp), e * (1 - sl)
    t1 = tick1(tpp)
    tpp = math.ceil(tpp / t1 - 1e-9) * t1
    t2 = tick1(slp)
    slp = math.floor(slp / t2 + 1e-9) * t2
    mr = r['max_ratio']
    if mr is not None:
        if (e - slp) > mr * (tpp - e) + 1e-9:
            slp = math.ceil(slp / t2 + 1e-9) * t2
        if (e - slp) > mr * (tpp - e) + 1e-9:
            slp = e - mr * (tpp - e)
            t3 = tick1(slp)
            slp = math.ceil(slp / t3 - 1e-9) * t3
    return tpp, slp


def zone_top(c0):
    top = c0 * 1.01
    top = math.floor(top / tick1(top)) * tick1(top)
    return max(top, c0)


def load_frozen(folder, go_live):
    frozen = {}
    for f in sorted(glob.glob(os.path.join(folder, '*.json'))):
        d = os.path.basename(f)[:-5]
        if not re.match(r'\d{4}-\d{2}-\d{2}$', d) or d < go_live:
            continue
        frozen[d] = json.load(open(f))
    return frozen


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--bars', required=True)
    ap.add_argument('--names')
    ap.add_argument('--spec', default=os.path.join(HERE, 'spec.json'))
    ap.add_argument('--picks', help='folder of earlier frozen picks (<date>.json)')
    ap.add_argument('--out', required=True)
    ap.add_argument('--asof', help='pretend the data ends at this date (validation)')
    ap.add_argument('--go-live', dest='go_live')
    args = ap.parse_args()

    spec = json.load(open(args.spec))
    go_live = args.go_live or spec['go_live']
    raw = pd.read_csv(args.bars, dtype={'date': str, 'ticker': str})
    if args.asof:
        raw = raw[raw.date <= args.asof]
    names = {}
    if args.names and os.path.exists(args.names):
        nm = pd.read_csv(args.names, dtype=str).fillna('')
        names = dict(zip(nm.ticker, nm.name))

    c = Context(build_features(raw))
    n_last = int((c.D == c.dates[-1]).sum())
    if n_last < 400 and not args.asof:
        raise SystemExit(f'Data closing {c.dates[-1]} hanya {n_last} saham, radar tidak dipublikasikan')
    eng = Engine(c)
    last = len(c.dates) - 1
    today = c.dates[last]
    frozen_all = load_frozen(args.picks, go_live) if args.picks else {}
    frozen_all.pop(today, None)          # today's picks are always recomputed
    os.makedirs(args.out, exist_ok=True)

    df = c.df
    ret = df['ret'].to_numpy()
    valma = df['val_ma20'].to_numpy()
    rows_today = np.where(c.di == last)[0]

    boards, ledgers, today_frozen, stock_hits = {}, {}, {}, {}
    used_tickers = set()

    def pick_obj(i, s, rank_no):
        o = eng.outcomes(s['exit'])
        c0 = float(c.close[i])
        top = zone_top(c0)
        rule = exit_rule(c, s['exit'], i)
        p = c.EXITS[s['exit']]
        ok = rule['tp'] is not None and rule['sl'] is not None and not ('sl_px' in p and rule['sl_px'] is None) \
            and not ('tp_px' in p and rule['tp_px'] is None)
        tpp, slp = levels(top, rule) if ok else (None, None)
        t = c.tick_all[i]
        used_tickers.add(t)
        return dict(ticker=t, name=names.get(t, ''), rank=rank_no, close=c0, chg=round(float(ret[i]) * 100, 2) if np.isfinite(ret[i]) else 0.0,
                    value_ma20=round(float(valma[i])), entry_high=top, tp=tpp, sl=slp,
                    tp_pct=round((tpp / top - 1) * 100, 2) if ok else None, sl_pct=round((1 - slp / top) * 100, 2) if ok else None,
                    hold=rule['hold'], rule=rule, valid=bool(ok), why=why(c, s['cond'], i))

    def ledger_row(i, d, s, jenis, combined):
        o = eng.outcomes(s['exit'])
        t = c.tick_all[i]
        row = dict(date=c.dates[d], jenis=jenis, sid=s['id'], g=combined, ticker=t, close=float(c.close[i]))
        if d == last:
            row.update(status='menunggu')
            return row
        nd = eng.ndate[i]
        if not o['ok'][i]:
            row.update(status='batal', entry_date=nd if isinstance(nd, str) else None)
            return row
        e = float(o['e'][i])
        tpp, slp = e * (1 + o['tp'][i]), e * (1 - o['sl'][i])
        row.update(entry_date=nd, entry=snap(e), tp=snap(tpp), sl=snap(slp))
        if np.isfinite(o['res'][i]):
            pnl = float(o['res'][i])
            k = int(o['exk'][i])
            why_ = 'TP' if abs(pnl - o['tp'][i]) < 1e-6 else 'SL' if abs(pnl + o['sl'][i]) < 1e-6 else 'Waktu'
            row.update(status=why_, exit_date=c.dates[o['xdi'][i]], exit=snap(e * (1 + pnl)) if why_ != 'Waktu' else float(c.close[i + k]),
                       pnl=round(pnl * 100, 2), hold=k)
        else:
            # still open: mark to the last close of the stock
            j = i
            while j + 1 < len(df) and c.tick_all[j + 1] == t:
                j += 1
            row.update(status='aktif', last=float(c.close[j]), pnl=round((c.close[j] / e - 1) * 100, 2), hold=int(c.di[j] - d))
        return row

    for tier in TIERS:
        boards[tier] = {}
        led = []
        tm = c.tier_mask(tier)
        for jenis in ('profit', 'winrate'):
            board = spec['boards'][tier][jenis]
            specs = board['strategies']
            fz = frozen_all
            out_strats = []
            for s in specs:
                frozen = {d: v[tier][jenis]['s'].get(str(s['id']), []) for d, v in fz.items()
                          if tier in v and jenis in v[tier]}
                picks, held = eng.strategy(s, tier, frozen)
                active, note = regime_note(c, s['reg'], last)
                cond_today = c.mask_of(s['cond'], tier)
                cands = rows_today[cond_today[rows_today]]
                todays = [i for i, d in picks if d == last]
                po = [pick_obj(i, s, n + 1) for n, i in enumerate(todays)]
                held_now = sorted(t for t, h in held.items() if h > last)
                out_strats.append(dict(id=s['id'], active=bool(active), note=note, candidates=int(len(cands)),
                                       held=held_now, picks=po))
                today_frozen.setdefault(tier, {}).setdefault(jenis, {'s': {}, 'g': []})['s'][str(s['id'])] = [p['ticker'] for p in po]
                for i, d in picks:
                    if c.dates[d] >= go_live:
                        led.append(ledger_row(i, d, s, jenis, False))
                # per-stock check for the manual analysis screen
                rk = c.RANKS[s['rank']]
                order = sorted(cands, key=lambda i: -rk[i] if np.isfinite(rk[i]) else INF)
                for n, i in enumerate(order):
                    t = c.tick_all[i]
                    status = 'top5' if i in todays else ('ditahan' if t in held_now else ('pasar' if not active else 'peringkat'))
                    stock_hits.setdefault(t, []).append(dict(tier=tier, jenis=jenis, sid=s['id'], rank=n + 1, of=len(order), status=status))
            frozen_g = {d: [tuple(x) for x in v[tier][jenis]['g']] for d, v in fz.items() if tier in v and jenis in v[tier]}
            gp, _ = eng.combined(specs, tier, frozen_g)
            g_today = [(i, k) for i, d, k in gp if d == last]
            top5 = []
            for n, (i, k) in enumerate(g_today):
                p = pick_obj(i, specs[k], n + 1)
                p['sid'] = specs[k]['id']
                top5.append(p)
            today_frozen[tier][jenis]['g'] = [[p['ticker'], p['sid']] for p in top5]
            for i, d, k in gp:
                if c.dates[d] >= go_live:
                    led.append(ledger_row(i, d, specs[k], jenis, True))
            boards[tier][jenis] = dict(strategies=out_strats, top5=top5)
        ledgers[tier] = led

    # ---- market block
    mk = c.mk.loc[today]
    market = dict(breadth=round(float(mk['breadth']), 3), breadth_ma5=round(float(mk['breadth_ma5']), 3),
                  breadth_chg10=round(float(mk['chg']), 3) if np.isfinite(mk['chg']) else None,
                  ew_ret5=round(float(mk['ew_ret5']), 4) if np.isfinite(mk['ew_ret5']) else None,
                  guard=bool(mk['chg'] < -.15), weak30=bool(mk['breadth_ma5'] < .30), weak20=bool(mk['breadth_ma5'] < .20))
    if market['guard'] and market['weak30']:
        market['label'], market['text'] = 'Rem aktif · pasar lemah', 'Saham di atas MA20 anjlok cepat: strategi pasar normal berhenti dulu, hanya strategi khusus pasar lemah yang aktif.'
    elif market['guard']:
        market['label'], market['text'] = 'Rem aktif', 'Saham di atas MA20 anjlok cepat: strategi pasar normal berhenti dulu sampai pasar tenang.'
    elif market['weak30']:
        market['label'], market['text'] = 'Pasar lemah', 'Sedikit saham di atas MA20: strategi khusus pasar lemah ikut aktif.'
    else:
        market['label'], market['text'] = 'Pasar normal', 'Strategi pasar normal aktif; strategi khusus pasar lemah menunggu.'
    tiers = [dict(key=t, label=TIER_LABEL[t], stocks=int(c.tier_mask(t)[rows_today].sum())) for t in TIERS]

    # ---- recent bars for the chart of every picked stock (60 sessions)
    bars = {}
    for t in sorted(used_tickers):
        x = df[df.ticker == t].tail(60)
        bars[t] = [[d, float(o), float(h), float(l), float(cl), float(v)] for d, o, h, l, cl, v in
                   zip(x.date, x.open, x.high, x.low, x.close, x.volume)]

    wib = datetime.now(timezone(timedelta(hours=7)))
    latest = dict(version=1, date=today, generated_at=wib.strftime('%Y-%m-%dT%H:%M:%S+07:00'), go_live=go_live,
                  calibrated=spec['calibrated'], market=market, tiers=tiers, jenis=spec['jenis'],
                  strategies=spec['strategies'], specs={t: {j: spec['boards'][t][j] for j in spec['boards'][t]} for t in spec['boards']},
                  boards=boards, bars=bars)
    json.dump(latest, open(os.path.join(args.out, 'latest.json'), 'w'), ensure_ascii=False, separators=(',', ':'))

    stocks = {}
    for i in rows_today:
        t = c.tick_all[i]
        r = df.iloc[i]
        stocks[t] = dict(name=names.get(t, ''), close=float(r.close), chg=round(float(r.ret) * 100, 2) if np.isfinite(r.ret) else None,
                         value_ma20=round(float(r.val_ma20)) if np.isfinite(r.val_ma20) else None,
                         rsi14=round(float(r.rsi14), 1) if np.isfinite(r.rsi14) else None,
                         pos60=round(float(r.pos60), 3) if np.isfinite(r.pos60) else None,
                         gain60=round(float(r.gain60), 3) if np.isfinite(r.gain60) else None,
                         vol_ratio=round(float(r.vol_ratio), 2) if np.isfinite(r.vol_ratio) else None,
                         eligible=[t_ for t_ in TIERS if c.tier_mask(t_)[i]],
                         hits=stock_hits.get(t, []))
    json.dump(dict(date=today, stocks=stocks), open(os.path.join(args.out, 'stocks.json'), 'w'), ensure_ascii=False, separators=(',', ':'))

    for tier, led in ledgers.items():
        led = sorted(led, key=lambda r: (r['date'], r['jenis'], r['g'], r['sid'], r['ticker']), reverse=True)
        json.dump(dict(date=today, go_live=go_live, rows=led), open(os.path.join(args.out, f'ledger_{tier}.json'), 'w'),
                  ensure_ascii=False, separators=(',', ':'))
    os.makedirs(os.path.join(args.out, 'picks'), exist_ok=True)
    json.dump(today_frozen, open(os.path.join(args.out, 'picks', f'{today}.json'), 'w'), ensure_ascii=False, separators=(',', ':'))
    n = sum(len(p['picks']) for t in boards.values() for j in t.values() for p in j['strategies'])
    print(f"radar {today}: {len(rows_today)} saham, {n} pick strategi, pasar {market['label']}")


if __name__ == '__main__':
    main()
