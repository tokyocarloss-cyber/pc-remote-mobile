package com.pcremote.mobile

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
private val Panel=Color(0xFF111629);private val Panel2=Color(0xFF171D34);private val Cyan=Color(0xFF32D7FF);private val Violet=Color(0xFF8D5CFF);private val Muted=Color(0xFF9299AE);private const val PORT=8765
class MainActivity:ComponentActivity(){override fun onCreate(s:Bundle?){super.onCreate(s);setContent{MaterialTheme(colorScheme=darkColorScheme()){RemoteApp()}}}}
object Api{var host by mutableStateOf("");var connected by mutableStateOf(false);fun post(path:String,b:String=""){if(host.isBlank())return;try{val c=URL("http://$host:$PORT$path").openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=700;c.readTimeout=700;c.doOutput=true;c.outputStream.use{it.write(b.toByteArray())};connected=c.responseCode in 200..299;c.disconnect()}catch(_:Exception){connected=false}}}
@Composable fun RemoteApp(){var page by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope();val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE;LaunchedEffect(Unit){while(true){if(!Api.connected)Discovery.findPc()?.let{Api.host=it;Api.connected=true};delay(if(Api.connected)5000 else 2500)}};PremiumShell(page,{page=it}){when(page){0->Home(scope){page=it};1->PcLibrary();2->Touch(scope){page=6};3->if(landscape)LandscapeGamepad() else AdvancedGamepad();4->Transfer();5->LivePcScreen();else->TextRemote()}}}
@Composable fun Home(scope:kotlinx.coroutines.CoroutineScope,go:(Int)->Unit){Column{Text("Central de controle",color=Color.White,fontSize=30.sp,fontWeight=FontWeight.Black);Text(if(Api.connected)"Seu PC está pronto" else "Encontrando seu Windows…",color=if(Api.connected)Color(0xFF62F29A) else Muted,fontSize=13.sp);Spacer(Modifier.height(18.dp));Row{Tile("▣","APPS & JOGOS","Biblioteca do PC",Cyan,Modifier.weight(1f)){go(1)};Spacer(Modifier.width(12.dp));Tile("⌁","TOUCHPAD","Mouse + teclado",Violet,Modifier.weight(1f)){go(2)}};Spacer(Modifier.height(12.dp));Row{Tile("🎮","CONTROLE","Modo console",Violet,Modifier.weight(1f)){go(3)};Spacer(Modifier.width(12.dp));Tile("⇄","PC DROP","Arquivos e texto",Cyan,Modifier.weight(1f)){go(4)}};Spacer(Modifier.height(16.dp));Text("CONTROLES RÁPIDOS",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Surface(color=Color(0x66171D34),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(17.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){Quick("−","VOL",scope,"VOLUME_DOWN");Quick("+","VOL",scope,"VOLUME_UP");Quick("▶","MÍDIA",scope,"MEDIA_PLAY");Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable{go(5)}){Text("▣",color=Cyan,fontSize=22.sp);Text("TELA",color=Muted,fontSize=9.sp)}}}}}
@Composable fun Tile(icon:String,title:String,sub:String,accent:Color,m:Modifier,on:()->Unit){Surface(color=Color(0x99171D34),shape=RoundedCornerShape(26.dp),modifier=m.height(138.dp).clickable{on()}){Box(Modifier.background(Brush.linearGradient(listOf(accent.copy(.10f),Color.Transparent)))){Column(Modifier.fillMaxSize().padding(17.dp),verticalArrangement=Arrangement.SpaceBetween){Box(Modifier.size(43.dp).background(accent.copy(.17f),CircleShape),contentAlignment=Alignment.Center){Text(icon,color=accent,fontSize=22.sp)};Column{Text(title,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=10.sp)}}}}}
@Composable fun Quick(i:String,t:String,s:kotlinx.coroutines.CoroutineScope,k:String){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable{s.launch(Dispatchers.IO){Api.post("/key",k)}}){Box(Modifier.size(42.dp).background(Color(0xFF1B2340),CircleShape),contentAlignment=Alignment.Center){Text(i,color=Cyan,fontSize=20.sp)};Spacer(Modifier.height(4.dp));Text(t,color=Muted,fontSize=9.sp)}}
@Composable fun Touch(scope:kotlinx.coroutines.CoroutineScope,keyboard:()->Unit){var dx by remember{mutableFloatStateOf(0f)};var dy by remember{mutableFloatStateOf(0f)};Column{Title("Touchpad","Mouse, clique e teclado");Spacer(Modifier.height(12.dp));Box(Modifier.fillMaxWidth().weight(1f).background(Brush.radialGradient(listOf(Color(0xFF1A2445),Panel)),RoundedCornerShape(28.dp)).pointerInput(Unit){detectDragGestures(onDragEnd={scope.launch(Dispatchers.IO){Api.post("/mouse","${dx.toInt()},${dy.toInt()}")};dx=0f;dy=0f}){c,d->c.consume();dx+=d.x;dy+=d.y}},contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("⌁",color=Cyan,fontSize=50.sp);Text("DESLIZE",color=Muted,fontSize=10.sp)}};Spacer(Modifier.height(8.dp));Row{MouseAction("CLIQUE",Modifier.weight(1f),scope,"left");Spacer(Modifier.width(8.dp));MouseAction("DIREITO",Modifier.weight(1f),scope,"right")};Spacer(Modifier.height(8.dp));Button(onClick=keyboard,modifier=Modifier.fillMaxWidth()){Text("⌨ TECLADO REMOTO")}}}
@Composable fun MouseAction(t:String,m:Modifier,s:kotlinx.coroutines.CoroutineScope,k:String){Surface(color=Panel2,shape=RoundedCornerShape(17.dp),modifier=m.clickable{s.launch(Dispatchers.IO){Api.post("/click",k)}}){Text(t,Modifier.padding(15.dp),color=Color.White,fontSize=11.sp,textAlign=TextAlign.Center)}}
@Composable fun Transfer(){Column{Title("PC Drop","Envie para o computador");Spacer(Modifier.height(18.dp));Box(Modifier.fillMaxWidth().weight(1f).background(Brush.radialGradient(listOf(Cyan.copy(.12f),Panel)),RoundedCornerShape(30.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(86.dp).background(Cyan.copy(.12f),CircleShape),contentAlignment=Alignment.Center){Text("⇅",color=Cyan,fontSize=48.sp)};Spacer(Modifier.height(12.dp));Text("ARRASTE OU ESCOLHA",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold);Text(if(Api.connected)"${Api.host} pronto para receber" else "Conectando ao PC…",color=Muted,fontSize=11.sp)}}}}
@Composable fun Title(a:String,b:String){Column{Text(a,color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Black);Text(b,color=Muted,fontSize=12.sp)}}
