package com.pcremote.mobile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
 var pos by remember{mutableStateOf(loadPos())};var hidden by remember{mutableStateOf(emptySet<String>())}
 fun loadProfile(n:String){profile=n;prefs.edit().putString("profile",n).apply();scale=prefs.getFloat("$n:scale",1f);alpha=prefs.getFloat("$n:alpha",.88f);pos=defaults.associate{it.id to Offset(prefs.getFloat("$n:${it.id}:x",it.x),prefs.getFloat("$n:${it.id}:y",it.y))};hidden=defaults.filter{!prefs.getBoolean("$n:${it.id}:visible",true)}.map{it.id}.toSet()}
 LaunchedEffect(Unit){loadProfile(profile)}
 fun pad(json:String){if(!edit)scope.launch(Dispatchers.IO){Api.post("/gamepad",json)}}
 fun button(k:String,down:Boolean)=pad("{\"kind\":\"button\",\"button\":\""+k+"\",\"down\":"+down+"}")
 fun trigger(k:String,v:Float)=pad("{\"kind\":\"trigger\",\"trigger\":\""+k+"\",\"value\":"+v+"}")
 fun stick(k:String,x:Float,y:Float)=pad("{\"kind\":\"stick\",\"stick\":\""+k+"\",\"x\":"+x+",\"y\":"+(-y)+"}")
 Column(Modifier.fillMaxSize().background(Color(0xFF05080D))){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Box{TextButton(onClick={profileMenu=true}){Text("PERFIL  •  $profile",color=Color.White)};DropdownMenu(profileMenu,{profileMenu=false}){listOf("Padrão","Corrida","Ação","Personalizado").forEach{n->DropdownMenuItem({Text(n)},{loadProfile(n);profileMenu=false})}}}
   Button(onClick={edit=!edit}){Text(if(edit)"SALVAR" else "EDITAR")}
  }
  if(edit){Text("Tamanho",color=Color.White);Slider(scale,{scale=it},valueRange=.65f..1.45f);Text("Opacidade",color=Color.White);Slider(alpha,{alpha=it},valueRange=.35f..1f);Text("Toque num botão para ocultar/mostrar pelo painel abaixo",color=Color(0xFF42D9FF));Column(verticalArrangement=Arrangement.spacedBy(4.dp)){defaults.chunked(6).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){row.forEach{k->FilterChip(selected=k.id !in hidden,onClick={hidden=if(k.id in hidden)hidden-k.id else hidden+k.id;prefs.edit().putBoolean(key(k.id,"visible"),k.id !in hidden).apply()},label={Text(k.label)})}}}}}
  BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF080D14))){
   val w=maxWidth;val h=maxHeight
   defaults.filter{it.id !in hidden}.forEach{k->val p=pos[k.id]?:Offset(k.x,k.y)
    Box(Modifier.offset(w*p.x,h*p.y).size((54*scale).dp).background(Color(0xFF111A24).copy(alpha=alpha),CircleShape)
     .pointerInput(edit,k.id,profile){if(edit)detectDragGestures{ch,d->ch.consume();val cur=pos[k.id]?:p;val nx=(cur.x+d.x/constraints.maxWidth).coerceIn(0f,.9f);val ny=(cur.y+d.y/constraints.maxHeight).coerceIn(0f,.9f);pos=pos+(k.id to Offset(nx,ny));prefs.edit().putFloat(key(k.id,"x"),nx).putFloat(key(k.id,"y"),ny).apply()}}
     .pointerInput(edit,k.command){if(!edit)detectTapGestures(onPress={if(k.command=="LT"||k.command=="RT")trigger(k.command,1f) else button(k.command,true);tryAwaitRelease();if(k.command=="LT"||k.command=="RT")trigger(k.command,0f) else button(k.command,false)})},contentAlignment=Alignment.Center){Text(k.label,color=if(edit)Color(0xFF42D9FF) else Color.White)}
   }
   listOf("left" to Offset(.18f,.68f),"right" to Offset(.68f,.68f)).forEach{(side,base)->
    var knob by remember(side){mutableStateOf(Offset.Zero)}
    Box(Modifier.offset(w*base.x,h*base.y).size((96*scale).dp).background(Color(0x55171D34),CircleShape)
     .pointerInput(edit,side){if(!edit)detectDragGestures(
      onDragEnd={knob=Offset.Zero;stick(side,0f,0f)},
      onDragCancel={knob=Offset.Zero;stick(side,0f,0f)}
     ){ch,d->ch.consume();val radius=constraints.maxWidth/2f;val n=knob+d;val len=n.getDistance();knob=if(len>radius)n*(radius/len) else n;stick(side,(knob.x/radius).coerceIn(-1f,1f),(knob.y/radius).coerceIn(-1f,1f))}},
     contentAlignment=Alignment.Center){
      Box(Modifier.offset((knob.x/ctx.resources.displayMetrics.density).dp,(knob.y/ctx.resources.displayMetrics.density).dp).size((44*scale).dp).background(Color(0xFF159BFF).copy(alpha=alpha),CircleShape))
     }
   }
  }
  if(edit)Button(onClick={prefs.edit().apply{defaults.forEach{remove(key(it.id,"x"));remove(key(it.id,"y"))};remove("$profile:scale");remove("$profile:alpha");defaults.forEach{remove(key(it.id,"visible"))}}.apply();scale=1f;alpha=.88f;hidden=emptySet();pos=defaults.associate{it.id to Offset(it.x,it.y)}},Modifier.fillMaxWidth()){Text("RESTAURAR ESTE PERFIL")}
 }
 LaunchedEffect(scale,alpha,profile){prefs.edit().putFloat("$profile:scale",scale).putFloat("$profile:alpha",alpha).apply()}
}
