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
 val scope=rememberCoroutineScope();var edit by remember{mutableStateOf(false)};var scale by remember{mutableFloatStateOf(prefs.getFloat("scale",1f))};var alpha by remember{mutableFloatStateOf(prefs.getFloat("alpha",.88f))}
 val defaults=remember{listOf(PadKey("lt","LT","LT",.08f,.10f),PadKey("rt","RT","RT",.78f,.10f),PadKey("lb","LB","LB",.08f,.23f),PadKey("rb","RB","RB",.78f,.23f),PadKey("up","↑","UP",.16f,.43f),PadKey("left","←","LEFT",.05f,.57f),PadKey("right","→","RIGHT",.27f,.57f),PadKey("down","↓","DOWN",.16f,.71f),PadKey("y","Y","Y",.76f,.43f),PadKey("b","B","B",.87f,.57f),PadKey("x","X","X",.65f,.57f),PadKey("a","A","A",.76f,.71f),PadKey("l3","L3","L3",.18f,.84f),PadKey("select","SELECT","SELECT",.37f,.86f),PadKey("start","START","START",.55f,.86f),PadKey("r3","R3","R3",.78f,.84f))}
 var pos by remember{mutableStateOf(defaults.associate{it.id to Offset(prefs.getFloat(it.id+"_x",it.x),prefs.getFloat(it.id+"_y",it.y))})}
 fun send(k:String){if(!edit)scope.launch(Dispatchers.IO){Api.post("/key",k)}}
 Column(Modifier.fillMaxSize()){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Title("Controle",if(edit)"Editor de layout" else "Layout personalizado");TextButton(onClick={edit=!edit}){Text(if(edit)"SALVAR" else "EDITAR")}}
  if(edit){Text("Tamanho",color=Color.White);Slider(scale,{scale=it},valueRange=.65f..1.45f);Text("Opacidade",color=Color.White);Slider(alpha,{alpha=it},valueRange=.35f..1f)}
  BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color(0x22000000))){
   val w=maxWidth;val h=maxHeight
   defaults.forEach{k->val p=pos[k.id]?:Offset(k.x,k.y);Box(Modifier.offset(w*p.x,h*p.y).size((54*scale).dp).background(Color(0xFF171D34).copy(alpha=alpha),CircleShape).pointerInput(edit,k.id){if(edit)detectDragGestures{ch,d->ch.consume();val nx=(p.x+d.x/constraints.maxWidth).coerceIn(0f,.9f);val ny=(p.y+d.y/constraints.maxHeight).coerceIn(0f,.9f);pos=pos+(k.id to Offset(nx,ny));prefs.edit().putFloat(k.id+"_x",nx).putFloat(k.id+"_y",ny).apply()}}.clickable{send(k.command)},contentAlignment=Alignment.Center){Text(k.label,color=if(edit)Color(0xFF42D9FF) else Color.White)}}
  }
  if(edit)Button(onClick={prefs.edit().clear().apply();pos=defaults.associate{it.id to Offset(it.x,it.y)};scale=1f;alpha=.88f},Modifier.fillMaxWidth()){Text("RESTAURAR PADRÃO")}
 }
 LaunchedEffect(scale,alpha){prefs.edit().putFloat("scale",scale).putFloat("alpha",alpha).apply()}
}
