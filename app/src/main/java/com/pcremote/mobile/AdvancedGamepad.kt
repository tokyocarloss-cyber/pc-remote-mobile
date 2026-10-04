package com.pcremote.mobile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class PadKey(val id:String,val label:String,val command:String,val x:Float,val y:Float)

@Composable fun AdvancedGamepad(){
 val ctx=LocalContext.current;val prefs=remember{ctx.getSharedPreferences("pad_layout",Context.MODE_PRIVATE)}
 var profile by remember{mutableStateOf(prefs.getString("profile","Padrão")?:"Padrão")};var profileMenu by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope();var edit by remember{mutableStateOf(false)};var scale by remember{mutableFloatStateOf(1f)};var alpha by remember{mutableFloatStateOf(.88f)}
 val defaults=remember{listOf(PadKey("lt","LT","LT",.08f,.10f),PadKey("rt","RT","RT",.78f,.10f),PadKey("lb","LB","LB",.08f,.23f),PadKey("rb","RB","RB",.78f,.23f),PadKey("up","↑","UP",.16f,.43f),PadKey("left","←","LEFT",.05f,.57f),PadKey("right","→","RIGHT",.27f,.57f),PadKey("down","↓","DOWN",.16f,.71f),PadKey("y","Y","Y",.76f,.43f),PadKey("b","B","B",.87f,.57f),PadKey("x","X","X",.65f,.57f),PadKey("a","A","A",.76f,.71f),PadKey("l3","L3","L3",.18f,.84f),PadKey("select","SELECT","SELECT",.37f,.86f),PadKey("start","START","START",.55f,.86f),PadKey("r3","R3","R3",.78f,.84f))}
 fun key(id:String,suffix:String)="$profile:$id:$suffix"
 fun loadPos()=defaults.associate{it.id to Offset(prefs.getFloat(key(it.id,"x"),it.x),prefs.getFloat(key(it.id,"y"),it.y))}
 var pos by remember{mutableStateOf(loadPos())}
 fun loadProfile(n:String){profile=n;prefs.edit().putString("profile",n).apply();scale=prefs.getFloat("$n:scale",1f);alpha=prefs.getFloat("$n:alpha",.88f);pos=defaults.associate{it.id to Offset(prefs.getFloat("$n:${it.id}:x",it.x),prefs.getFloat("$n:${it.id}:y",it.y))}}
 LaunchedEffect(Unit){loadProfile(profile)}
 fun send(k:String){if(!edit)scope.launch(Dispatchers.IO){Api.post("/key",k)}}
 Column(Modifier.fillMaxSize()){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Box{TextButton(onClick={profileMenu=true}){Text("PERFIL: $profile")};DropdownMenu(profileMenu,{profileMenu=false}){listOf("Padrão","Corrida","Ação","Personalizado").forEach{n->DropdownMenuItem({Text(n)},{loadProfile(n);profileMenu=false})}}}
   TextButton(onClick={edit=!edit}){Text(if(edit)"SALVAR" else "EDITAR")}
  }
  if(edit){Text("Tamanho",color=Color.White);Slider(scale,{scale=it},valueRange=.65f..1.45f);Text("Opacidade",color=Color.White);Slider(alpha,{alpha=it},valueRange=.35f..1f)}
  BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color(0x22000000))){
   val w=maxWidth;val h=maxHeight
   defaults.forEach{k->val p=pos[k.id]?:Offset(k.x,k.y)
    Box(Modifier.offset(w*p.x,h*p.y).size((54*scale).dp).background(Color(0xFF171D34).copy(alpha=alpha),CircleShape)
     .pointerInput(edit,k.id,profile){if(edit)detectDragGestures{ch,d->ch.consume();val cur=pos[k.id]?:p;val nx=(cur.x+d.x/constraints.maxWidth).coerceIn(0f,.9f);val ny=(cur.y+d.y/constraints.maxHeight).coerceIn(0f,.9f);pos=pos+(k.id to Offset(nx,ny));prefs.edit().putFloat(key(k.id,"x"),nx).putFloat(key(k.id,"y"),ny).apply()}}
     .clickable{send(k.command)},contentAlignment=Alignment.Center){Text(k.label,color=if(edit)Color(0xFF42D9FF) else Color.White)}
   }
  }
  if(edit)Button(onClick={prefs.edit().apply{defaults.forEach{remove(key(it.id,"x"));remove(key(it.id,"y"))};remove("$profile:scale");remove("$profile:alpha")}.apply();scale=1f;alpha=.88f;pos=defaults.associate{it.id to Offset(it.x,it.y)}},Modifier.fillMaxWidth()){Text("RESTAURAR ESTE PERFIL")}
 }
 LaunchedEffect(scale,alpha,profile){prefs.edit().putFloat("$profile:scale",scale).putFloat("$profile:alpha",alpha).apply()}
}
