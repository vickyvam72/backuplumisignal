"""Daily price history of every IDX stock from TradingView's public feeds (no login).

1. The scanner API lists all IDX stocks with their company names.
2. The chart websocket returns daily OHLCV (split adjusted) for each stock, a few at a time.
Stocks that still fail after the retries keep their previous bars from --cache, so one bad fetch never
removes a stock (and its published history) from the radar.
"""
import argparse
import csv
import gzip
import json
import random
import re
import string
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from websocket import create_connection


def universe():
    body = {
        "filter": [{"left": "type", "operation": "equal", "right": "stock"}],
        "markets": ["indonesia"],
        "symbols": {"query": {"types": []}, "tickers": []},
        "columns": ["name", "description"],
        "sort": {"sortBy": "name", "sortOrder": "asc"},
        "range": [0, 3000],
    }
    req = urllib.request.Request("https://scanner.tradingview.com/indonesia/scan", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", "User-Agent": "Mozilla/5.0"})
    data = json.load(urllib.request.urlopen(req, timeout=90))
    out = {}
    for item in data.get("data", []):
        exch, sym = item["s"].split(":")
        if exch != "IDX" or not re.match(r"^[A-Z]{4}$", sym):
            continue
        out[sym] = item["d"][1] or ""
    return out


def _gen(p):
    return p + "".join(random.choice(string.ascii_lowercase) for _ in range(12))


def _msg(f, p):
    m = json.dumps({"m": f, "p": p}, separators=(",", ":"))
    return "~m~" + str(len(m)) + "~m~" + m


def fetch(sym, n):
    for attempt in range(4):
        try:
            ws = create_connection("wss://data.tradingview.com/socket.io/websocket",
                                   header=["Origin: https://data.tradingview.com"], timeout=20)
            cs = _gen("cs_")
            ws.send(_msg("set_auth_token", ["unauthorized_user_token"]))
            ws.send(_msg("chart_create_session", [cs, ""]))
            ws.send(_msg("resolve_symbol", [cs, "symbol_1", '={"symbol":"IDX:%s","adjustment":"splits","session":"regular"}' % sym]))
            ws.send(_msg("create_series", [cs, "s1", "s1", "symbol_1", "1D", n]))
            raw, t0 = "", time.time()
            while time.time() - t0 < 25:
                x = ws.recv()
                raw += x
                if "series_completed" in x or "symbol_error" in x:
                    break
                if x.startswith("~m~") and "~h~" in x:
                    ws.send(x)
            ws.close()
            bars = []
            for m in re.findall(r'"s":\[(.*?)\}\]', raw):
                for b in re.findall(r'\{"i":\d+,"v":\[(.*?)\]', m + "}]"):
                    v = b.split(",")
                    if len(v) >= 5:
                        bars.append(v[:6])
            if bars or "symbol_error" in raw:
                return bars
        except Exception:
            pass
        time.sleep(1 + attempt)
    return []


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bars", required=True, help="output CSV(.gz)")
    ap.add_argument("--names", required=True, help="output CSV ticker,name")
    ap.add_argument("--cache", help="previous bars CSV(.gz) used for stocks that fail today")
    ap.add_argument("--n", type=int, default=320)
    ap.add_argument("--workers", type=int, default=4)
    args = ap.parse_args()

    names = universe()
    tickers = sorted(names)
    print(len(tickers), "IDX stocks", flush=True)
    rows, empty = [], []

    def job(t):
        return t, fetch(t, args.n)

    with ThreadPoolExecutor(args.workers) as ex:
        for t, bars in ex.map(job, tickers):
            if not bars:
                empty.append(t)
            for v in bars:
                ts = float(v[0])
                d = time.strftime("%Y-%m-%d", time.gmtime(ts + 7 * 3600))
                rows.append((d, t, v[1], v[2], v[3], v[4], v[5] if len(v) > 5 else ""))
    # one more slow pass for the failures
    if empty:
        retry, empty = empty, []
        for t in retry:
            bars = fetch(t, args.n)
            if not bars:
                empty.append(t)
            for v in bars:
                d = time.strftime("%Y-%m-%d", time.gmtime(float(v[0]) + 7 * 3600))
                rows.append((d, t, v[1], v[2], v[3], v[4], v[5] if len(v) > 5 else ""))
    kept = 0
    if empty and args.cache:
        try:
            opener = gzip.open if args.cache.endswith(".gz") else open
            with opener(args.cache, "rt") as f:
                miss = set(empty)
                for r in csv.DictReader(f):
                    if r["ticker"] in miss:
                        rows.append((r["date"], r["ticker"], r["open"], r["high"], r["low"], r["close"], r["volume"]))
                        kept += 1
        except FileNotFoundError:
            pass
    opener = gzip.open if args.bars.endswith(".gz") else open
    with opener(args.bars, "wt", newline="") as f:
        w = csv.writer(f)
        w.writerow(["date", "ticker", "open", "high", "low", "close", "volume"])
        w.writerows(sorted(rows))
    with open(args.names, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["ticker", "name"])
        w.writerows(sorted(names.items()))
    print(f"{len(rows)} bars, {len(tickers) - len(empty)} stocks fetched, {len(empty)} failed ({kept} cached rows reused)", flush=True)


if __name__ == "__main__":
    main()
