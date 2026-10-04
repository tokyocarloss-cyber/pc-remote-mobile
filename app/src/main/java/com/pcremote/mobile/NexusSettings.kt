package com.pcremote.mobile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val DefaultStores = listOf("Steam","Epic","Xbox","EA","Ubisoft","Battle.net","GOG","Outros")

fun loadStoreOrder(ctx:Context):List<String>{
 val p=ctx.getSharedPreferences("nexus_settings",Context.MODE_PRIVATE)
 val saved=p.getString("store_order",null)?.split('|')?.filter{it.isNotBlank()}.orEmpty()
 return (saved + DefaultStores).distinct().filter{it in DefaultStores}
}
fun isStoreEnabled(ctx:Context,store:String)=ctx.getSharedPreferences("nexus_settings",Context.MODE_PRIVATE).getBoolean("store_$store",true)

@Composable fun NexusSettings(){
 val ctx=LocalContext.current
 val prefs=remember{ctx.getSharedPreferences("nexus_settings",Context.MODE_PRIVATE)}
 var stores by remember{mutableStateOf(loadStoreOrder(ctx))}
 fun persist(){prefs.edit().putString("store_order",stores.joinToString("|")).apply()}
 Column{
  Title("Configurações","Personalize o NEXUS do seu jeito")
  Spacer(Modifier.height(14.dp))
  Text("LOJAS E LAUNCHERS",color=Color(0xFF5DE7FF),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Black)
  Spacer(Modifier.height(8.dp))
  Text("Escolha o que aparece e mude a ordem usada na biblioteca e na roda radial.",color=Color(0xFF9299AE),style=MaterialTheme.typography.bodySmall)
  Spacer(Modifier.height(10.dp))
  stores.forEachIndexed{index,store->
   var enabled by remember(store){mutableStateOf(prefs.getBoolean("store_$store",true))}
   Surface(color=Color(0xCC101722),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){
    Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
     Box(Modifier.size(38.dp).background(Color(0x22159BFF),CircleShape),contentAlignment=Alignment.Center){Text(store.take(1),color=Color(0xFF5DE7FF),fontWeight=FontWeight.Black)}
     Spacer(Modifier.width(10.dp));Text(store,Modifier.weight(1f),color=Color.White,fontWeight=FontWeight.SemiBold)
     Text("↑",color=if(index>0)Color.White else Color.DarkGray,modifier=Modifier.padding(7.dp).clickable(enabled=index>0){if(index>0){stores=stores.toMutableList().also{java.util.Collections.swap(it,index,index-1)};persist()}})
     Text("↓",color=if(index<stores.lastIndex)Color.White else Color.DarkGray,modifier=Modifier.padding(7.dp).clickable(enabled=index<stores.lastIndex){if(index<stores.lastIndex){stores=stores.toMutableList().also{java.util.Collections.swap(it,index,index+1)};persist()}})
     Switch(enabled,{enabled=it;prefs.edit().putBoolean("store_$store",it).apply()})
    }
   }
  }
  Spacer(Modifier.height(14.dp))
  Surface(color=Color(0xCC101722),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){
   Column(Modifier.padding(14.dp)){Text("VISUAL",color=Color.White,fontWeight=FontWeight.Bold);Text("Tema NEXUS • preto profundo • azul neon",color=Color(0xFF9299AE),style=MaterialTheme.typography.bodySmall)}
  }
 }
}
