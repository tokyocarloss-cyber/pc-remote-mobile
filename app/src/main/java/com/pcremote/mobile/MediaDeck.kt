package com.pcremote.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable fun MediaDeck(){
 val s=rememberCoroutineScope();fun hit(k:String){s.launch(Dispatchers.IO){Api.post("/key",k)}}
 Column{
  Title("Mídia","Controle sem voltar para o PC");Spacer(Modifier.height(14.dp))
  Surface(color=Color(0xFF080D14),shape=RoundedCornerShape(30.dp),modifier=Modifier.fillMaxWidth()){
   Column(Modifier.background(Brush.radialGradient(listOf(Color(0x22159BFF),Color.Transparent))).padding(vertical=26.dp),horizontalAlignment=Alignment.CenterHorizontally){
    Text("NOW PLAYING",color=Color(0xFF5DE7FF),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Black);Spacer(Modifier.height(22.dp))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){MediaKey("⏮",62){hit("MEDIA_PREV")};MediaKey("▶",88){hit("MEDIA_PLAY")};MediaKey("⏭",62){hit("MEDIA_NEXT")}}
    Spacer(Modifier.height(28.dp));Text("VOLUME",color=Color(0xFF9299AE),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){MediaKey("−",58){hit("VOLUME_DOWN")};MediaKey("×",58){hit("VOLUME_MUTE")};MediaKey("+",58){hit("VOLUME_UP")}}
   }
  }
 }
}
@Composable private fun MediaKey(t:String,size:Int,on:()->Unit){Box(Modifier.size(size.dp).background(if(size>70)Color(0xFF159BFF) else Color(0xFF111A24),CircleShape).clickable{on()},contentAlignment=Alignment.Center){Text(t,if(size>70)Color.White else Color(0xFF5DE7FF),if(size>70)30.sp else 24.sp,fontWeight=FontWeight.Bold)}}
