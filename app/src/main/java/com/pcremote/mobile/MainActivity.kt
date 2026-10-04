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
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

private val Bg=Color(0xFF060812); private val Panel=Color(0xFF111629); private val Panel2=Color(0xFF171D34); private val Cyan=Color(0xFF32D7FF); private val Violet=Color(0xFF8D5CFF); private val Muted=Color(0xFF9299AE)
private const val PORT=8765
class MainActivity:ComponentActivity(){override fun onCreate(s:Bundle?){super.onCreate(s);setContent{MaterialTheme(colorScheme=darkColorScheme()){RemoteApp()}}}}
object Api{var host by mutableStateOf("");var connected by mutableStateOf(false);fun post(path:String,b:String=""){if(host.isBlank())return;try{val c=URL("http://$host:$PORT$path").openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=600;c.readTimeout=600;c.doOutput=true;c.outputStream.use{it.write(b.toByteArray())};connected=c.responseCode in 200..299;c.disconnect()}catch(_:Exception){connected=false}}}

@Composable fun RemoteApp(){var page by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope();Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF090C19),Bg)))){Column(Modifier.fillMaxSize().padding(horizontal=18.dp,vertical=16.dp)){Header();Spacer(Modifier.height(18.dp));Box(Modifier.weight(1f)){when(page){0->Home(scope){page=it};1->Apps(scope);2->Touch(scope);3->Pad(scope);4->Transfer();else->Live()}};Nav(page){page=it}}}}
@Composable fun Header(){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column{Text("PC REMOTE",Color.White,24.sp,fontWeight=FontWeight.Black);Text("seu PC na palma da mão",Muted,12.sp)};Surface(color=Panel2,shape=RoundedCornerShape(22.dp)){Text(if(Api.connected)"● CONECTADO" else "● PROCURANDO PC",Modifier.padding(13.dp,8.dp),if(Api.connected)Color(0xFF54E68B) else Cyan,11.sp,fontWeight=FontWeight.Bold)}}}
@Composable fun Home(scope:kotlinx.coroutines.CoroutineScope,go:(Int)->Unit){Column{Text("Boa noite 👋",Color.White,28.sp,fontWeight=FontWeight.Bold);Text("O que você quer controlar?",Muted,14.sp);Spacer(Modifier.height(18.dp));Row{Tile("▣","APPS & JOGOS","Abra tudo do PC",Cyan,Modifier.weight(1f)){go(1)};Spacer(Modifier.width(12.dp));Tile("⌁","TOUCHPAD","Mouse + teclado",Violet,Modifier.weight(1f)){go(2)}};Spacer(Modifier.height(12.dp));Row{Tile("🎮","CONTROLE","Gamepad completo",Violet,Modifier.weight(1f)){go(3)};Spacer(Modifier.width(12.dp));Tile("⇄","ENVIAR","Arquivos e links",Cyan,Modifier.weight(1f)){go(4)}};Spacer(Modifier.height(16.dp));Surface(color=Panel,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("CONTROLES RÁPIDOS",Muted,11.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(13.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){Quick("🔉","VOL -",scope,"VOLUME_DOWN");Quick("🔊","VOL +",scope,"VOLUME_UP");Quick("⏯","PLAY",scope,"MEDIA_PLAY");Quick("▣","TELA",scope,"DESKTOP")}}}}}
@Composable fun Tile(icon:String,title:String,sub:String,accent:Color,m:Modifier,on:()->Unit){Surface(color=Panel,shape=RoundedCornerShape(25.dp),modifier=m.height(142.dp).clickable{on()}){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.SpaceBetween){Box(Modifier.size(44.dp).background(accent.copy(.14f),CircleShape),contentAlignment=Alignment.Center){Text(icon,accent,22.sp)};Column{Text(title,Color.White,15.sp,fontWeight=FontWeight.Bold);Text(sub,Muted,11.sp)}}}}
@Composable fun Quick(i:String,t:String,s:kotlinx.coroutines.CoroutineScope,k:String){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable{s.launch(Dispatchers.IO){Api.post("/key",k)}}){Box(Modifier.size(47.dp).background(Panel2,CircleShape),contentAlignment=Alignment.Center){Text(i,fontSize=20.sp)};Spacer(Modifier.height(5.dp));Text(t,Muted,9.sp)}}
@Composable fun Apps(scope:kotlinx.coroutines.CoroutineScope){Column{Title("Apps & Jogos","Segure e escolha como uma roda rápida");Spacer(Modifier.height(20.dp));Box(Modifier.fillMaxWidth().height(330.dp),contentAlignment=Alignment.Center){Box(Modifier.size(260.dp).background(Panel,CircleShape),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("◎",Cyan,54.sp);Text("SEGURE",Color.White,13.sp,fontWeight=FontWeight.Bold);Text("arraste até o app",Muted,10.sp)}};Radial("STEAM",Alignment.TopCenter,scope);Radial("CHROME",Alignment.CenterEnd,scope);Radial("EXPLORER",Alignment.BottomCenter,scope);Radial("DESKTOP",Alignment.CenterStart,scope)}}}
@Composable fun BoxScope.Radial(n:String,a:Alignment,s:kotlinx.coroutines.CoroutineScope){Surface(color=Panel2,shape=RoundedCornerShape(16.dp),modifier=Modifier.align(a).clickable{s.launch(Dispatchers.IO){Api.post("/launch",n)}}){Text(n,Modifier.padding(12.dp,9.dp),Color.White,10.sp,fontWeight=FontWeight.Bold)}}
@Composable fun Touch(scope:kotlinx.coroutines.CoroutineScope){var dx by remember{mutableFloatStateOf(0f)};var dy by remember{mutableFloatStateOf(0f)};Column{Title("Touchpad","Controle preciso do mouse");Spacer(Modifier.height(14.dp));Box(Modifier.fillMaxWidth().height(360.dp).background(Panel,RoundedCornerShape(28.dp)).pointerInput(Unit){detectDragGestures(onDragEnd={val x=dx.toInt();val y=dy.toInt();scope.launch(Dispatchers.IO){Api.post("/mouse","$x,$y")};dx=0f;dy=0f}){c,d->c.consume();dx+=d.x;dy+=d.y}},contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("⌁",Cyan,54.sp);Text("DESLIZE PARA MOVER",Muted,11.sp)}};Spacer(Modifier.height(10.dp));Row{Action("CLIQUE",Modifier.weight(1f),scope,"LEFT_CLICK");Spacer(Modifier.width(10.dp));Action("DIREITO",Modifier.weight(1f),scope,"RIGHT_CLICK")}}}
@Composable fun Pad(scope:kotlinx.coroutines.CoroutineScope){Column{Title("Controle","Perfil inteligente • personalizável");Spacer(Modifier.height(14.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Stick();Column{Row{Btn("Y",scope);Btn("B",scope)};Row{Btn("X",scope);Btn("A",scope)}}};Spacer(Modifier.height(24.dp));Surface(color=Panel,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Perfil automático",Color.White,fontWeight=FontWeight.Bold);Text("EDITAR",Cyan,fontWeight=FontWeight.Bold)}}}}
@Composable fun Stick(){Box(Modifier.size(145.dp).background(Panel,CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(72.dp).background(Panel2,CircleShape),contentAlignment=Alignment.Center){Text("●",Cyan,30.sp)}}}
@Composable fun Btn(t:String,s:kotlinx.coroutines.CoroutineScope){Box(Modifier.padding(5.dp).size(62.dp).background(Panel2,CircleShape).clickable{s.launch(Dispatchers.IO){Api.post("/key",t)}},contentAlignment=Alignment.Center){Text(t,Violet,18.sp,fontWeight=FontWeight.Black)}}
@Composable fun Transfer(){Column{Title("Enviar para o PC","Arraste, compartilhe ou cole");Spacer(Modifier.height(18.dp));Box(Modifier.fillMaxWidth().height(280.dp).background(Panel,RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("⇅",Cyan,62.sp);Text("SOLTE AQUI",Color.White,18.sp,fontWeight=FontWeight.Bold);Text("Fotos • vídeos • arquivos • links • texto",Muted,11.sp,textAlign=TextAlign.Center)}}}}
@Composable fun Live(){Column{Title("Tela ao vivo","Visualize e controle seu PC");Spacer(Modifier.height(16.dp));Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color.Black,RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){Text("PC SCREEN",Muted,16.sp,fontWeight=FontWeight.Bold)}}}
@Composable fun Action(t:String,m:Modifier,s:kotlinx.coroutines.CoroutineScope,k:String){Surface(color=Panel2,shape=RoundedCornerShape(17.dp),modifier=m.clickable{s.launch(Dispatchers.IO){Api.post("/key",k)}}){Text(t,Modifier.padding(15.dp),Color.White,11.sp,textAlign=TextAlign.Center)}}
@Composable fun Title(a:String,b:String){Column{Text(a,Color.White,25.sp,fontWeight=FontWeight.Bold);Text(b,Muted,12.sp)}}
@Composable fun Nav(sel:Int,set:(Int)->Unit){val n=listOf("⌂\nINÍCIO","▦\nAPPS","⌁\nMOUSE","🎮\nCONTROLE","⇄\nENVIAR","▣\nTELA");Surface(color=Panel,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(4.dp),horizontalArrangement=Arrangement.SpaceEvenly){n.forEachIndexed{i,x->Text(x,if(i==sel)Cyan else Muted,9.sp,textAlign=TextAlign.Center,lineHeight=12.sp,modifier=Modifier.clickable{set(i)}.padding(8.dp,7.dp))}}}}
