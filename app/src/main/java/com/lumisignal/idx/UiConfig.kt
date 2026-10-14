package com.lumisignal.idx

/**
 * What the app shows. Everything that is switched off here still exists in the code and the radar data;
 * flip a flag to bring it back without rewriting anything.
 */
object UiConfig {
    /** Liquidity tier picker (all five tiers). Off = the app uses [FIXED_TIER] everywhere. */
    const val SHOW_TIER_PICKER = false

    /** The only tier used while the picker is hidden. */
    const val FIXED_TIER = "250jt"

    /** The five strategies of each signal type. Off = only the combined top 5 is shown. */
    const val SHOW_STRATEGIES = false

    /** Name of the combined top 5. */
    const val TOP5_TITLE = "Top 5 Saham IDX"
}
