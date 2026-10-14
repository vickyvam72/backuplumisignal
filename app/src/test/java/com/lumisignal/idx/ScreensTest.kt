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
import com.lumisignal.idx.ui.screens.CekSahamScreen
import com.lumisignal.idx.ui.screens.RadarPickScreen
import com.lumisignal.idx.ui.screens.SetupScreen
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
@Config(sdk = [34], qualifiers = "w400dp-h3000dp-xxhdpi", application = ScreensTest.TestApp::class)
class ScreensTest {
    /** Plain application: no WorkManager scheduling under Robolectric. */
    class TestApp : android.app.Application()

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
        com.lumisignal.idx.engine.Scanner.testSource = { sym ->
            com.lumisignal.idx.engine.ManualResult(sym, p.name, day.date, day.barsOf(sym), null, null, null)
        }

        var screen by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LumiTheme {
                LumiBackground {
                    when (screen) {
                        0 -> HomeScreen(onScreening = {})
                        1 -> SetupScreen(onBack = {}, onDone = {}, autoStart = false)
                        2 -> SetupScreen(onBack = {}, onDone = {}, previewAllOk = true)
                        3 -> HomeScreen(onScreening = {})
                        4 -> RadarPickScreen(tier, "winrate", sid, p.ticker, false, onBack = {}, onOpenSignal = {})
                        5 -> HistoryScreen(onOpenSignal = {})
                        6 -> CekSahamScreen(onOpenPick = { _, _, _, _, _ -> }, onOpenSignal = {}, initialSymbol = p.ticker)
                        7 -> SignalDetailScreen(porto.id, onBack = {})
                        else -> com.lumisignal.idx.ui.screens.BacktestScreen()
                    }
                }
            }
        }
        val names = listOf("1-home-awal", "2-setup", "3-setup-siap", "4-beranda-sinyal", "5-pick", "6-riwayat", "7-cek-saham", "8-porto", "9-backtest")
        for (i in names.indices) {
            if (i == 3) {
                RadarRepo.setupDone = true
                RadarPorto.saveAll(day)
            }
            screen = i
            if (i == 8) repeat(8) { Thread.sleep(1000); compose.mainClock.advanceTimeBy(500) }
            compose.mainClock.advanceTimeBy(1500)
            Thread.sleep(800)
            compose.mainClock.advanceTimeBy(1500)
            compose.onRoot().captureRoboImage("build/screens/${names[i]}.png")
        }
        // Share cards (pictures sent through the share sheet).
        val share = com.lumisignal.idx.share.ShareCards
        val top = day.board("250jt", "profit")?.top5?.firstOrNull { it.valid }
            ?: day.boards.values.flatMap { it.values }.flatMap { it.top5 }.first { it.valid }
        fun save(name: String, b: android.graphics.Bitmap) =
            File("build/screens/$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        save("11-share-sinyal", share.pickBitmap(ctx, day, "profit", top, top.sid ?: 0))
        save("12-share-semua", share.dayBitmap(ctx, day, "250jt"))
        val done = porto.copy(status = com.lumisignal.idx.data.SignalStatus.TP, entryPrice = p.entryHigh, entryDate = day.date,
            exitPrice = p.tp, exitDate = day.date)
        save("13-share-riwayat", share.signalBitmap(ctx, done))
    }
}
