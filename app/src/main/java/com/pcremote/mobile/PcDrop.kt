package com.pcremote.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable fun PcDrop(){
 val ctx=LocalContext.current;val scope=rememberCoroutineScope();var text by remember{mutableStateOf("")};var status by remember{mutableStateOf("Pronto para enviar")};var edgeOn by remember{mutableStateOf(false)}
 val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->status=if(granted)"Notificações autorizadas • ative o Drop Edge" else "Drop Edge funciona, mas notificações podem ficar ocultas"}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri:Uri?->if(uri!=null){scope.launch(Dispatchers.IO){status="Enviando…";try{val bytes=ctx.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:byteArrayOf();val name=uri.lastPathSegment?.substringAfterLast('/')?:"arquivo";val ok=RemoteClient.post("/upload/"+URLEncoder.encode(name,"UTF-8"),bytes);status=if(ok)"✓ Arquivo enviado" else "Falha no envio"}catch(_:Exception){status="Falha no envio"}}}}
 fun enable(){
  if(Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
  if(!Settings.canDrawOverlays(ctx)){ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+ctx.packageName)));status="Autorize sobreposição e toque novamente"}
  else{ctx.startForegroundService(Intent(ctx,EdgeDropService::class.java));ctx.startForegroundService(Intent(ctx,PhoneInboxService::class.java));edgeOn=true;status="✓ Drop Edge ativo"}
 }
 fun disable(){ctx.stopService(Intent(ctx,EdgeDropService::class.java));ctx.stopService(Intent(ctx,PhoneInboxService::class.java));edgeOn=false;status="Drop Edge desativado"}
 Column{
  Title("NEXUS Drop","Celular ↔ Windows sem cabo");Spacer(Modifier.height(12.dp))
  Surface(color=Color(0xFF09111A),shape=RoundedCornerShape(26.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.background(Brush.linearGradient(listOf(Color(0x22159BFF),Color.Transparent))).padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(46.dp).background(Color(0x22159BFF),CircleShape),contentAlignment=Alignment.Center){Text("⇄",color=Color(0xFF5DE7FF),fontSize=25.sp)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("DROP EDGE",color=Color.White,fontWeight=FontWeight.Black);Text("Arraste no topo do celular ou canto do PC",color=Color(0xFF9299AE),fontSize=10.sp)}};Spacer(Modifier.height(12.dp));Button(onClick={if(edgeOn)disable() else enable()},Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text(if(edgeOn)"DESATIVAR" else "ATIVAR DROP EDGE")}}}
  Spacer(Modifier.height(10.dp));Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF0C141E),RoundedCornerShape(26.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("⇅",color=Color(0xFF5DE7FF),fontSize=34.sp);Spacer(Modifier.height(5.dp));Text(status,color=Color.White,fontWeight=FontWeight.SemiBold)}}
  Spacer(Modifier.height(10.dp));Button(onClick={picker.launch("*/*")},Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(16.dp)){Text("ESCOLHER ARQUIVO")}
  Spacer(Modifier.height(12.dp));OutlinedTextField(text,{text=it},Modifier.fillMaxWidth(),label={Text("Texto ou link")},minLines=2,shape=RoundedCornerShape(18.dp));Spacer(Modifier.height(8.dp))
  Row{OutlinedButton(onClick={scope.launch(Dispatchers.IO){RemoteClient.post("/clipboard",text.toByteArray());status="✓ Copiado no PC"}},Modifier.weight(1f)){Text("COPIAR")};Spacer(Modifier.width(8.dp));Button(onClick={scope.launch(Dispatchers.IO){Api.post("/text",text);status="✓ Digitado no PC"}},Modifier.weight(1f)){Text("DIGITAR")}}
 }
}
