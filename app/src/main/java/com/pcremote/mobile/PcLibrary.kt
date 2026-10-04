package com.pcremote.mobile

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class PcApp(val name:String,val store:String="Outros")

@Composable fun PcLibrary(){
 val ctx=LocalContext.current
 var apps by remember{mutableStateOf<List<PcApp>>(emptyList())};var search by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 LaunchedEffect(Api.host,Api.connected){if(Api.connected){RemoteClient.get("/apps")?.let{raw->try{val a=JSONArray(String(raw));apps=(0 until a.length()).map{i->val v=a.get(i);if(v is org.json.JSONObject)PcApp(v.optString("name"),v.optString("store","Outros"))else PcApp(v.toString())}}catch(_:Exception){}}}}
 val order=loadStoreOrder(ctx)
 val filtered=apps.filter{isStoreEnabled(ctx,it.store)}.sortedBy{val i=order.indexOf(it.store);if(i<0)999 else i}.filter{search.isBlank()||it.name.contains(search,true)}
 Column{
  Title("Biblioteca","Apps e jogos do seu Windows")
  Spacer(Modifier.height(10.dp))
  if(filtered.isNotEmpty()) RadialLauncher(filtered.take(8)){app->scope.launch(Dispatchers.IO){Api.post("/launch",app.name)}}
  Spacer(Modifier.height(10.dp))
  OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Buscar app ou jogo…")},leadingIcon={Text("⌕",color=Color(0xFF5DE7FF))},shape=RoundedCornerShape(20.dp))
  Spacer(Modifier.height(9.dp));Text(filtered.size.toString()+" ITENS",color=Color(0xFF8F98B5),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(5.dp))
  if(apps.isEmpty())Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){Text(if(Api.connected)"Carregando biblioteca…" else "Conecte ao PC para carregar",color=Color(0xFF8F98B5))}
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp)){items(filtered,key={it.name}){app->
   Surface(color=Color(0xB80E151F),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().clickable{scope.launch(Dispatchers.IO){Api.post("/launch",app.name)}}){
    Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){AppIcon(app,44);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(app.name,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=1);Text(app.store,color=Color(0xFF758094),style=MaterialTheme.typography.labelSmall)};Text("›",color=Color(0xFF5DE7FF))}
   }
  }}
 }
}

@Composable private fun RadialLauncher(apps:List<PcApp>,launch:(PcApp)->Unit){
 Surface(color=Color(0xFF070B11),shape=RoundedCornerShape(30.dp),modifier=Modifier.fillMaxWidth().height(230.dp)){
  BoxWithConstraints(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x33159BFF),Color.Transparent))),contentAlignment=Alignment.Center){
   Box(Modifier.size(82.dp).background(Color(0xFF0D1620),CircleShape),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("N",color=Color(0xFF5DE7FF),fontWeight=FontWeight.Black,style=MaterialTheme.typography.headlineMedium);Text("LAUNCH",color=Color(0xFF758094),style=MaterialTheme.typography.labelSmall)}}
   val radius=(if(maxWidth<maxHeight)maxWidth else maxHeight)*.34f
   apps.forEachIndexed{index,app->
    val angle=(-PI/2)+(2*PI*index/apps.size)
    val x=maxWidth/2-30.dp+radius*cos(angle).toFloat();val y=maxHeight/2-30.dp+radius*sin(angle).toFloat()
    Column(Modifier.offset(x,y).width(60.dp).clickable{launch(app)},horizontalAlignment=Alignment.CenterHorizontally){AppIcon(app,52);Spacer(Modifier.height(3.dp));Text(app.name.take(10),color=Color.White,style=MaterialTheme.typography.labelSmall,maxLines=1,textAlign=TextAlign.Center)}
   }
  }
 }
}

@Composable private fun AppIcon(app:PcApp,size:Int){
 Box(Modifier.size(size.dp).background(Color(0xFF111B26),RoundedCornerShape((size*.28f).dp)).clip(RoundedCornerShape((size*.28f).dp)),contentAlignment=Alignment.Center){
  Text(app.name.take(1).uppercase(),color=Color(0xFF5DE7FF),fontWeight=FontWeight.Black)
  if(Api.host.isNotBlank())AsyncImage(model="http://${Api.host}:8765/app-icon/${Uri.encode(app.name)}",contentDescription=app.name,modifier=Modifier.fillMaxSize().padding(6.dp),contentScale=ContentScale.Fit)
 }
}
