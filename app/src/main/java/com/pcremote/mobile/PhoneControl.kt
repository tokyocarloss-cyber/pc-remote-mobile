package com.pcremote.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@Composable
fun PhoneControlScreen(){
    val ctx=LocalContext.current
    val prefs=remember{ctx.getSharedPreferences("nexus_phone_control",Context.MODE_PRIVATE)}
    var enabled by remember{mutableStateOf(PhoneControlService.active)}
    var sharingNotifications by remember{mutableStateOf(prefs.getBoolean("share_notifications",false))}
    var code by remember{mutableStateOf(HubStore.key(ctx))}
    var status by remember{mutableStateOf("")}

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Title("Controle do celular","PC controla volume e recebe notificações autorizadas")
        Surface(color=NexusUi.Panel,shape=RoundedCornerShape(18.dp),
            border=BorderStroke(1.dp,NexusUi.Border)){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Você decide quando o PC pode controlar o volume do seu celular.",
                    color=NexusUi.Text)
                OutlinedTextField(value=code,onValueChange={code=it},
                    label={Text("Código NEXUS Central mostrado no Windows")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                Button(onClick={
                    HubStore.setKey(ctx,code.trim())
                    status="Código salvo"
                },modifier=Modifier.fillMaxWidth()){Text("SALVAR CÓDIGO")}
                Button(onClick={
                    if(enabled || PhoneControlService.active){
                        ctx.stopService(Intent(ctx,PhoneControlService::class.java))
                        enabled=false
                        prefs.edit().putBoolean("enabled",false).apply()
                        status="Controle do celular interrompido"
                    }else if(Api.host.isBlank() || HubStore.key(ctx).isBlank()){
                        status="Conecte ao PC e informe o código de autorização"
                    }else{
                        ctx.startForegroundService(Intent(ctx,PhoneControlService::class.java))
                        enabled=true
                        prefs.edit().putBoolean("enabled",true).apply()
                        status="Serviço iniciado. Confira o painel Windows."
                    }
                },modifier=Modifier.fillMaxWidth()){
                    Text(if(enabled)"DESATIVAR CONTROLE REMOTO" else "ATIVAR CONTROLE DO CELULAR")
                }
                HorizontalDivider()
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    Switch(checked=sharingNotifications,onCheckedChange={
                        sharingNotifications=it
                        prefs.edit().putBoolean("share_notifications",it).apply()
                    })
                    Spacer(Modifier.width(9.dp))
                    Text("Compartilhar título e texto das notificações",
                        color=NexusUi.Text,style=MaterialTheme.typography.bodyMedium,
                        modifier=Modifier.weight(1f))
                }
                OutlinedButton(onClick={
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Default.NotificationsActive,null)
                    Spacer(Modifier.width(7.dp))
                    Text("AUTORIZAR NOTIFICAÇÕES NO ANDROID")
                }
                Text("Notificações são opcionais. O Windows recebe só os eventos novos, mantém até 30 itens em memória e não grava um histórico em disco. A conexão local atual não usa TLS: use apenas uma rede confiável.",
                    color=NexusUi.Muted,style=MaterialTheme.typography.bodySmall)
                if(status.isNotBlank()) Text(status,color=NexusUi.Accent)
            }
        }
    }
}

class PhoneControlService:Service(){
    companion object{
        var active by mutableStateOf(false)
        private const val CHANNEL="nexus_phone_control"
        const val STOP="com.pcremote.mobile.STOP_CONTROL"
    }
    private val running=AtomicBoolean(false)
    private var worker:Thread?=null
    override fun onBind(intent:Intent?):IBinder?=null

    private fun statusMessage():ByteArray{
        val audio=getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val value=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val percent=(value * 100 / max).coerceIn(0,100)
        val muted=audio.isStreamMute(AudioManager.STREAM_MUSIC)
        return JSONObject().put("volume",percent).put("muted",muted)
            .put("enabled",running.get()).toString().toByteArray()
    }

    private fun applyCommand(raw:ByteArray){
        val json=JSONObject(String(raw,Charsets.UTF_8))
        val audio=getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when(json.optString("action")){
            "volume"->{
                val v=json.optInt("value",-1)
                if(v in 0..100){
                    val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,(v*max+50)/100,0)
                }
            }
            "mute"->{
                val bool=json.opt("value") as? Boolean ?: return
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    if(bool)AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,0)
            }
        }
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==STOP){stopSelf();return START_NOT_STICKY}
        if(running.get())return START_NOT_STICKY
        val notifications=getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"NEXUS • Controle do celular",
            NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,77,Intent(this,PhoneControlService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val note=Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("NEXUS • Controle do celular ativo")
            .setContentText("O PC pode alterar seu volume. Toque em Parar para encerrar.")
            .addAction(Notification.Action.Builder(null,"PARAR",stop).build()).setOngoing(true).build()
        if(Build.VERSION.SDK_INT>=34)
            startForeground(77,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(77,note)
        running.set(true)
        active=true
        getSharedPreferences("nexus_phone_control",MODE_PRIVATE).edit().putBoolean("enabled",true).apply()
        worker=thread(isDaemon=true,name="NEXUS-PhoneControl"){
            while(running.get()){
                if(Api.host.isNotBlank() && HubStore.key(this).isNotBlank()){
                    try{
                        val next=RecorderClient.get(this,"/phone/control/next")
                        if(next!=null)applyCommand(next)
                        RecorderClient.post(this,"/phone/control/state",statusMessage())
                    }catch(_:Exception){}
                }
                try{Thread.sleep(850)}catch(_:InterruptedException){break}
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy(){
        running.set(false)
        active=false
        getSharedPreferences("nexus_phone_control",MODE_PRIVATE).edit().putBoolean("enabled",false).apply()
        worker?.interrupt()
        super.onDestroy()
    }
}

class NexusNotificationRelay:NotificationListenerService(){
    private val queue=Executors.newSingleThreadExecutor()
    override fun onNotificationPosted(sbn:StatusBarNotification?){
        if(sbn==null || sbn.packageName==packageName)return
        val prefs=getSharedPreferences("nexus_phone_control",Context.MODE_PRIVATE)
        if(!prefs.getBoolean("enabled",false) || !prefs.getBoolean("share_notifications",false))return
        val extras=sbn.notification.extras
        val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(150)
        val text=extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().take(150)
        val app=sbn.packageName.substringAfterLast('.').take(50)
        queue.execute {
            try {
                val payload=JSONObject().put("app",app).put("title",title).put("text",text)
                RecorderClient.post(this,"/phone/control/notification",
                    payload.toString().toByteArray())
            }catch(_:Exception){}
        }
    }
    override fun onDestroy(){
        queue.shutdownNow()
        super.onDestroy()
    }
}
