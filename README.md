# Lumi Signal IDX

Aplikasi Android untuk screening sinyal beli saham Indonesia (IDX). Radar dihitung dari data harga publik; pemantauan Porto dan broker summary memakai akun Stockbit milik pengguna sendiri (read-only).

## Fitur

- **Radar Besok, sekali ketuk tampil semua.** Screening semua saham IDX dihitung otomatis di server (GitHub Actions, `tools/radar`) setiap hari bursa ±16:30 WIB, dengan mesin yang sama persis dengan backtest 1 tahun. Aplikasi cukup mengunduh hasilnya: kedua jenis sinyal, kelima strategi tiap tingkat likuiditas, dan Top 5 gabungan langsung tampil tanpa memilih strategi satu per satu.
- **Dua jenis sinyal × 5 tingkat likuiditas.** *Profit besar* (target dari ATR atau resisten) dan *Winrate tinggi* (TP minimal 3%). Tingkat: tanpa minimal, ≥ Rp250 jt, ≥ Rp1 M, ≥ Rp5 M, ≥ Rp20 M nilai transaksi rata-rata 20 hari; tiap tingkat punya 5 strateginya sendiri.
- **Simbol unik untuk 32 strategi.** Profit besar memakai lencana kotak, winrate tinggi lencana bulat.
- **Aturan beli & jual yang sama dengan backtest.** Beli di sesi berikutnya di harga pembukaan bila ≤ closing +1% (atau antre di batas itu), TP/SL mengikuti fraksi harga IDX dan dihitung dari harga beli sebenarnya, saham yang masih dipegang tidak direkomendasikan ulang, harga ≤ 60 dihindari.
- **Riwayat Strategi** berisi hasil nyata setiap rekomendasi radar sejak go-live (winrate & akumulasi P/L real). **Riwayat Portofolio** memantau saham yang kamu masukkan Porto dengan harga Stockbit.
- **Cek satu saham** terhadap semua strategi radar, plus chart dan broker summary dari Stockbit.
- **Notifikasi** saat Radar Besok terbit dan saat posisi Porto menyentuh entry, TP, atau SL (WorkManager, tiap ±15 menit).

## Build

APK dibangun otomatis oleh GitHub Actions setiap kali ada push ke `main`, lalu dipublikasikan di halaman **Releases**.

Build lokal: `gradle assembleRelease` (butuh JDK 17, Android SDK 35, dan Gradle 8.11+).

> Catatan: keystore `app/lumi-release.jks` sengaja disimpan di repo agar setiap build bisa meng-update aplikasi yang sudah terpasang tanpa uninstall. Ganti keystore ini jika aplikasi akan didistribusikan ke publik.

## Disclaimer

Bukan produk resmi Stockbit dan bukan rekomendasi investasi. Endpoint Stockbit yang dipakai tidak terdokumentasi resmi dan bisa berubah sewaktu-waktu.
