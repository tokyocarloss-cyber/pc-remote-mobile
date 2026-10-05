package com.pcremote.mobile

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NexusPalette(
    val name: String,
    val background: Color,
    val backgroundSoft: Color,
    val panel: Color,
    val panelRaised: Color,
    val accent: Color,
    val accentStrong: Color,
    val violet: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val success: Color
)

val NexusThemes = listOf(
    NexusPalette("NEXUS Neon", Color(0xFF05070A), Color(0xFF090D12), Color(0xF20D1218), Color(0xFF121922), Color(0xFF60E6FF), Color(0xFF168CFF), Color(0xFF7668FF), Color(0xFFF6F8FB), Color(0xFF8E99A8), Color(0xFF202A36), Color(0xFF62E69A)),
    NexusPalette("Attack on Titan", Color(0xFF090B09), Color(0xFF111510), Color(0xF2161A14), Color(0xFF20261D), Color(0xFFD2B36A), Color(0xFF587A4A), Color(0xFF8B3E33), Color(0xFFF4EEDF), Color(0xFF9B9A8E), Color(0xFF32392D), Color(0xFF7CC66A)),
    NexusPalette("Frutiger Aero", Color(0xFF071A24), Color(0xFF0C2A31), Color(0xE9143440), Color(0xFF1D4852), Color(0xFF66F4FF), Color(0xFF26A7FF), Color(0xFF67E38E), Color(0xFFF3FFFF), Color(0xFF9BC8D0), Color(0xFF2D6370), Color(0xFF79F58D)),
    NexusPalette("Dark Souls", Color(0xFF080706), Color(0xFF110F0D), Color(0xF2181512), Color(0xFF241F1A), Color(0xFFE2A754), Color(0xFF9C5D2A), Color(0xFF6A4537), Color(0xFFF1E8DC), Color(0xFF9D9187), Color(0xFF3B3027), Color(0xFFC5A85B)),
    NexusPalette("Anime Prism", Color(0xFF090713), Color(0xFF121027), Color(0xF2171530), Color(0xFF211D42), Color(0xFF70F0FF), Color(0xFFFF5FB6), Color(0xFF8A75FF), Color(0xFFFFF7FF), Color(0xFFB0A5C4), Color(0xFF342E56), Color(0xFF7BFFA7)),
    NexusPalette("Naruto", Color(0xFF110904), Color(0xFF1D1008), Color(0xF228150A), Color(0xFF351C0E), Color(0xFFFFA12F), Color(0xFFE35D1F), Color(0xFF2B78D6), Color(0xFFFFF6EA), Color(0xFFC3A88C), Color(0xFF55301A), Color(0xFF7AE06A)),
    NexusPalette("Cyber Samurai", Color(0xFF07060A), Color(0xFF0D0A12), Color(0xF215101C), Color(0xFF201629), Color(0xFFFF3C7D), Color(0xFF8F48FF), Color(0xFF2DD9FF), Color(0xFFFFF7FB), Color(0xFFA594B1), Color(0xFF38263F), Color(0xFF4FFFC1)),
    NexusPalette("Sakura Night", Color(0xFF0D0810), Color(0xFF160D19), Color(0xF21C1021), Color(0xFF29162F), Color(0xFFFF8CCB), Color(0xFFD85EFF), Color(0xFF8E78FF), Color(0xFFFFF6FB), Color(0xFFB39EAF), Color(0xFF402A45), Color(0xFF88F0B0)),
    NexusPalette("Retro CRT", Color(0xFF020704), Color(0xFF06110A), Color(0xF20A170E), Color(0xFF10251A), Color(0xFF7DFF8A), Color(0xFF20D85A), Color(0xFFB8FF66), Color(0xFFE8FFE9), Color(0xFF7FA58A), Color(0xFF1D3D27), Color(0xFF7DFF8A)),
    NexusPalette("Arctic Glass", Color(0xFF061015), Color(0xFF0A1B24), Color(0xE9122630), Color(0xFF183643), Color(0xFFC8F8FF), Color(0xFF55CFFF), Color(0xFF87A9FF), Color(0xFFF7FDFF), Color(0xFFA5BBC4), Color(0xFF284955), Color(0xFF7DF2D1))
)

