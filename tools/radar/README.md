# Radar Besok (server)

Screening harian semua saham IDX dengan strategi hasil kalibrasi backtest (v9). Dijalankan GitHub Actions
(`.github/workflows/radar.yml`) setiap hari bursa pukul ±16:25 WIB (diulang 16:45, 17:15, 18:00 bila GitHub terlambat), hasilnya disimpan di branch `market-data`
folder `radar/` dan dibaca aplikasi Lumi Signal tanpa login.

- `fetch_tv.py` — daftar saham + candle harian dari TradingView.
- `features.py`, `sim.py` — mesin yang sama persis dengan backtest (indikator, kondisi pasar, entry zona close s.d. +1%, TP/SL di fraksi harga).
- `radar.py` — top 5 per strategi, top 5 gabungan per tingkat likuiditas, saham yang masih di-hold tidak direkomendasikan lagi.
- `spec.json` — 5 strategi per jenis sinyal per tingkat likuiditas, beserta statistik backtest.

File keluaran: `latest.json` (radar hari ini), `stocks.json` (cek per saham), `ledger_<tingkat>.json`
(hasil nyata setiap rekomendasi sejak go-live), `picks/<tanggal>.json` (rekomendasi yang sudah terbit, dibekukan).
