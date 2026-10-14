package com.lumisignal.idx.engine

/**
 * The five broker-flow strategies of the first app version. Screening now runs on Radar Besok; these
 * stay only so signals saved by the old version still show their strategy name and evidence.
 */
enum class Strategy(
    val id: Int,
    val title: String,
    val tagline: String,
    val description: String,
    val needsRange: Boolean,
    val risk: String,
) {
    CLOSING_KUAT(
        1, "Closing Kuat Bandar", "Momentum lanjutan",
        "Naik 2% sampai di bawah ARA, ditutup kuat di atas MA20 dengan volume ≥1,5x, bandar (bukan ritel) menguasai pembelian hari itu dan tidak distribusi dalam 5 hari. Entry tidak mengejar close: ditunggu di area harga rata-rata transaksi hari sinyal.",
        false, "Sedang",
    ),
    BREAKOUT(
        2, "Breakout Volume", "Tembus atap dengan ledakan volume",
        "Close menembus high 10 hari dengan volume ≥2x dan candle kuat, harga di atas MA20, belum naik terlalu jauh, dan bandar 5 hari tidak distribusi. Entry di area retest level breakout.",
        false, "Sedang–tinggi",
    ),
    ARA_HUNTER(
        3, "ARA Hunter", "Lanjutan ARA & hampir ARA",
        "Ditutup di ARA atau sangat dekat ARA, bukan hari ke-3 beruntun, pembeli utama bukan ritel dan tidak ikut menjual, serta bandar 5 hari masih akumulasi. Pola gorengan yang sedang dikerek.",
        false, "Tinggi",
    ),
    PANTULAN(
        4, "Pantulan Modal Bandar", "Beli koreksi di harga bandar",
        "Bandar mengumpulkan barang selama 20 hari, harga koreksi ≥4% dengan volume sepi sampai ±5% dari harga rata-rata beli bandar, lalu muncul candle pembalikan. Stop loss paling dekat.",
        true, "Rendah–sedang",
    ),
    GORENGAN_BANGUN(
        5, "Gorengan Bangun", "Akumulasi lama, mulai dikerek",
        "Pembelian 20 hari terkonsentrasi di sedikit broker non-ritel, lalu hari ini muncul pemicu: naik ≥2%, volume ≥1,5x, ditutup kuat, harga belum lari jauh dari modal bandar.",
        true, "Tinggi",
    );

    /** Strategies that read the last 5 sessions of broker flow (distribution guard). */
    val needsRange5: Boolean get() = !needsRange

    companion object {
        fun byId(id: Int): Strategy = entries.firstOrNull { it.id == id } ?: CLOSING_KUAT
    }
}