object NexusUi {
    var currentTheme by mutableStateOf("NEXUS Neon")
        private set
    var Background by mutableStateOf(NexusThemes.first().background); private set
    var BackgroundSoft by mutableStateOf(NexusThemes.first().backgroundSoft); private set
    var Panel by mutableStateOf(NexusThemes.first().panel); private set
    var PanelRaised by mutableStateOf(NexusThemes.first().panelRaised); private set
    var Accent by mutableStateOf(NexusThemes.first().accent); private set
    var AccentStrong by mutableStateOf(NexusThemes.first().accentStrong); private set
    var Violet by mutableStateOf(NexusThemes.first().violet); private set
    var Text by mutableStateOf(NexusThemes.first().text); private set
    var Muted by mutableStateOf(NexusThemes.first().muted); private set
    var Border by mutableStateOf(NexusThemes.first().border); private set
    var Success by mutableStateOf(NexusThemes.first().success); private set

    fun applyTheme(name: String) {
        val p = NexusThemes.firstOrNull { it.name == name } ?: NexusThemes.first()
        currentTheme = p.name
        Background = p.background
        BackgroundSoft = p.backgroundSoft
        Panel = p.panel
        PanelRaised = p.panelRaised
        Accent = p.accent
        AccentStrong = p.accentStrong
        Violet = p.violet
        Text = p.text
        Muted = p.muted
        Border = p.border
        Success = p.success
    }
}

object NexusFullscreen { var active by mutableStateOf(false) }

private data class NavItem(val page: Int, val label: String, val icon: ImageVector)
private val primaryNav = listOf(
    NavItem(0, "Início", Icons.Default.Home),
    NavItem(1, "Biblioteca", Icons.Default.Apps),
    NavItem(2, "Mouse", Icons.Default.Mouse),
    NavItem(3, "Controle", Icons.Default.SportsEsports)
)
private val extraNav = listOf(
    NavItem(4, "Drop", Icons.Default.SwapHoriz),
    NavItem(5, "Tela", Icons.Default.DesktopWindows),
    NavItem(6, "Teclado", Icons.Default.Keyboard),
    NavItem(7, "Mídia", Icons.Default.MusicNote),
    NavItem(8, "Configurações", Icons.Default.Settings)
)

@Composable
private fun RoutedContent(page: Int, setPage: (Int) -> Unit, content: @Composable () -> Unit) {
    when (page) {
        2 -> NotebookTouchpad { setPage(6) }
        5 -> SmoothLiveScreen()
        else -> content()
    }
}

@Composable
fun PremiumShell(page: Int, setPage: (Int) -> Unit, content: @Composable () -> Unit) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current as? Activity

    LaunchedEffect(page) { if (page != 3 && page != 5) NexusFullscreen.active = false }
    LaunchedEffect(NexusFullscreen.active) {
        activity?.window?.decorView?.systemUiVisibility = if (NexusFullscreen.active) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else View.SYSTEM_UI_FLAG_VISIBLE
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NexusUi.BackgroundSoft, NexusUi.Background, Color.Black)))) {
        if (NexusFullscreen.active && (page == 3 || page == 5)) {
            Box(Modifier.fillMaxSize()) { RoutedContent(page, setPage, content) }
            if (page == 3) {
                IconButton(
                    onClick = { NexusFullscreen.active = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(44.dp).background(NexusUi.Panel, RoundedCornerShape(14.dp)).border(1.dp, NexusUi.Border, RoundedCornerShape(14.dp))
                ) { Icon(Icons.Default.FullscreenExit, "Sair da tela cheia", tint = NexusUi.Text) }
            }
        } else if (landscape) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp)) {
                SideRail(page, setPage)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp)) {
                    RoutedContent(page, setPage, content)
                    if (page == 3 || page == 5) FullscreenButton()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
                Spacer(Modifier.height(8.dp))
                PremiumStatus { setPage(8) }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    RoutedContent(page, setPage, content)
                    if (page == 3 || page == 5) FullscreenButton()
                }
                Spacer(Modifier.height(10.dp))
                BottomDock(page, setPage)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun BoxScope.FullscreenButton() {
    IconButton(
        onClick = { NexusFullscreen.active = true },
        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(40.dp).background(NexusUi.PanelRaised, RoundedCornerShape(13.dp)).border(1.dp, NexusUi.Border, RoundedCornerShape(13.dp))
    ) { Icon(Icons.Default.Fullscreen, "Tela cheia", tint = NexusUi.Accent) }
}

