package com.pcremote.mobile

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun MediaDeck() {
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(50f) }
    var muted by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("Conecte ao PC para consultar o volume real") }
    var authCode by remember { mutableStateOf(HubStore.key(ctx)) }
    var authDialog by remember { mutableStateOf(false) }

    fun mediaKey(key:String) { scope.launch(Dispatchers.IO){ Api.post("/key",key) } }
    fun setMute(next:Boolean) {
        lastTouch=SystemClock.elapsedRealtime()
        muted=next
        scope.launch(Dispatchers.IO) {
            val ok=RecorderClient.post(ctx,"/audio/set",
                JSONObject().put("muted",next).toString().toByteArray())
            if(!ok) status="Falha ao silenciar o PC"
        }
    }

    LaunchedEffect(Api.host, Api.connected) {
        while(isActive){
            if(Api.host.isNotBlank() && HubStore.key(ctx).isNotBlank()){
                val data=withContext(Dispatchers.IO){ RecorderClient.get(ctx,"/audio/status") }
                if(data!=null){
                    try{
                        val obj=JSONObject(String(data,Charsets.UTF_8))
                        available=obj.optBoolean("available",false)
                        if(available) {
                            if(SystemClock.elapsedRealtime()-lastTouch>1800L) {
                                volume=obj.optDouble("volume",volume.toDouble()).toFloat()
                                muted=obj.optBoolean("muted",false)
                            }
                            status="Volume do Windows sincronizado"
                        }
                    }catch(_:Exception){ status="Falha ao interpretar volume" }
                }else{
                    available=false
                    status="Sem retorno do volume: confira o código NEXUS Central"
                }
            }
            delay(950)
        }
    }

    // Requests from dragging are conflated by Compose's effect restart:
    // only the most recent slider position is sent after 80 ms.
    LaunchedEffect(volume,lastTouch) {
        if(lastTouch!=0L && SystemClock.elapsedRealtime()-lastTouch<2000L &&
           Api.host.isNotBlank() && HubStore.key(ctx).isNotBlank()){
            delay(80)
            val value=volume.toInt().coerceIn(0,100)
            val ok=withContext(Dispatchers.IO) {
                RecorderClient.post(ctx,"/audio/set",
                    JSONObject().put("volume",value).toString().toByteArray())
            }
            if(!ok) status="Não foi possível ajustar o volume"
        }
    }

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Title("Mídia", "Volume real, sem depender de teclas repetidas")
        Surface(color=NexusUi.Panel,shape=RoundedCornerShape(22.dp),
            border=BorderStroke(1.dp,NexusUi.Border),
            modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.VolumeUp,null,tint=NexusUi.Accent)
                    Spacer(Modifier.width(9.dp))
                    Text("VOLUME PRINCIPAL",color=NexusUi.Text,fontWeight=FontWeight.Bold,
                        modifier=Modifier.weight(1f))
                    Text("${volume.toInt()}%",color=NexusUi.Accent,fontWeight=FontWeight.Bold)
                }
                Slider(value=volume,onValueChange={
                    volume=it
                    lastTouch=SystemClock.elapsedRealtime()
                },valueRange=0f..100f,enabled=Api.connected)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                    OutlinedButton(onClick={setMute(!muted)},enabled=Api.connected){
                        Icon(if(muted)Icons.Default.VolumeOff else Icons.Default.VolumeUp,null)
                        Spacer(Modifier.width(5.dp))
                        Text(if(muted)"ATIVAR SOM" else "SILENCIAR")
                    }
                    TextButton(onClick={authDialog=true}){Text("CÓDIGO CENTRAL")}
                }
                Text(status,color=if(available)NexusUi.Success else NexusUi.Muted,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        Surface(color=NexusUi.PanelRaised,shape=RoundedCornerShape(22.dp),
            border=BorderStroke(1.dp,NexusUi.Border),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),
                horizontalAlignment=Alignment.CenterHorizontally){
                Text("REPRODUÇÃO",color=NexusUi.Text,fontWeight=FontWeight.Black)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,
                    verticalAlignment=Alignment.CenterVertically){
                    MediaKey(Icons.Default.SkipPrevious,56){mediaKey("MEDIA_PREV")}
                    MediaKey(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,76,true){
                        playing=!playing; mediaKey("MEDIA_PLAY")
                    }
                    MediaKey(Icons.Default.SkipNext,56){mediaKey("MEDIA_NEXT")}
                }
            }
        }
    }
    if(authDialog){
        AlertDialog(onDismissRequest={authDialog=false},
            title={Text("Código NEXUS Central")},
            text={OutlinedTextField(value=authCode,onValueChange={authCode=it},
                singleLine=true,label={Text("Código no menu do Windows")})},
            confirmButton={TextButton(onClick={HubStore.setKey(ctx,authCode.trim());authDialog=false}){
                Text("SALVAR")}},
            dismissButton={TextButton(onClick={authDialog=false}){Text("CANCELAR")}})
    }
}

@Composable
private fun MediaKey(icon:ImageVector,size:Int,primary:Boolean=false,onClick:()->Unit){
    Box(Modifier.size(size.dp)
        .background(if(primary)NexusUi.AccentStrong else NexusUi.Panel,CircleShape)
        .clickable(onClick=onClick),contentAlignment=Alignment.Center){
        Icon(icon,null,tint=if(primary)androidx.compose.ui.graphics.Color.White else NexusUi.Accent,
            modifier=Modifier.size((size*.42f).dp))
    }
}
