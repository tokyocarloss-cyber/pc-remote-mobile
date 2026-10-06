package com.pcremote.mobile

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.sin

data class PcFsItem(val name:String,val path:String,val dir:Boolean,val size:Long=0L,val mtime:Long=0L)

private data class FlowAction(val id:String,val label:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,val page:Int?=null)

private val FlowActions = listOf(
    FlowAction("files","Arquivos PC",Icons.Default.Folder,9),
    FlowAction("recent","Recentes",Icons.Default.History,9),
    FlowAction("screen","Tela",Icons.Default.DesktopWindows,4),
    FlowAction("mouse","Mouse",Icons.Default.Mouse,5),
    FlowAction("gamepad","Controle",Icons.Default.SportsEsports,2),
    FlowAction("apps","Apps",Icons.Default.Apps,1),
    FlowAction("capture","Print → celular",Icons.Default.PhotoCamera,null),
    FlowAction("record","Gravar tela",Icons.Default.FiberManualRecord,null)
)
private val DefaultFlowActionIds = FlowActions.map{it.id}.toSet()

@Composable
fun NexusFlowEdge(go:(Int)->Unit){
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    val prefs=remember{ctx.getSharedPreferences("nexus_flow",Context.MODE_PRIVATE)}
    var open by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    val enabled=remember(open){prefs.getStringSet("actions",DefaultFlowActionIds)?.toSet()?:DefaultFlowActionIds}
    var dragTotal by remember{mutableFloatStateOf(0f)}

    Box(Modifier.fillMaxSize()){
        Box(
            Modifier.align(Alignment.CenterEnd)
                .width(14.dp).height(118.dp)
                .background(NexusUi.Accent.copy(alpha=.18f),RoundedCornerShape(topStart=14.dp,bottomStart=14.dp))
                .pointerInput(Unit){
                    detectHorizontalDragGestures(
                        onDragStart={dragTotal=0f},
                        onHorizontalDrag={change,amount->
                            change.consume();dragTotal+=amount
                            if(dragTotal < -26f) open=true
                        }
                    )
                }
                .clickable{open=true}
        )

        AnimatedVisibility(
            visible=open,
            enter=slideInHorizontally(initialOffsetX={it})+fadeIn(),
            exit=slideOutHorizontally(targetOffsetX={it})+fadeOut(),
            modifier=Modifier.align(Alignment.CenterEnd)
        ){
            Surface(
                color=NexusUi.Panel.copy(alpha=.98f),
                shape=RoundedCornerShape(topStart=26.dp,bottomStart=26.dp),
                border=BorderStroke(1.dp,NexusUi.Border),
                shadowElevation=20.dp,
                modifier=Modifier.width(286.dp).heightIn(min=360.dp,max=620.dp)
            ){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text("NEXUS FLOW",color=NexusUi.Text,fontWeight=FontWeight.Black)
                            Text("Ações instantâneas",color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick={open=false}){Icon(Icons.Default.ChevronRight,null,tint=NexusUi.Text)}
                    }
                    enabled.mapNotNull{id->FlowActions.firstOrNull{it.id==id}}.chunked(2).forEach{row->
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            row.forEach{a->
                                Surface(
                                    color=NexusUi.PanelRaised,
                                    shape=RoundedCornerShape(NexusUi.cardRadius.dp),
                                    border=BorderStroke(1.dp,NexusUi.Border),
                                    modifier=Modifier.weight(1f).height(84.dp).clickable{
                                        when(a.id){
                                            "capture"->scope.launch{
                                                status="Capturando…"
                                                status=if(capturePcToGallery(ctx))"Print salvo na Galeria" else "Falha ao capturar"
                                            }
                                            "record"->scope.launch(Dispatchers.IO){
                                                val ok=RemoteClient.post("/record-toggle",ByteArray(0))
                                                withContext(Dispatchers.Main){status=if(ok)"Gravação alternada • ao parar, o vídeo vem para a Galeria" else "Falha na gravação"}
                                            }
                                            else->{a.page?.let(go);open=false}
                                        }
                                    }
                                ){
                                    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                        Icon(a.icon,null,tint=if(a.id=="record")androidx.compose.ui.graphics.Color(0xFFFF5A5F) else NexusUi.Accent,modifier=Modifier.size(24.dp))
                                        Spacer(Modifier.height(7.dp))
                                        Text(a.label,color=NexusUi.Text,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                                    }
                                }
                            }
                            if(row.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                    if(status.isNotBlank())Text(status,color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
                    TextButton(onClick={go(9);open=false},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Tune,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("PERSONALIZAR FLOW")
                    }
                }
            }
        }
    }
}

@Composable
fun NexusFlowScreen(){
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    val prefs=remember{ctx.getSharedPreferences("nexus_flow",Context.MODE_PRIVATE)}
    var path by remember{mutableStateOf("")}
    var parent by remember{mutableStateOf("")}
    var list by remember{mutableStateOf<List<PcFsItem>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    var recent by remember{mutableStateOf(false)}
    var favorites by remember{mutableStateOf(prefs.getStringSet("folders",emptySet())?.toSet().orEmpty())}
    var enabledActions by remember{mutableStateOf(prefs.getStringSet("actions",DefaultFlowActionIds)?.toSet()?:DefaultFlowActionIds)}
    var refresh by remember{mutableIntStateOf(0)}
    var showFolderWheel by remember{mutableStateOf(false)}

    LaunchedEffect(path,recent,refresh,Api.host){
        if(Api.host.isBlank())return@LaunchedEffect
        loading=true
        val result=withContext(Dispatchers.IO){
            if(recent){
                val raw=RemoteClient.get("/fs/recent")?:return@withContext Triple("Recentes","",emptyList<PcFsItem>())
                val a=JSONArray(String(raw))
                Triple("Recentes","",(0 until a.length()).map{i->parseFsItem(a.getJSONObject(i))})
            }else{
                val encoded=URLEncoder.encode(path,"UTF-8")
                val raw=RemoteClient.get("/fs/list?path=$encoded")?:return@withContext Triple(path,parent,emptyList<PcFsItem>())
                val o=JSONObject(String(raw));val a=o.getJSONArray("items")
                Triple(o.optString("path"),o.optString("parent"),(0 until a.length()).map{i->parseFsItem(a.getJSONObject(i))})
            }
        }
        path=result.first;parent=result.second;list=result.third;loading=false
    }

    if(showFolderWheel){
        val radialFavorites=favorites.take(8).toList()
        Dialog(onDismissRequest={showFolderWheel=false}){
            Surface(
                color=NexusUi.PanelRaised,
                shape=RoundedCornerShape(999.dp),
                border=BorderStroke(1.dp,NexusUi.Border),
                shadowElevation=24.dp,
                modifier=Modifier.size(330.dp)
            ){
                Box(Modifier.fillMaxSize()){
                    if(radialFavorites.isEmpty()){
                        Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){
                            Icon(Icons.Default.StarBorder,null,tint=NexusUi.Accent)
                            Spacer(Modifier.height(6.dp))
                            Text("Favorite pastas primeiro",color=NexusUi.Text,fontWeight=FontWeight.Bold)
                        }
                    }else{
                        radialFavorites.forEachIndexed{index,fav->
                            val angle=(Math.PI*2.0*index/radialFavorites.size)-Math.PI/2.0
                            val x=137f+(110f*cos(angle)).toFloat()
                            val y=137f+(110f*sin(angle)).toFloat()
                            Surface(
                                color=NexusUi.BackgroundSoft,
                                shape=RoundedCornerShape(999.dp),
                                border=BorderStroke(1.dp,NexusUi.Accent.copy(alpha=.55f)),
                                modifier=Modifier.offset(x.dp,y.dp).size(58.dp).clickable{
                                    recent=false
                                    path=fav
                                    showFolderWheel=false
                                }
                            ){
                                Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                    Icon(Icons.Default.Folder,null,tint=NexusUi.Accent,modifier=Modifier.size(20.dp))
                                    Text(fav.substringAfterLast('\\').ifBlank{"PC"}.take(8),color=NexusUi.Text,style=MaterialTheme.typography.labelSmall,maxLines=1)
                                }
                            }
                        }
                        Surface(
                            color=NexusUi.AccentStrong,
                            shape=RoundedCornerShape(999.dp),
                            modifier=Modifier.align(Alignment.Center).size(94.dp)
                        ){
                            Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                Icon(Icons.Default.TrackChanges,null,tint=androidx.compose.ui.graphics.Color.White)
                                Text("PASTAS",color=androidx.compose.ui.graphics.Color.White,fontWeight=FontWeight.Black,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("NEXUS Flow",color=NexusUi.Text,fontWeight=FontWeight.Black,style=MaterialTheme.typography.headlineSmall)
                Text("Arquivos do PC, favoritos e ações rápidas",color=NexusUi.Muted,style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,null,tint=NexusUi.Accent)}
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
            FilterChip(selected=!recent,onClick={recent=false;path=""},label={Text("Arquivos PC")},leadingIcon={Icon(Icons.Default.Folder,null,Modifier.size(15.dp))})
            FilterChip(selected=recent,onClick={recent=true},label={Text("Recentes")},leadingIcon={Icon(Icons.Default.History,null,Modifier.size(15.dp))})
        }
        if(!recent){
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={if(parent.isNotBlank()) path=parent},enabled=parent.isNotBlank()){
                    Icon(Icons.Default.ArrowUpward,null,tint=if(parent.isNotBlank())NexusUi.Accent else NexusUi.Muted)
                }
                Text(path.ifBlank{"Pastas principais"},color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                if(path.isNotBlank()){
                    IconButton(onClick={
                        favorites=if(path in favorites)favorites-path else favorites+path
                        prefs.edit().putStringSet("folders",favorites).apply()
                    }){Icon(if(path in favorites)Icons.Default.Star else Icons.Default.StarBorder,null,tint=if(path in favorites)androidx.compose.ui.graphics.Color(0xFFFFD45C) else NexusUi.Muted)}
                }
            }
        }
        if(favorites.isNotEmpty() && !recent){
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){
                OutlinedButton(onClick={showFolderWheel=true},shape=RoundedCornerShape(14.dp)){
                    Icon(Icons.Default.TrackChanges,null,Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("RODA")
                }
                Row(Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    favorites.forEach{fav->AssistChip(onClick={path=fav},label={Text(fav.substringAfterLast('\\').ifBlank{"Favorito"},maxLines=1)})}
                }
            }
        }
        Surface(color=NexusUi.Panel,shape=RoundedCornerShape(NexusUi.cardRadius.dp),border=BorderStroke(1.dp,NexusUi.Border),modifier=Modifier.weight(1f).fillMaxWidth()){
            if(loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=NexusUi.Accent)}
            else LazyColumn(contentPadding=PaddingValues(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                items(list,key={it.path}){entry->
                    Surface(
                        color=NexusUi.PanelRaised,
                        shape=RoundedCornerShape((NexusUi.cardRadius-2).coerceAtLeast(6).dp),
                        modifier=Modifier.fillMaxWidth().clickable{
                            if(entry.dir){recent=false;path=entry.path}
                            else scope.launch{
                                status="Baixando ${entry.name}…"
                                status=if(downloadPcFile(ctx,entry))"Salvo em Downloads/NEXUS" else "Falha no download"
                            }
                        }
                    ){
                        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(if(entry.dir)Icons.Default.Folder else fileIcon(entry.name),null,tint=if(entry.dir)NexusUi.Accent else NexusUi.Text,modifier=Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)){
                                Text(entry.name,color=NexusUi.Text,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(!entry.dir)Text(formatBytes(entry.size),color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
                            }
                            Icon(if(entry.dir)Icons.Default.ChevronRight else Icons.Default.Download,null,tint=NexusUi.Muted)
                        }
                    }
                }
            }
        }
        if(status.isNotBlank())Text(status,color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=6.dp))
        Spacer(Modifier.height(8.dp))
        Surface(color=NexusUi.Panel,shape=RoundedCornerShape(NexusUi.cardRadius.dp),border=BorderStroke(1.dp,NexusUi.Border)){
            Column(Modifier.padding(10.dp)){
                Text("ATALHOS DO EDGE",color=NexusUi.Accent,fontWeight=FontWeight.Black,style=MaterialTheme.typography.labelSmall)
                FlowActions.chunked(2).forEach{row->
                    Row{
                        row.forEach{a->
                            val checked=a.id in enabledActions
                            Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){
                                Checkbox(checked=checked,onCheckedChange={checkedNow->
                                    enabledActions=if(checkedNow)enabledActions+a.id else enabledActions-a.id
                                    prefs.edit().putStringSet("actions",enabledActions).apply()
                                })
                                Text(a.label,color=NexusUi.Text,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseFsItem(o:JSONObject)=PcFsItem(o.optString("name"),o.optString("path"),o.optBoolean("dir"),o.optLong("size"),o.optLong("mtime"))

private fun formatBytes(v:Long):String=when{
    v<1024->"$v B"
    v<1024*1024->"${v/1024} KB"
    v<1024L*1024*1024->"${v/(1024*1024)} MB"
    else->String.format("%.1f GB",v/(1024.0*1024*1024))
}

private fun fileIcon(name:String)=when(name.substringAfterLast('.', "").lowercase()){
    "jpg","jpeg","png","webp","gif"->Icons.Default.Image
    "mp4","mkv","avi","webm"->Icons.Default.Movie
    "mp3","wav","flac"->Icons.Default.MusicNote
    else->Icons.Default.InsertDriveFile
}

private suspend fun capturePcToGallery(ctx:Context):Boolean=withContext(Dispatchers.IO){
    try{
        val data=RemoteClient.get("/capture.jpg")?:return@withContext false
        val name="NEXUS_${System.currentTimeMillis()}.jpg"
        val values=ContentValues().apply{
            put(MediaStore.Images.Media.DISPLAY_NAME,name)
            put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/NEXUS")
        }
        val uri=ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:return@withContext false
        ctx.contentResolver.openOutputStream(uri)?.use{it.write(data)}?:return@withContext false
        true
    }catch(_:Exception){false}
}

private suspend fun downloadPcFile(ctx:Context,entry:PcFsItem):Boolean=withContext(Dispatchers.IO){
    try{
        val encoded=URLEncoder.encode(entry.path,"UTF-8")
        val data=RemoteClient.get("/fs/download?path=$encoded")?:return@withContext false
        val values=ContentValues().apply{
            put(MediaStore.Downloads.DISPLAY_NAME,entry.name)
            put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/NEXUS")
        }
        val uri=ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:return@withContext false
        ctx.contentResolver.openOutputStream(uri)?.use{it.write(data)}?:return@withContext false
        true
    }catch(_:Exception){false}
}