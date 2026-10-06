package com.pcremote.mobile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NexusV2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startForegroundService(Intent(this, PhoneInboxService::class.java)) }
        setContent {
            val ctx = LocalContext.current
            val prefs = remember { ctx.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE) }
            val savedTheme = prefs.getString("theme", "NEXUS Neon") ?: "NEXUS Neon"
            LaunchedEffect(savedTheme) { NexusUi.applyTheme(savedTheme) }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NexusUi.AccentStrong,
                    secondary = NexusUi.Accent,
                    background = NexusUi.Background,
                    surface = NexusUi.Panel
                )
            ) { NexusV2App() }
        }
    }
}

private data class V2Nav(val page: Int, val label: String, val icon: ImageVector)
private val v2Primary = listOf(
    V2Nav(0,"Início",Icons.Default.Home),
    V2Nav(1,"Apps",Icons.Default.Apps),
    V2Nav(5,"Mouse",Icons.Default.Mouse),
    V2Nav(2,"Controle",Icons.Default.SportsEsports)
)
private val v2Extra = listOf(
    V2Nav(3,"Arquivos",Icons.Default.Folder),
    V2Nav(4,"Tela",Icons.Default.DesktopWindows),
    V2Nav(6,"Teclado",Icons.Default.Keyboard),
    V2Nav(8,"Mídia",Icons.Default.MusicNote),
    V2Nav(7,"Configurações",Icons.Default.Settings),
    V2Nav(9,"Flow",Icons.Default.AutoAwesome)
)

private fun themeMotionMs(): Int = when (NexusUi.currentTheme) {
    "Attack on Titan" -> 150
    "Frutiger Aero" -> 420
    "Dark Souls" -> 520
    "Anime Prism" -> 260
    "Naruto" -> 210
    "Cyber Samurai" -> 170
    "Sakura Night" -> 480
    "Retro CRT" -> 90
    "Arctic Glass" -> 360
    else -> 240
}

@Composable
private fun NexusV2App() {
    var page by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        while (true) {
            if (!Api.connected) {
                val found = Discovery.findPc()
                if (found != null) connectToHost(found, Discovery.lastMethod)
            } else {
                val alive = withContext(Dispatchers.IO) { Discovery.checkHost(Api.host) }
                if (!alive) {
                    Api.connected = false
                    Api.connectionMethod = "Reconectando"
                }
            }
            delay(if (Api.connected) 4200 else 1600)
        }
    }

    LaunchedEffect(page) { if (page != 2 && page != 4) NexusFullscreen.active = false }
    LaunchedEffect(NexusFullscreen.active) {
        activity?.window?.decorView?.systemUiVisibility = if (NexusFullscreen.active) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else View.SYSTEM_UI_FLAG_VISIBLE
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(NexusUi.BackgroundSoft, NexusUi.Background, Color.Black))
        )
    ) {
        NexusThemeBackdrop(Modifier.fillMaxSize())
        if (NexusFullscreen.active && page == 4) {
            SmoothLiveScreen()
        } else if (NexusFullscreen.active && page == 2) {
            Box(Modifier.fillMaxSize()) {
                AdvancedGamepad()
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top=3.dp).width(76.dp).height(12.dp)
                        .background(Color(0x66000000),RoundedCornerShape(99.dp)).clickable { NexusFullscreen.active=false },
                    contentAlignment=Alignment.Center
                ){Box(Modifier.width(38.dp).height(3.dp).background(NexusUi.Accent,RoundedCornerShape(99.dp)))}
            }
        } else if (landscape && page == 2) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(8.dp)) {
                AdvancedGamepad()
                IconButton(
                    onClick={NexusFullscreen.active=true;NexusEffects.play(ctx,NexusEffect.OPEN)},
                    modifier=Modifier.align(Alignment.TopEnd).size(42.dp).background(NexusUi.Panel,RoundedCornerShape(NexusUi.cardRadius.dp))
                ){Icon(Icons.Default.Fullscreen,null,tint=NexusUi.Accent)}
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                V2Header {
                    NexusEffects.play(ctx, NexusEffect.TAP)
                    page = 7
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
                    Crossfade(targetState = page, animationSpec = tween(themeMotionMs()), label = "themePage") { targetPage ->
                        V2Page(targetPage) {
                            NexusEffects.play(ctx, NexusEffect.TAP)
                            page = it
                        }
                    }
                    if (page == 2 || page == 4) {
                        IconButton(
                            onClick = { NexusFullscreen.active = true; NexusEffects.play(ctx,NexusEffect.OPEN) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top=3.dp).size(40.dp)
                                .background(NexusUi.Panel,RoundedCornerShape(NexusUi.cardRadius.dp))
                        ) { Icon(Icons.Default.Fullscreen,"Tela cheia",tint=NexusUi.Accent) }
                    }
                }
                V2BottomNav(page) {
                    NexusEffects.play(ctx, NexusEffect.TICK)
                    page = it
                }
            }
        }
        if (!NexusFullscreen.active) {
            NexusFlowEdge { page = it }
        }
    }
}

