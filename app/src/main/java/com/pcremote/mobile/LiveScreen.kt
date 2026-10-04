package com.pcremote.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable fun LivePcScreen(){
 var tick by remember{mutableIntStateOf(0)}
 LaunchedEffect(Api.host,Api.connected){while(Api.connected){tick++;delay(550)}}
 Column{
  Title("Tela ao vivo","Veja o Windows direto no celular");Spacer(Modifier.height(12.dp))
  Surface(color=Color(0xFF05080D),shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth()){
   Box(Modifier.background(Brush.linearGradient(listOf(Color(0x22159BFF),Color.Transparent))).padding(8.dp)){
    Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(22.dp)).background(Color.Black),contentAlignment=Alignment.Center){
     if(Api.connected)AsyncImage(model="http://${Api.host}:8765/screen.jpg?t=$tick",contentDescription="Tela do PC",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
     else Text("Aguardando conexão",color=Color.Gray)
    }
   }
  }
  Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("● REDE LOCAL",color=if(Api.connected)Color(0xFF62F29A) else Color.Gray,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall);Text("Atualização automática",color=Color(0xFF9299AE),style=MaterialTheme.typography.labelSmall)}
 }
}
