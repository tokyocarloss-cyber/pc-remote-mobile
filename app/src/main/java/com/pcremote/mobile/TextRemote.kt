package com.pcremote.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable fun TextRemote(){
 var value by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 Column{
  Title("Teclado remoto","Digite no Windows pelo celular");Spacer(Modifier.height(14.dp))
  Surface(color=Color(0xFF0B121B),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
   Column(Modifier.padding(14.dp)){Text("DIGITAÇÃO RÁPIDA",color=Color(0xFF5DE7FF),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Black);Spacer(Modifier.height(8.dp));OutlinedTextField(value,{value=it},Modifier.fillMaxWidth(),placeholder={Text("Escreva aqui…")},minLines=4,shape=RoundedCornerShape(18.dp));Spacer(Modifier.height(10.dp));Button(onClick={scope.launch(Dispatchers.IO){Api.post("/text",value)}},modifier=Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(16.dp)){Text("ENVIAR PARA O PC")}}
  }
  Spacer(Modifier.height(10.dp));Text("TECLAS RÁPIDAS",color=Color(0xFF9299AE),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp))
  Row{listOf("ENTER","ESC","SPACE").forEach{key->OutlinedButton(onClick={scope.launch(Dispatchers.IO){Api.post("/key",key)}},modifier=Modifier.weight(1f).padding(3.dp),shape=RoundedCornerShape(15.dp)){Text(key)}}}
 }
}
