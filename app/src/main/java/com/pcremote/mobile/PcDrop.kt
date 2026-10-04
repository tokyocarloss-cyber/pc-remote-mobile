package com.pcremote.mobile

import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable fun PcDrop(){
 val ctx=LocalContext.current;val scope=rememberCoroutineScope();var text by remember{mutableStateOf("")};var status by remember{mutableStateOf("Pronto para enviar")}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri:Uri?->if(uri!=null){scope.launch(Dispatchers.IO){status="Enviando…";try{val bytes=ctx.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:byteArrayOf();val name=uri.lastPathSegment?.substringAfterLast('/')?:"arquivo";val ok=RemoteClient.post("/upload/${URLEncoder.encode(name,"UTF-8")}",bytes);status=if(ok)"✓ Arquivo enviado" else "Falha no envio"}catch(_:Exception){status="Falha no envio"}}}}
 Column{Title("PC Drop","Celular ↔ Windows");Spacer(Modifier.height(14.dp));Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFF111A31),RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(64.dp).background(Color(0x2232D7FF),CircleShape),contentAlignment=Alignment.Center){Text("⇅",Color(0xFF32D7FF),36.sp)};Spacer(Modifier.height(8.dp));Text(status,color=Color.White)}};Spacer(Modifier.height(12.dp));Button(onClick={picker.launch("*/*")},Modifier.fillMaxWidth()){Text("ESCOLHER ARQUIVO")};Spacer(Modifier.height(14.dp));OutlinedTextField(text,{text=it},Modifier.fillMaxWidth(),label={Text("Texto / link")},minLines=2);Spacer(Modifier.height(8.dp));Row{Button(onClick={scope.launch(Dispatchers.IO){RemoteClient.post("/clipboard",text.toByteArray());status="✓ Copiado no PC"}},Modifier.weight(1f)){Text("CLIPBOARD")};Spacer(Modifier.width(8.dp));Button(onClick={scope.launch(Dispatchers.IO){Api.post("/text",text);status="✓ Digitado no PC"}},Modifier.weight(1f)){Text("DIGITAR")}}}
}
