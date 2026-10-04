package com.pcremote.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

private val Bg=Color(0xFF070A12); private val Card=Color(0xFF121827); private val Cyan=Color(0xFF27E5FF); private val Purple=Color(0xFF9D5CFF)
private const val PORT=8765

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme(colorScheme=darkColorScheme()){PCRemoteApp()}}}}

object RemoteApi {
 var host by mutableStateOf(""); var connected by mutableStateOf(false)
 fun base()="http://$host:$PORT"
 fun post(path:String, body:String=""){ if(host.isBlank())return; try{ val c=URL(base()+path).openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=700;c.readTimeout=700;c.doOutput=true;c.setRequestProperty("Content-Type","text/plain; charset=utf-8");c.outputStream.use{it.write(body.toByteArray())};connected=c.responseCode in 200..299;c.disconnect()}catch(_:Exception){connected=false} }
 fun ping(){ if(host.isBlank())return;try{val c=URL(base()+"/ping").openConnection() as HttpURLConnection;c.connectTimeout=700;c.readTimeout=700;connected=c.responseCode==200;c.disconnect()}catch(_:Exception){connected=false}}
}

@Composable fun PCRemoteApp(){var tab by remember{mutableIntStateOf(0)};var ip by remember{mutableStateOf("")};val scope=rememberCoroutineScope();val names=listOf("Controle","Apps","Touchpad","Enviar","Tela")
 Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("PC REMOTE",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Black);Text("Central de controle",color=Color.Gray)};Surface(shape=RoundedCornerShape(30.dp),color=Card){Text(if(RemoteApi.connected)"● PC conectado" else "● PC aguardando",Modifier.padding(12.dp,8.dp),color=if(RemoteApi.connected)Color.Green else Cyan)}}
 Spacer(Modifier.height(12.dp));Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(ip,{ip=it},label={Text("IP do PC")},singleLine=true,modifier=Modifier.weight(1f));Spacer(Modifier.width(8.dp));Button(onClick={RemoteApi.host=ip.trim();scope.launch(Dispatchers.IO){RemoteApi.ping()} }){Text("Conectar")}}
 Spacer(Modifier.height(18.dp));when(tab){0->ControllerScreen(scope);1->AppsScreen(scope);2->TouchpadScreen(scope);3->TransferScreen();else->ScreenView()};Spacer(Modifier.weight(1f));Row(Modifier.fillMaxWidth().background(Card,RoundedCornerShape(22.dp)).padding(5.dp),horizontalArrangement=Arrangement.SpaceEvenly){names.forEachIndexed{i,n->Text(n,color=if(tab==i)Cyan else Color.LightGray,fontSize=12.sp,modifier=Modifier.clickable{tab=i}.padding(9.dp))}}}}

@Composable fun ControllerScreen(scope:kotlinx.coroutines.CoroutineScope){Column{Text("Controle",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Text("Comandos enviados ao Windows",color=Color.Gray);Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Box(Modifier.size(135.dp).background(Card,RoundedCornerShape(70.dp)),contentAlignment=Alignment.Center){Text("◎",color=Cyan,fontSize=54.sp)};Column{Row{GameButton("Y",scope);GameButton("B",scope)};Row{GameButton("X",scope);GameButton("A",scope)}}};Spacer(Modifier.height(18.dp));Row{Small("⏮",scope,"MEDIA_PREV");Small("▶/⏸",scope,"MEDIA_PLAY");Small("⏭",scope,"MEDIA_NEXT");Small("🔉",scope,"VOLUME_DOWN");Small("🔊",scope,"VOLUME_UP")}}}
@Composable fun GameButton(t:String,scope:kotlinx.coroutines.CoroutineScope){Box(Modifier.padding(4.dp).size(58.dp).background(Card,RoundedCornerShape(30.dp)).clickable{scope.launch(Dispatchers.IO){RemoteApi.post("/key",t)}},contentAlignment=Alignment.Center){Text(t,color=Purple,fontWeight=FontWeight.Bold)}}
@Composable fun Small(t:String,scope:kotlinx.coroutines.CoroutineScope,key:String){Text(t,color=Color.White,modifier=Modifier.padding(3.dp).background(Card,RoundedCornerShape(12.dp)).clickable{scope.launch(Dispatchers.IO){RemoteApi.post("/key",key)}}.padding(10.dp))}
@Composable fun AppsScreen(scope:kotlinx.coroutines.CoroutineScope){Column{Text("Apps & Jogos",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Text("Atalhos rápidos",color=Color.Gray);Spacer(Modifier.height(20.dp));listOf("Steam","Chrome","Explorador","Área de Trabalho").forEach{n->Surface(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable{scope.launch(Dispatchers.IO){RemoteApi.post("/launch",n)}},shape=RoundedCornerShape(18.dp),color=Card){Text(n,Modifier.padding(18.dp),color=Color.White,fontSize=18.sp)}}}}
@Composable fun TouchpadScreen(scope:kotlinx.coroutines.CoroutineScope){var dx by remember{mutableFloatStateOf(0f)};var dy by remember{mutableFloatStateOf(0f)};Column{Text("Touchpad",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp));Box(Modifier.fillMaxWidth().height(330.dp).background(Card,RoundedCornerShape(28.dp)).pointerInput(Unit){detectDragGestures(onDragEnd={if(dx!=0f||dy!=0f){val x=dx.toInt();val y=dy.toInt();scope.launch(Dispatchers.IO){RemoteApi.post("/mouse","$x,$y")};dx=0f;dy=0f}}){change,drag->change.consume();dx+=drag.x;dy+=drag.y}},contentAlignment=Alignment.Center){Text("Deslize para controlar o mouse",color=Color.LightGray)}}}
@Composable fun TransferScreen(){Column{Text("Transferência",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Text("Será ativada na próxima etapa",color=Color.Gray)}}
@Composable fun ScreenView(){Column{Text("Tela do PC",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(18.dp));Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color.Black,RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center){Text("Streaming em desenvolvimento",color=Color.Gray)}}}