@Composable
fun PremiumStatus(settings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Brush.linearGradient(listOf(NexusUi.AccentStrong, NexusUi.Violet)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("N", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp) }
            Spacer(Modifier.width(10.dp))
            Column { Text("NEXUS", color = NexusUi.Text, fontWeight = FontWeight.Black, fontSize = 20.sp); Text("PC REMOTE", color = NexusUi.Muted, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.4.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = if (Api.connected) NexusUi.Success.copy(alpha = .10f) else NexusUi.PanelRaised, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (Api.connected) NexusUi.Success.copy(alpha = .35f) else NexusUi.Border)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (Api.connected) NexusUi.Success else NexusUi.Muted, RoundedCornerShape(99.dp))); Spacer(Modifier.width(6.dp)); Text(if (Api.connected) "ONLINE" else "BUSCANDO", color = if (Api.connected) NexusUi.Success else NexusUi.Muted, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = settings, modifier = Modifier.size(40.dp).background(NexusUi.PanelRaised, RoundedCornerShape(14.dp)).border(1.dp, NexusUi.Border, RoundedCornerShape(14.dp))) { Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = NexusUi.Text, modifier = Modifier.size(19.dp)) }
        }
    }
}

@Composable
fun BottomDock(sel: Int, set: (Int) -> Unit) {
    var moreOpen by remember { mutableStateOf(false) }
    Surface(color = NexusUi.Panel, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NexusUi.Border), shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            primaryNav.forEach { item -> DockItem(item, sel == item.page, Modifier.weight(1f)) { set(item.page) } }
            Box(Modifier.weight(1f)) {
                DockItem(NavItem(-1, "Mais", Icons.Default.MoreHoriz), sel in 4..8, Modifier.fillMaxWidth()) { moreOpen = true }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }, containerColor = NexusUi.PanelRaised, shape = RoundedCornerShape(18.dp)) {
                    extraNav.forEach { item -> DropdownMenuItem(text = { Text(item.label, color = NexusUi.Text) }, leadingIcon = { Icon(item.icon, null, tint = if (sel == item.page) NexusUi.Accent else NexusUi.Muted) }, onClick = { moreOpen = false; set(item.page) }) }
                }
            }
        }
    }
}

@Composable
fun SideRail(sel: Int, set: (Int) -> Unit) {
    var moreOpen by remember { mutableStateOf(false) }
    Surface(color = NexusUi.Panel, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NexusUi.Border), modifier = Modifier.width(68.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(NexusUi.AccentStrong, NexusUi.Violet)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("N", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp) }
            primaryNav.forEach { item -> RailItem(item, sel == item.page) { set(item.page) } }
            Box {
                RailItem(NavItem(-1, "Mais", Icons.Default.MoreHoriz), sel in 4..8) { moreOpen = true }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }, containerColor = NexusUi.PanelRaised, shape = RoundedCornerShape(18.dp)) {
                    extraNav.forEach { item -> DropdownMenuItem(text = { Text(item.label, color = NexusUi.Text) }, leadingIcon = { Icon(item.icon, null, tint = if (sel == item.page) NexusUi.Accent else NexusUi.Muted) }, onClick = { moreOpen = false; set(item.page) }) }
                }
            }
        }
    }
}

@Composable
private fun DockItem(item: NavItem, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (active) 1f else .96f, label = "dockScale")
    val contentColor by animateColorAsState(if (active) NexusUi.Accent else NexusUi.Muted, label = "dockColor")
    Column(modifier.height(58.dp).scale(scale).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(32.dp).background(if (active) NexusUi.Accent.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(item.icon, item.label, tint = contentColor, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(2.dp)); Text(item.label, color = if (active) NexusUi.Text else NexusUi.Muted, fontSize = 8.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun RailItem(item: NavItem, active: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).background(if (active) NexusUi.Accent.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(15.dp)).border(1.dp, if (active) NexusUi.Accent.copy(alpha = .24f) else Color.Transparent, RoundedCornerShape(15.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(item.icon, item.label, tint = if (active) NexusUi.Accent else NexusUi.Muted, modifier = Modifier.size(22.dp))
    }
}