@Composable
private fun V2Page(page:Int,go:(Int)->Unit){
    when(page){
        0->V2Home(go)
        1->ReferenceLibrary()
        2->AdvancedGamepad()
        3->PcDrop()
        4->SmoothLiveScreen()
        5->NotebookTouchpad{go(6)}
        6->TextRemote()
        7->NexusSettings()
        8->MediaDeck()
        9->NexusFlowScreen()
        else->V2Home(go)
    }
}

@Composable
private fun V2Header(settings:()->Unit){
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(NexusUi.AccentStrong,NexusUi.Violet)),RoundedCornerShape(NexusUi.cardRadius.dp)),contentAlignment=Alignment.Center){Text("N",color=Color.White,fontWeight=FontWeight.Black,fontSize=20.sp)}
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)){
            Text("Meu PC",color=NexusUi.Text,fontWeight=FontWeight.Black,fontSize=19.sp)
            Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(7.dp).background(if(Api.connected)NexusUi.Success else NexusUi.Muted,CircleShape));Spacer(Modifier.width(6.dp))
                Text(if(Api.connected)"Conectado • ${Api.host}" else "Procurando PC…",color=if(Api.connected)NexusUi.Success else NexusUi.Muted,fontSize=10.sp)
            }
        }
        IconButton(onClick=settings,modifier=Modifier.size(44.dp).background(NexusUi.PanelRaised,RoundedCornerShape(NexusUi.cardRadius.dp))){Icon(Icons.Default.Settings,"Configurações",tint=NexusUi.Text)}
    }
}

@Composable
private fun V2BottomNav(selected:Int,setPage:(Int)->Unit){
    var more by remember{mutableStateOf(false)}
    Surface(color=NexusUi.Panel.copy(alpha=.94f),border=BorderStroke(1.dp,NexusUi.Border),shadowElevation=18.dp){
        Row(Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=5.dp)){
            v2Primary.forEach{item->V2NavItem(item,selected==item.page,Modifier.weight(1f)){setPage(item.page)}}
            Box(Modifier.weight(1f)){
                V2NavItem(V2Nav(-1,"Mais",Icons.Default.MoreHoriz),selected in setOf(3,4,6,7,8,9),Modifier.fillMaxWidth()){more=true}
                DropdownMenu(expanded=more,onDismissRequest={more=false},containerColor=NexusUi.PanelRaised,shape=RoundedCornerShape(NexusUi.cardRadius.dp)){
                    v2Extra.forEach{item->DropdownMenuItem(text={Text(item.label,color=NexusUi.Text)},leadingIcon={Icon(item.icon,null,tint=if(selected==item.page)NexusUi.Accent else NexusUi.Muted)},onClick={more=false;setPage(item.page)})}
                }
            }
        }
    }
}

@Composable
private fun V2NavItem(item:V2Nav,active:Boolean,modifier:Modifier,onClick:()->Unit){
    val scale = if(active) 1f else .96f
    Column(modifier.height(58.dp).clickable(onClick=onClick),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Box(Modifier.size(32.dp).background(if(active)NexusUi.Accent.copy(alpha=.14f) else Color.Transparent,RoundedCornerShape(NexusUi.cardRadius.dp)).padding((1f-scale).dp),contentAlignment=Alignment.Center){Icon(item.icon,item.label,tint=if(active)NexusUi.Accent else NexusUi.Muted,modifier=Modifier.size(20.dp))}
        Spacer(Modifier.height(2.dp));Text(item.label,color=if(active)NexusUi.Text else NexusUi.Muted,fontSize=8.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Medium)
    }
}

private data class V2Action(val title:String,val icon:ImageVector,val page:Int,val accent:Color)

