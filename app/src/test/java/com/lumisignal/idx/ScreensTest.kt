package com.lumisignal.idx

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.lumisignal.idx.engine.RadarPorto
import com.lumisignal.idx.engine.RadarRepo
import com.lumisignal.idx.ui.components.LumiBackground
import com.lumisignal.idx.ui.screens.HistoryScreen
import com.lumisignal.idx.ui.screens.HomeScreen
import com.lumisignal.idx.ui.screens.ManualScreen
import com.lumisignal.idx.ui.screens.RadarPickScreen
import com.lumisignal.idx.ui.screens.RadarScreen
import com.lumisignal.idx.ui.screens.SignalDetailScreen
import com.lumisignal.idx.ui.theme.LumiTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the main screens with a recorded radar so the layout can be reviewed (CI only). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h3000dp-xxhdpi")
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun screens() {
        assumeTrue(System.getProperty("lumi.screens") == "1")
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(ctx.filesDir, "radar").apply { mkdirs() }
        for (n in listOf("latest.json", "stocks.json", "ledger_1M.json")) File(dir, n).writeText(res("screens/$n"))
        Graph.init(ctx)
        val day = RadarRepo.day.value!!
        val tier = "1M"
        val pick = day.board(tier, "winrate")!!.strategies.first { it.picks.isNotEmpty() }
        val sid = pick.id
        val p = pick.picks.first()
        val porto = RadarPorto.add(day, tier, "winrate", sid, p, false, null)

        var screen by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LumiTheme {
                LumiBackground {
                    when (screen) {
                        0 -> HomeScreen(onOpenRadar = {}, onManual = {})
                        1 -> RadarScreen(onBack = {}, onOpenPick = { _, _, _, _, _ -> })
                        2 -> RadarPickScreen(tier, "winrate", sid, p.ticker, false, onBack = {}, onOpenSignal = {})
                        3 -> HistoryScreen(onOpenSignal = {})
                        4 -> ManualScreen(p.ticker, onBack = {}, onOpenPick = { _, _, _, _, _ -> })
                        else -> SignalDetailScreen(porto.id, onBack = {})
                    }
                }
            }
        }
        val names = listOf("1-home", "2-radar", "3-pick", "4-riwayat", "5-cek-saham", "6-porto")
        for (i in names.indices) {
            screen = i
            compose.mainClock.advanceTimeBy(1500)
            Thread.sleep(800)
            compose.mainClock.advanceTimeBy(1500)
            compose.onRoot().captureRoboImage("build/screens/${names[i]}.png")
        }
    }
}
