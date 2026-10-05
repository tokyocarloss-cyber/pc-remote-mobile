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
import androidx.compose.foundation.shape.CircleShape
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

object NexusUi {
    val Background = Color(0xFF05070A)
    val BackgroundSoft = Color(0xFF090D12)
    val Panel = Color(0xF20D1218)
    val PanelRaised = Color(0xFF121922)
    val Accent = Color(0xFF60E6FF)
    val AccentStrong = Color(0xFF168CFF)
    val Violet = Color(0xFF7668FF)
    val Text = Color(0xFFF6F8FB)
    val Muted = Color(0xFF8E99A8)
    val Border = Color(0xFF202A36)
    val Success = Color(0xFF62E69A)
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

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0B1119), NexusUi.Background, Color(0xFF030406)))
        )
    ) {
        if (NexusFullscreen.active && (page == 3 || page == 5)) {
            Box(Modifier.fillMaxSize()) { RoutedContent(page, setPage, content) }
            IconButton(
                onClick = { NexusFullscreen.active = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(44.dp).background(Color(0xCC0D1218), RoundedCornerShape(14.dp)).border(1.dp, NexusUi.Border, RoundedCornerShape(14.dp))
            ) { Icon(Icons.Default.FullscreenExit, "Sair da tela cheia", tint = NexusUi.Text) }
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
        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(40.dp).background(Color(0xCC121922), RoundedCornerShape(13.dp)).border(1.dp, NexusUi.Border, RoundedCornerShape(13.dp))
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
                    Box(Modifier.size(7.dp).background(if (Api.connected) NexusUi.Success else NexusUi.Muted, CircleShape)); Spacer(Modifier.width(6.dp)); Text(if (Api.connected) "ONLINE" else "BUSCANDO", color = if (Api.connected) NexusUi.Success else NexusUi.Muted, fontWeight = FontWeight.Bold, fontSize = 9.sp)
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
