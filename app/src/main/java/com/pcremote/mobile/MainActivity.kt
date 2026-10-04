package com.pcremote.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
private val Bg=Color(0xFF060812);private val Panel=Color(0xFF111629);private val Panel2=Color(0xFF171D34);private val Cyan=Color(0xFF32D7FF);private val Violet=Color(0xFF8D5CFF);private val Muted=Color(0xFF9299AE);private const val PORT=8765
class MainActivity:ComponentActivity(){override fun onCreate(s:Bundle?){super.onCreate(s);setContent{MaterialTheme(colorScheme=darkColorScheme()){RemoteApp()}}}}
object Api{var host by mutableStateOf("");var connected by mutableStateOf(false);fun post(path:String,b:String=""){if(host.isBlank())return;try{val c=URL("http://$host:$PORT$path").openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=700;c.readTimeout=700;c.doOutput=true;c.outputStream.use{it.write(b.toByteArray())};connected=c.responseCode in 200..299;c.disconnect()}catch(_:Exception){connected=false}}}
@Composable fun RemoteApp(){var page by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope();LaunchedEffect(Unit){while(true){if(!Api.connected)Discovery.findPc()?.let{Api.host=it;Api.connected=true};delay(if(Api.connected)5000 else 2500)}};Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF090C19),Bg)))){Column(Modifier.fillMaxSize().padding(18.dp)){Header();Spacer(Modifier.height(14.dp));Box(Modifier.weight(1f)){when(page){0->Home(scope){page=it};1->PcLibrary();2->Touch(scope){page=6};3->AdvancedGamepad();4->Transfer();5->LivePcScreen();else->TextRemote()}};Nav(page){page=it}}}}
@Composable fun Header(){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column{Text("PC REMOTE",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Black);Text(if(Api.connected)"PC ${Api.host}" else "buscando na rede local…",color=Muted,fontSize=12.sp)};Surface(color=Panel2,shape=RoundedCornerShape(22.dp)){Text(if(Api.connected)"● CONECTADO" else "● PROCURANDO",Modifier.padding(13.dp,8.dp),color=if(Api.connected)Color(0xFF54E68B) else Cyan,fontSize=11.sp,fontWeight=FontWeight.Bold)}}}
@Composable fun Home(scope:kotlinx.coroutines.CoroutineScope,go:(Int)->Unit){Column{Text("Central de controle",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Controle seu Windows sem sair da cama",color=Muted,fontSize=14.sp);Spacer(Modifier.height(18.dp));Row{Tile("▣","APPS & JOGOS","Biblioteca do PC",Cyan,Modifier.weight(1f)){go(1)};Spacer(Modifier.width(12.dp));Tile("⌁","TOUCHPAD","Mouse + teclado",Violet,Modifier.weight(1f)){go(2)}};Spacer(Modifier.height(12.dp));Row{Tile("🎮","CONTROLE","Gamepad móvel",Violet,Modifier.weight(1f)){go(3)};Spacer(Modifier.width(12.dp));Tile("⇄","ENVIAR","Arquivos e texto",Cyan,Modifier.weight(1f)){go(4)}};Spacer(Modifier.height(16.dp));Surface(color=Panel,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){Quick("🔉","VOL -",scope,"VOLUME_DOWN");Quick("🔊","VOL +",scope,"VOLUME_UP");Quick("⏯","PLAY",scope,"MEDIA_PLAY");Quick("⛶","TELA",scope,"ENTER")}}}}
@Composable fun Tile(icon:String,title:String,sub:String,accent:Color,m:Modifier,on:()->Unit){Surface(color=Panel,shape=RoundedCornerShape(25.dp),modifier=m.height(142.dp).clickable{on()}){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.SpaceBetween){Box(Modifier.size(44.dp).background(accent.copy(.14f),CircleShape),contentAlignment=Alignment.Center){Text(icon,color=accent,fontSize=22.sp)};Column{Text(title,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=11.sp)}}}}
@Composable fun Quick(i:String,t:String,s:kotlinx.coroutines.CoroutineScope,k:String){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable{s.launch(Dispatchers.IO){Api.post("/key",k)}}){Text(i,fontSize=22.sp);Text(t,color=Muted,fontSize=9.sp)}}
@Composable fun Touch(scope:kotlinx.coroutines.CoroutineScope,keyboard:()->Unit){var dx by remember{mutableFloatStateOf(0f)};var dy by remember{mutableFloatStateOf(0f)};Column{Title("Touchpad","Mouse, rolagem e teclado");Spacer(Modifier.height(12.dp));Box(Modifier.fillMaxWidth().height(310.dp).background(Panel,RoundedCornerShape(28.dp)).pointerInput(Unit){detectDragGestures(onDragEnd={scope.launch(Dispatchers.IO){Api.post("/mouse","${dx.toInt()},${dy.toInt()}")};dx=0f;dy=0f}){c,d->c.consume();dx+=d.x;dy+=d.y}},contentAlignment=Alignment.Center){Text("DESLIZE PARA MOVER",color=Muted)};Spacer(Modifier.height(8.dp));Row{MouseAction("CLIQUE",Modifier.weight(1f),scope,"left");Spacer(Modifier.width(8.dp));MouseAction("DIREITO",Modifier.weight(1f),scope,"right")};Spacer(Modifier.height(8.dp));Button(onClick=keyboard,modifier=Modifier.fillMaxWidth()){Text("ABRIR TECLADO REMOTO")}}}
@Composable fun MouseAction(t:String,m:Modifier,s:kotlinx.coroutines.CoroutineScope,k:String){Surface(color=Panel2,shape=RoundedCornerShape(17.dp),modifier=m.clickable{s.launch(Dispatchers.IO){Api.post("/click",k)}}){Text(t,Modifier.padding(15.dp),color=Color.White,fontSize=11.sp,textAlign=TextAlign.Center)}}
@Composable fun Transfer(){Column{Title("Enviar","Centro de transferência");Spacer(Modifier.height(18.dp));Box(Modifier.fillMaxWidth().height(260.dp).background(Panel,RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("⇅",color=Cyan,fontSize=62.sp);Text("PC REMOTE DROP",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(if(Api.connected)"PC pronto para receber" else "Aguardando PC",color=Muted)}}}}
@Composable fun Title(a:String,b:String){Column{Text(a,color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold);Text(b,color=Muted,fontSize=12.sp)}}
@Composable fun Nav(sel:Int,set:(Int)->Unit){val n=listOf("⌂\nINÍCIO","▦\nAPPS","⌁\nMOUSE","🎮\nPAD","⇄\nENVIAR","▣\nTELA");Surface(color=Panel,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(3.dp),horizontalArrangement=Arrangement.SpaceEvenly){n.forEachIndexed{i,x->Text(x,Modifier.clickable{set(i)}.padding(7.dp),color=if(i==sel)Cyan else Muted,fontSize=9.sp,textAlign=TextAlign.Center,lineHeight=12.sp)}}}}
