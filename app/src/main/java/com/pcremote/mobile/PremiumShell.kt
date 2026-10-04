package com.pcremote.mobile

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Deep = Color(0xFF050713)
private val Glass = Color(0xCC12182C)
private val Neon = Color(0xFF42D9FF)
private val Purple = Color(0xFF8B63FF)

@Composable fun PremiumShell(page:Int,setPage:(Int)->Unit,content:@Composable () -> Unit){
 val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
 Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF18234B),Deep)))){
  if(landscape) Row(Modifier.fillMaxSize().padding(14.dp)){SideRail(page,setPage);Spacer(Modifier.width(14.dp));Surface(color=Glass,shape=RoundedCornerShape(30.dp),modifier=Modifier.weight(1f).fillMaxHeight()){Box(Modifier.padding(20.dp)){content()}}}
  else Column(Modifier.fillMaxSize().padding(14.dp)){PremiumStatus();Spacer(Modifier.height(12.dp));Surface(color=Glass,shape=RoundedCornerShape(30.dp),modifier=Modifier.weight(1f).fillMaxWidth()){Box(Modifier.padding(18.dp)){content()}};Spacer(Modifier.height(10.dp));BottomDock(page,setPage)}
 }
}
@Composable fun PremiumStatus(){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column{Text("PC REMOTE",Color.White,22.sp,fontWeight=FontWeight.Black);Text(if(Api.connected)"● ${Api.host} ONLINE" else "● PROCURANDO SEU PC",if(Api.connected)Color(0xFF62F29A) else Neon,10.sp,fontWeight=FontWeight.Bold)};Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(Neon,Purple)),CircleShape),contentAlignment=Alignment.Center){Text("PC",Color.White,11.sp,fontWeight=FontWeight.Black)}}}
private val nav=listOf("⌂" to "Home","▦" to "Apps","⌁" to "Mouse","🎮" to "Pad","⇄" to "Drop","▣" to "Tela","⌨" to "Texto","♫" to "Mídia")
@Composable fun BottomDock(sel:Int,set:(Int)->Unit){Surface(color=Glass,shape=RoundedCornerShape(26.dp)){Row(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.SpaceEvenly){nav.forEachIndexed{i,n->DockItem(n.first,n.second,i==sel){set(i)}}}}}
@Composable fun SideRail(sel:Int,set:(Int)->Unit){Surface(color=Glass,shape=RoundedCornerShape(28.dp),modifier=Modifier.width(82.dp).fillMaxHeight()){Column(Modifier.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Neon,Purple)),CircleShape),contentAlignment=Alignment.Center){Text("PC",Color.White,fontWeight=FontWeight.Black)};Spacer(Modifier.weight(1f));nav.forEachIndexed{i,n->DockItem(n.first,n.second,i==sel){set(i)}};Spacer(Modifier.weight(1f))}}}
@Composable private fun DockItem(icon:String,label:String,active:Boolean,on:()->Unit){val s by animateFloatAsState(if(active)1.08f else .94f,label="dock");Column(Modifier.width(58.dp).scale(s).clickable{on()}.padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(36.dp).background(if(active)Neon.copy(.18f) else Color.Transparent,CircleShape),contentAlignment=Alignment.Center){Text(icon,if(active)Neon else Color(0xFF8F98B5),18.sp)};Text(label,if(active)Color.White else Color(0xFF8F98B5),8.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal)}}