@Composable
private fun V2Home(go:(Int)->Unit){
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var volume by remember{mutableFloatStateOf(70f)}
    var playing by remember{mutableStateOf(false)}
    var power by remember{mutableStateOf<String?>(null)}

    if(power!=null){
        val action=power!!
        AlertDialog(onDismissRequest={power=null},title={Text(when(action){"shutdown"->"Desligar PC?";"restart"->"Reiniciar PC?";else->"Suspender PC?"})},text={Text("O comando será enviado imediatamente.")},confirmButton={Button(onClick={scope.launch(Dispatchers.IO){Api.post("/power",action)};NexusEffects.play(ctx,NexusEffect.SELECT);power=null}){Text("CONFIRMAR")}},dismissButton={TextButton(onClick={power=null}){Text("CANCELAR")}})
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(color=NexusUi.Panel.copy(alpha=.94f),shape=RoundedCornerShape(NexusUi.cardRadius.dp),border=BorderStroke(1.dp,NexusUi.Border)){
            Column(Modifier.padding(14.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(44.dp).background(NexusUi.PanelRaised,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.VolumeUp,null,tint=NexusUi.Text)}
                    Spacer(Modifier.width(10.dp));Text("Volume",color=NexusUi.Text,fontSize=16.sp,modifier=Modifier.weight(1f));Text("${volume.toInt()}%",color=NexusUi.Text,fontWeight=FontWeight.Bold)
                }
                Slider(value=volume,onValueChange={v->val old=volume;volume=v;if(kotlin.math.abs(v-old)>=2f){scope.launch(Dispatchers.IO){Api.post("/key",if(v>old)"VOLUME_UP" else "VOLUME_DOWN")}}},valueRange=0f..100f,colors=SliderDefaults.colors(thumbColor=NexusUi.Text,activeTrackColor=NexusUi.Accent,inactiveTrackColor=NexusUi.Border))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    V2MediaButton(Icons.Default.SkipPrevious,Modifier.weight(1f)){NexusEffects.play(ctx,NexusEffect.TAP);scope.launch(Dispatchers.IO){Api.post("/key","MEDIA_PREV")}}
                    V2MediaButton(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,Modifier.weight(1f)){playing=!playing;NexusEffects.play(ctx,NexusEffect.TAP);scope.launch(Dispatchers.IO){Api.post("/key","MEDIA_PLAY")}}
                    V2MediaButton(Icons.Default.SkipNext,Modifier.weight(1f)){NexusEffects.play(ctx,NexusEffect.TAP);scope.launch(Dispatchers.IO){Api.post("/key","MEDIA_NEXT")}}
                }
            }
        }

        val actions=listOf(
            V2Action("Mouse",Icons.Default.Mouse,5,NexusUi.Accent),V2Action("Teclado",Icons.Default.Keyboard,6,NexusUi.Violet),V2Action("Controle",Icons.Default.SportsEsports,2,NexusUi.AccentStrong),
            V2Action("Tela ao vivo",Icons.Default.DesktopWindows,4,NexusUi.Accent),V2Action("Apps",Icons.Default.Apps,1,NexusUi.Violet),V2Action("Arquivos",Icons.Default.Folder,3,NexusUi.AccentStrong)
        )
        actions.chunked(3).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){row.forEach{item->V2Tile(item.title,item.icon,item.accent,Modifier.weight(1f)){NexusEffects.play(ctx,NexusEffect.TAP);go(item.page)}}}}
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
            V2Tile("Desligar",Icons.Default.PowerSettingsNew,Color(0xFFFF5252),Modifier.weight(1f)){power="shutdown"}
            V2Tile("Reiniciar",Icons.Default.RestartAlt,NexusUi.AccentStrong,Modifier.weight(1f)){power="restart"}
            V2Tile("Suspender",Icons.Default.Bedtime,NexusUi.Violet,Modifier.weight(1f)){power="sleep"}
        }
    }
}

@Composable
private fun V2MediaButton(icon:ImageVector,modifier:Modifier,onClick:()->Unit){Surface(color=NexusUi.PanelRaised,shape=RoundedCornerShape(NexusUi.cardRadius.dp),modifier=modifier.height(50.dp).clickable(onClick=onClick)){Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=NexusUi.Text)}}}

@Composable
private fun V2Tile(title:String,icon:ImageVector,accent:Color,modifier:Modifier,onClick:()->Unit){
    Surface(color=NexusUi.Panel.copy(alpha=.94f),shape=RoundedCornerShape(NexusUi.cardRadius.dp),border=BorderStroke(1.dp,NexusUi.Border),modifier=modifier.aspectRatio(1f).clickable(onClick=onClick)){
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(icon,title,tint=accent,modifier=Modifier.size(29.dp));Spacer(Modifier.height(9.dp));Text(title,color=NexusUi.Text,fontSize=10.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)}
    }
}
