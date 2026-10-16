package com.pcremote.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private fun sendDocument(ctx: Context, uri: Uri, rawName: String, size: Long): Boolean {
    if (Api.host.isBlank() || size !in 1L..268435456L) return false
    val name = rawName.replace(Regex("[\\r\\n/\\\\]"), "_").take(130)
    var c: HttpURLConnection? = null
    return try {
        c = URL("http://${Api.host}:8765/upload/" + URLEncoder.encode(name, "UTF-8"))
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 2500
        c.readTimeout = 20000
        c.doOutput = true
        c.setFixedLengthStreamingMode(size)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            c.outputStream.use { output ->
                input.copyTo(output, 65536)
            }
        } ?: return false
        c.responseCode in 200..299
    } catch (_: Exception) {
        false
    } finally {
        c?.disconnect()
    }
}

@Composable
fun NexusFilesHub() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Title("Arquivos", "Explore pastas reais do celular e do PC")
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex=tab, containerColor=NexusUi.Panel) {
            listOf("Celular","PC","Enviar").forEachIndexed { index,label ->
                Tab(selected=tab==index, onClick={tab=index}, text={ Text(label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when(tab){
            0 -> PhoneSafExplorer()
            1 -> NexusFlowScreen()
            else -> PcDrop()
        }
    }
}

@Composable
private fun PhoneSafExplorer(){
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("nexus_files", Context.MODE_PRIVATE) }
    var tree by remember { mutableStateOf(prefs.getString("tree","").orEmpty()) }
    var current by remember { mutableStateOf<Uri?>(null) }
    var trail by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("tree",uri.toString()).apply()
                tree=uri.toString()
                current=null
                trail=emptyList()
                status="Pasta autorizada"
                refresh++
            } catch (_: Exception) { status="Sem permissão para essa pasta" }
        }
    }

    LaunchedEffect(tree,current,refresh) {
        if(tree.isBlank()) { entries=emptyList();return@LaunchedEffect }
        loading=true
        entries=withContext(Dispatchers.IO){
            try{
                val root=DocumentFile.fromTreeUri(ctx,Uri.parse(tree))
                val folder=if(current==null)root
                    else DocumentFile.fromTreeUri(ctx,current!!)
                if(folder?.isDirectory!=true) emptyList()
                else folder.listFiles().sortedWith(compareBy<DocumentFile>{!it.isDirectory}.thenBy{it.name?.lowercase()?:""})
            }catch(_:Exception){emptyList()}
        }
        loading=false
    }

    fun openFolder(file:DocumentFile) {
        val actual=current ?: Uri.parse(tree)
        trail=trail + actual
        current=file.uri
        search=""
    }
    fun goBack(){
        if(trail.isNotEmpty()){
            current=trail.last().takeUnless{it.toString()==tree}
            trail=trail.dropLast(1)
        }else current=null
        search=""
    }
    fun viewFile(file:DocumentFile){
        try{
            ctx.startActivity(Intent(Intent.ACTION_VIEW).apply{
                setDataAndType(file.uri,file.type?:"*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }catch(_:Exception){status="Não há aplicativo para abrir esse arquivo"}
    }
    fun sendFile(file:DocumentFile){
        scope.launch {
            status="Enviando ${file.name}..."
            val ok=withContext(Dispatchers.IO){
                sendDocument(ctx,file.uri,file.name?:"arquivo",file.length())
            }
            status=if(ok)"Arquivo enviado ao PC: ${file.name}"
                else "Envio falhou; verifique conexão, tamanho e permissões"
        }
    }

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
            Button(onClick={picker.launch(null)},modifier=Modifier.weight(1f)){
                Icon(Icons.Default.FolderOpen,null,Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if(tree.isBlank())"ESCOLHER PASTA" else "TROCAR PASTA")
            }
            IconButton(onClick={refresh++},enabled=tree.isNotBlank()){
                Icon(Icons.Default.Refresh,"Atualizar")
            }
        }
        if(tree.isBlank()){
            Text("Escolha uma pasta do armazenamento interno. O Android permite explorar suas subpastas e arquivos sem acesso irrestrito.",
                color=NexusUi.Muted,style=MaterialTheme.typography.bodyMedium)
        }else{
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={goBack()},enabled=current!=null){
                    Icon(Icons.Default.ArrowBack,"Voltar")
                }
                Text("Pasta autorizada" + if(trail.isEmpty())" / início" else " / nível ${trail.size}",
                    color=NexusUi.Text,style=MaterialTheme.typography.bodyMedium,
                    modifier=Modifier.weight(1f))
            }
            OutlinedTextField(value=search,onValueChange={search=it},
                label={Text("Pesquisar nesta pasta")},leadingIcon={Icon(Icons.Default.Search,null)},
                modifier=Modifier.fillMaxWidth(),singleLine=true)
            if(loading) CircularProgressIndicator()
            val visible=entries.filter{search.isBlank() || it.name.orEmpty().contains(search,true)}
            if(!loading && visible.isEmpty()){
                Text(if(entries.isEmpty())"Pasta vazia ou sem acesso" else "Nenhum arquivo encontrado",
                    color=NexusUi.Muted,modifier=Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                items(visible,key={it.uri.toString()}){file->
                    Surface(color=NexusUi.PanelRaised,shape=RoundedCornerShape(12.dp),
                        border=BorderStroke(1.dp,NexusUi.Border)){
                        Row(Modifier.fillMaxWidth().clickable {
                            if(file.isDirectory)openFolder(file) else viewFile(file)
                        }.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(if(file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                null,tint=NexusUi.Accent,modifier=Modifier.size(23.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)){
                                Text(file.name?:"Arquivo",maxLines=2,overflow=TextOverflow.Ellipsis,
                                    color=NexusUi.Text,fontWeight=FontWeight.SemiBold)
                                if(file.isFile) Text("${file.length()/1024} KB",
                                    style=MaterialTheme.typography.labelSmall,color=NexusUi.Muted)
                            }
                            if(file.isFile){
                                IconButton(onClick={sendFile(file)}){
                                    Icon(Icons.Default.Upload,"Enviar ao PC",tint=NexusUi.Accent)
                                }
                            }else Icon(Icons.Default.ChevronRight,null)
                        }
                    }
                }
            }
        }
        if(status.isNotBlank())Text(status,color=NexusUi.Accent,
            style=MaterialTheme.typography.bodySmall)
    }
}
