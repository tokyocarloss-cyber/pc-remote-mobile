package com.pcremote.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun LivePcScreen(){
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Api.host, Api.connected){
        while(Api.connected){ tick++; delay(700) }
    }
    Column{
        Title("Tela ao vivo","Monitor do Windows em tempo quase real")
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(22.dp)).background(Color.Black), contentAlignment=Alignment.Center){
            if(Api.connected) AsyncImage(
                model="http://${Api.host}:8765/screen.jpg?t=$tick",
                contentDescription="Tela do PC",
                modifier=Modifier.fillMaxSize(),
                contentScale=ContentScale.Fit
            ) else Text("Aguardando conexão com o PC",color=Color.Gray)
        }
        Spacer(Modifier.height(12.dp))
        Text("Atualização automática • rede local",color=Color.Gray)
    }
}
