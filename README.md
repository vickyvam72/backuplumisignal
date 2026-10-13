# Lumi Signal IDX

Aplikasi Android untuk screening sinyal beli saham Indonesia (IDX), memakai data dari akun Stockbit milik pengguna sendiri.

## Fitur

- **Login Stockbit lewat WebView.** Token sesi dibaca dari cookie `credentialStorage` milik stockbit.com. Akses read-only, tidak pernah membuat order.
- **5 strategi "Radar Besok"** berbasis harga closing sesi terakhir yang sudah selesai:
  1. Closing Kuat + Bandar Masuk
  2. Breakout dari Tidur
  3. ARA, Bandar Belum Keluar
  4. Pantulan di Modal Bandar
  5. Gorengan Bangun
- **Screening dua tahap.** Tahap 1 mengambil candle harian semua saham (di-cache per tanggal closing). Tahap 2 menganalisa broker summary maksimal 40 kandidat. Hasilnya top 5 saham.
- **Rencana trading** berisi area beli, syarat batal, stop loss struktural (bukan rasio R:R tetap), serta TP1/TP2 dari resistance atau ATR.
- **Halaman analisa** menampilkan bukti bandar (broker summary hari sinyal dan akumulasi 20 hari), bukti teknikal, dan chart candle dengan MA5/MA20 beserta level trading.
- **Analisa manual** satu saham terhadap kelima strategi sekaligus.
- **Riwayat Strategi & Riwayat Portofolio**, masing-masing dengan winrate dan rata-rata P/L.
- **Pemantauan latar belakang** (WorkManager, tiap ±15 menit) dengan notifikasi saat harga menyentuh entry, TP, atau SL.

## Build

APK dibangun otomatis oleh GitHub Actions setiap kali ada push ke `main`, lalu dipublikasikan di halaman **Releases**.

Build lokal: `gradle assembleRelease` (butuh JDK 17, Android SDK 35, dan Gradle 8.11+).

> Catatan: keystore `app/lumi-release.jks` sengaja disimpan di repo agar setiap build bisa meng-update aplikasi yang sudah terpasang tanpa uninstall. Ganti keystore ini jika aplikasi akan didistribusikan ke publik.

## Disclaimer

Bukan produk resmi Stockbit dan bukan rekomendasi investasi. Endpoint Stockbit yang dipakai tidak terdokumentasi resmi dan bisa berubah sewaktu-waktu.
