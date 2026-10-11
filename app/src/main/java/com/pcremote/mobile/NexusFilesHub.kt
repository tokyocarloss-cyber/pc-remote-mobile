package com.pcremote.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun NexusFilesHub() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()){
            listOf("Celular","PC","Enviar").forEachIndexed{ i,t ->
                FilterChip(selected=tab==i,onClick={tab=i},label={Text(t)},
                    modifier=Modifier.weight(1f))
            }
        }
        when(tab){
            0->PhoneFileExplorer()
            1->NexusFlowScreen()
            else->PcDrop()
        }
    }
}

/**
 * Opt-in file manager for shared internal storage. All-files access must be
 * approved by the user in Android Settings (never silent permission elevation).
 * Android/data, app-private paths and protected documents can still be restricted.
 */
@Composable
fun PhoneFileExplorer(){
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    val root=remember { Environment.getExternalStorageDirectory().canonicalFile }
    var accessTick by remember { mutableIntStateOf(0) }
    val permissionLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ){accessTick++}
    val settingsLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){accessTick++}
    val picker=rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ){uris ->
        if(uris.isNotEmpty())scope.launch(Dispatchers.IO){
            uris.forEach{uri->RemoteClient.upload(ctx,uri,
                runCatching{ctx.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)
                    ?.use{ c->if(c.moveToFirst())c.getString(0) else null } }.getOrNull()
                    ?: "arquivo") }
        }
    }
    val granted=if(Build.VERSION.SDK_INT>=30) Environment.isExternalStorageManager()
       else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED
    var directory by remember { mutableStateOf(root) }
    var children by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    fun inside(f:File): Boolean=runCatching {
        val canonical=f.canonicalFile
        canonical==root || canonical.toPath().startsWith(root.toPath())
    }.getOrDefault(false)
    fun open(f:File){if(inside(f))directory=f}
    fun askAccess(){
        if(Build.VERSION.SDK_INT>=30){
            val intent=Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:"+ctx.packageName))
            settingsLauncher.launch(intent)
        }else{
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    LaunchedEffect(granted,directory,refresh,accessTick){
        if(!granted){children=emptyList();return@LaunchedEffect}
        loading=true
        children=withContext(Dispatchers.IO){
            if(!inside(directory)) emptyList()
            else directory.listFiles()?.filter { inside(it) && !it.isHidden }
                ?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase()})
                ?: emptyList()
        }
        loading=false
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.Smartphone,null,tint=NexusUi.Accent)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)){
                Text("Memória interna",color=NexusUi.Text,fontWeight=FontWeight.Bold)
                Text(if(granted) "Pastas e arquivos compartilhados" else "Permissão de armazenamento necessária",
                    color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,"Atualizar",tint=NexusUi.Accent)}
        }
        if(!granted){
            Text("Para mostrar as pastas do celular diretamente no NEXUS, permita o acesso a todos os arquivos. Algumas pastas protegidas pelo Android continuam restritas.",
                color=NexusUi.Muted,style=MaterialTheme.typography.bodySmall)
            Button(onClick=::askAccess,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.FolderOpen,null);Spacer(Modifier.width(6.dp));Text("PERMITIR ACESSO À MEMÓRIA")
            }
            OutlinedButton(onClick={picker.launch("*/*")},modifier=Modifier.fillMaxWidth()){
                Text("OU ESCOLHER ARQUIVOS PELO ANDROID")
            }
        }else{
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()){
                listOf("Download","DCIM","Documents","Pictures").forEach{folder->
                    AssistChip(onClick={open(File(root,folder))},
                        label={Text(when(folder){"Download"->"Downloads";"DCIM"->"Câmera";"Documents"->"Docs";else->"Imagens"})},
                        modifier=Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()){
                listOf("Movies","Music","Android").forEach{folder->
                    AssistChip(onClick={open(File(root,folder))},label={Text(when(folder){"Movies"->"Vídeos";"Music"->"Músicas";else->"Android"})},modifier=Modifier.weight(1f))
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(enabled=directory!=root,onClick={open(directory.parentFile?:root)}){
                    Icon(Icons.Default.ArrowBack,"Voltar",tint=NexusUi.Accent)
                }
                Text(if(directory==root)"Armazenamento interno" else directory.relativeTo(root).path,
                    color=NexusUi.Text,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                TextButton(onClick={open(root)}){Text("RAIZ")}
            }
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp),contentPadding=PaddingValues(bottom=12.dp)){
                items(children,key={it.absolutePath}){item->
                    Surface(color=NexusUi.Panel,shape=RoundedCornerShape(14.dp),
                        border=BorderStroke(1.dp,NexusUi.Border)){
                        Row(Modifier.fillMaxWidth().clickable(enabled=item.isDirectory){
                            if(item.isDirectory)open(item)
                        }.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(if(item.isDirectory)Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                null,tint=NexusUi.Accent,modifier=Modifier.size(26.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)){
                                Text(item.name,color=NexusUi.Text,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(item.isFile)Text("${item.length()/1024} KB",color=NexusUi.Muted,
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            if(item.isFile) {
                                TextButton(enabled=Api.connected&&!sending,onClick={
                                    sending=true
                                    status="Enviando ${item.name}…"
                                    scope.launch(Dispatchers.IO){
                                        val ok=RemoteClient.uploadFile(item)
                                        withContext(Dispatchers.Main){
                                            status=if(ok)"Enviado: ${item.name}" else RemoteClient.lastError
                                            sending=false
                                        }
                                    }
                                }){Text("ENVIAR")}
                            }else Icon(Icons.Default.ChevronRight,null,tint=NexusUi.Muted)
                        }
                    }
                }
            }
            if(status.isNotBlank())Text(status,color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick={picker.launch("*/*")},modifier=Modifier.fillMaxWidth()){
                Text("SELECIONAR OUTROS ARQUIVOS")
            }
        }
    }
}
