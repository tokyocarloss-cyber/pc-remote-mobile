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

@Composable fun LandscapeGamepad(){val s=rememberCoroutineScope();fun hit(k:String){s.launch(Dispatchers.IO){Api.post("/key",k)}};Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column(horizontalAlignment=Alignment.CenterHorizontally){RoundKey("↑"){hit("UP")};Row{RoundKey("←"){hit("LEFT")};Spacer(Modifier.width(50.dp));RoundKey("→"){hit("RIGHT")}};RoundKey("↓"){hit("DOWN")}};Column(horizontalAlignment=Alignment.CenterHorizontally){Text("PC REMOTE",Color.White,18.sp);Spacer(Modifier.height(8.dp));Row{Mini("SELECT"){hit("ESC")};Spacer(Modifier.width(8.dp));Mini("START"){hit("ENTER")}}};Column{Row{RoundKey("Y"){hit("Y")};RoundKey("B"){hit("B")}};Row{RoundKey("X"){hit("X")};RoundKey("A"){hit("A")}}}}}
@Composable private fun RoundKey(t:String,on:()->Unit){Box(Modifier.padding(4.dp).size(58.dp).background(Color(0xFF1A213B),CircleShape).clickable{on()},contentAlignment=Alignment.Center){Text(t,Color(0xFF42D9FF),18.sp)}}
@Composable private fun Mini(t:String,on:()->Unit){Box(Modifier.background(Color(0xFF1A213B),CircleShape).clickable{on()}.padding(horizontal=14.dp,vertical=9.dp)){Text(t,Color.White,9.sp)}}
