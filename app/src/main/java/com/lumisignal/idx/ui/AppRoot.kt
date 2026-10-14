package com.lumisignal.idx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lumisignal.idx.Graph
import com.lumisignal.idx.service.Monitor
import com.lumisignal.idx.ui.components.LumiBackground
import com.lumisignal.idx.ui.screens.HistoryScreen
import com.lumisignal.idx.ui.screens.HomeScreen
import com.lumisignal.idx.ui.screens.LoginFlow
import com.lumisignal.idx.ui.screens.CekSahamScreen
import com.lumisignal.idx.ui.screens.PermissionScreen
import com.lumisignal.idx.ui.screens.RadarPickScreen
import com.lumisignal.idx.ui.screens.RadarScreen
import com.lumisignal.idx.ui.screens.SettingsScreen
import com.lumisignal.idx.ui.screens.SignalDetailScreen
import com.lumisignal.idx.ui.theme.Lumi
import kotlinx.coroutines.flow.MutableStateFlow

/** Navigation requested from outside Compose (notification taps). */
val PendingNav = MutableStateFlow<String?>(null)

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val loggedIn by Graph.session.loggedIn.collectAsStateWithLifecycle()
    val expired by Graph.session.expired.collectAsStateWithLifecycle()
    var onboarded by remember { mutableStateOf(Graph.cache.onboardingDone) }

    when {
        !loggedIn -> LoginFlow()
        expired -> LoginFlow(reason = "Sesi Stockbit kamu sudah berakhir. Login ulang untuk melanjutkan screening dan pemantauan sinyal.")
        !onboarded -> PermissionScreen(onDone = {
            Graph.cache.onboardingDone = true
            onboarded = true
            Monitor.schedule(ctx)
        })
        else -> LumiBackground { MainNav() }
    }
}

@Composable
private fun MainNav() {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val pending by PendingNav.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val route = pending ?: return@LaunchedEffect
        PendingNav.value = null
        try {
            nav.navigate(route) { launchSingleTop = true }
        } catch (e: Exception) {
        }
    }
    val ctx = LocalContext.current
    // Whenever the app comes to the foreground, refresh open signals (throttled) — no manual button.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { Monitor.refreshIfStale(ctx) }
    }
    NavHost(nav, startDestination = "tabs", modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        composable("tabs") { TabsScreen(nav) }
        val openPick: (String, String, Int, String, Boolean) -> Unit = { tier, jenis, sid, ticker, g ->
            nav.navigate("pick/$tier/$jenis/$sid/$ticker/${if (g) 1 else 0}")
        }
        composable("radar") {
            RadarScreen(onBack = { nav.popBackStack() }, onOpenPick = openPick)
        }
        composable("pick/{tier}/{jenis}/{sid}/{ticker}/{g}") { entry ->
            val a = entry.arguments
            RadarPickScreen(
                tier = a?.getString("tier") ?: "1M",
                jenis = a?.getString("jenis") ?: "profit",
                sid = a?.getString("sid")?.toIntOrNull() ?: 0,
                ticker = a?.getString("ticker") ?: "",
                combined = a?.getString("g") == "1",
                onBack = { nav.popBackStack() },
                onOpenSignal = { nav.navigate("detail/$it") },
            )
        }
        composable("detail/{id}") { entry ->
            SignalDetailScreen(entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
        }

    }
}

@Composable
private fun TabsScreen(nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HomeScreen(onScreening = { nav.navigate("radar") { launchSingleTop = true } })
                1 -> HistoryScreen(onOpenSignal = { nav.navigate("detail/$it") })
                2 -> CekSahamScreen(
                    onOpenPick = { tier, jenis, sid, ticker, g -> nav.navigate("pick/$tier/$jenis/$sid/$ticker/${if (g) 1 else 0}") },
                    onOpenSignal = { nav.navigate("detail/$it") },
                )
                else -> SettingsScreen()
            }
        }
        BottomBar(tab) { tab = it }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Lumi.SurfaceHi, Lumi.Surface)))
            .border(1.dp, Lumi.Border, RoundedCornerShape(24.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Icons.Filled.Home, "Beranda", selected == 0, Modifier.weight(1f)) { onSelect(0) }
        NavItem(Icons.AutoMirrored.Filled.List, "Riwayat", selected == 1, Modifier.weight(1f)) { onSelect(1) }
        NavItem(Icons.Filled.Search, "Cek Saham", selected == 2, Modifier.weight(1f)) { onSelect(2) }
        NavItem(Icons.Filled.AccountCircle, "Akun", selected == 3, Modifier.weight(1f)) { onSelect(3) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Lumi.Blue.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) Lumi.Sky else Lumi.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Lumi.Sky else Lumi.TextMuted)
    }
}
