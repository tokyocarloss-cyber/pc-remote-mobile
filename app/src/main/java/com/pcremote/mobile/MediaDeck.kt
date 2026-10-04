package com.pcremote.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable fun MediaDeck(){val s=rememberCoroutineScope();fun hit(k:String){s.launch(Dispatchers.IO){Api.post("/key",k)}};Column{Title("Mídia","Controle da cama");Spacer(Modifier.height(24.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){MediaKey("⏮"){hit("MEDIA_PREV")};MediaKey("▶"){hit("MEDIA_PLAY")};MediaKey("⏭"){hit("MEDIA_NEXT")}};Spacer(Modifier.height(24.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){MediaKey("−"){hit("VOLUME_DOWN")};MediaKey("🔇"){hit("VOLUME_MUTE")};MediaKey("+"){hit("VOLUME_UP")}}}}
@Composable private fun MediaKey(t:String,on:()->Unit){Box(Modifier.size(74.dp).background(Color(0xFF1A213B),CircleShape).clickable{on()},contentAlignment=Alignment.Center){Text(t,Color(0xFF42D9FF),25.sp)}}
