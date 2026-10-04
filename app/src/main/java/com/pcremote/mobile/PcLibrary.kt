package com.pcremote.mobile

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable fun PcLibrary(){
 var apps by remember{mutableStateOf<List<String>>(emptyList())};var search by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 LaunchedEffect(Api.host,Api.connected){if(Api.connected){RemoteClient.get("/apps")?.let{raw->try{val a=JSONArray(String(raw));apps=(0 until a.length()).map{a.getString(it)}}catch(_:Exception){}}}}
 val filtered=if(search.isBlank())apps else apps.filter{it.contains(search,true)}
 Column{
  Title("Biblioteca","Abra qualquer app ou jogo do seu Windows")
  Spacer(Modifier.height(12.dp))
  OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Buscar no PC…")},leadingIcon={Text("⌕",color=Color(0xFF5DE7FF))},shape=RoundedCornerShape(20.dp))
  Spacer(Modifier.height(10.dp));Text(filtered.size.toString()+" ITENS",color=Color(0xFF8F98B5),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(5.dp))
  if(apps.isEmpty())Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){Text(if(Api.connected)"Carregando biblioteca…" else "Conecte ao PC para carregar",color=Color(0xFF8F98B5))}
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp)){items(filtered){name->
   Surface(color=Color(0xB8171D34),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().clickable{scope.launch(Dispatchers.IO){Api.post("/launch",name)}}){
    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(Color(0x335DE7FF),Color(0x33765BFF))),CircleShape),contentAlignment=Alignment.Center){Text(name.take(1).uppercase(),color=Color.White,fontWeight=FontWeight.Black)};Spacer(Modifier.width(12.dp));Text(name,Modifier.weight(1f),color=Color.White,fontWeight=FontWeight.SemiBold);Text("›",color=Color(0xFF5DE7FF))}
   }
  }}
 }
}