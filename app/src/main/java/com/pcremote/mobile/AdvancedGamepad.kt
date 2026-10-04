package com.pcremote.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable fun AdvancedGamepad(){
 val scope=rememberCoroutineScope();fun send(k:String){scope.launch(Dispatchers.IO){Api.post("/key",k)}}
 Column(Modifier.fillMaxSize()){
  Title("Controle","Layout móvel completo")
  Spacer(Modifier.height(10.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Shoulder("LT"){send("LT")};Shoulder("RT"){send("RT")}}
  Spacer(Modifier.height(6.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Shoulder("LB"){send("LB")};Shoulder("RB"){send("RB")}}
  Spacer(Modifier.height(14.dp))
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   Column(horizontalAlignment=Alignment.CenterHorizontally){Key("↑"){send("UP")};Row{Key("←"){send("LEFT")};Spacer(Modifier.width(42.dp));Key("→"){send("RIGHT")}};Key("↓"){send("DOWN")}}
   Column(horizontalAlignment=Alignment.CenterHorizontally){Row{Face("Y"){send("Y")};Face("B"){send("B")}};Row{Face("X"){send("X")};Face("A"){send("A")}}}
  }
  Spacer(Modifier.height(16.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Stick("L3"){send("L3")};Shoulder("SELECT"){send("SELECT")};Shoulder("START"){send("START")};Stick("R3"){send("R3")}}
 }
}
@Composable private fun Shoulder(t:String,on:()->Unit){Box(Modifier.width(76.dp).height(42.dp).background(Color(0xFF171D34),RoundedCornerShape(16.dp)).clickable{on()},contentAlignment=Alignment.Center){Text(t,color=Color.White)}}
@Composable private fun Key(t:String,on:()->Unit){Box(Modifier.size(50.dp).background(Color(0xFF171D34),RoundedCornerShape(12.dp)).clickable{on()},contentAlignment=Alignment.Center){Text(t,color=Color.White)}}
@Composable private fun Face(t:String,on:()->Unit){Box(Modifier.padding(5.dp).size(58.dp).background(Color(0xFF171D34),CircleShape).clickable{on()},contentAlignment=Alignment.Center){Text(t,color=Color(0xFF8D5CFF))}}
@Composable private fun Stick(t:String,on:()->Unit){Box(Modifier.size(48.dp).background(Color(0xFF222A46),CircleShape).clickable{on()},contentAlignment=Alignment.Center){Text(t,color=Color(0xFF42D9FF))}}
