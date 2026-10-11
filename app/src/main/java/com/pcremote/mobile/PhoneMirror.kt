package com.pcremote.mobile

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@Composable
fun PhoneMirrorScreen() {
    val ctx=LocalContext.current
    val manager=remember {
        ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    var status by remember { mutableStateOf("") }
    val request=rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if(result.resultCode==Activity.RESULT_OK && result.data!=null && Api.connected){
            val intent=Intent(ctx,PhoneMirrorService::class.java)
                .putExtra("resultCode",result.resultCode)
                .putExtra("projectionData",result.data)
            ctx.startForegroundService(intent)
            status="Compartilhamento solicitado. Abra a janela 'Tela do celular' no NEXUS do PC."
        } else status="Captura cancelada ou computador não conectado."
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Title("Tela do celular → PC","Espelhe o Android no Windows")
        Surface(color=NexusUi.Panel,shape=RoundedCornerShape(22.dp),
            border=BorderStroke(1.dp,NexusUi.Border)){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Icons.Default.Smartphone,null,tint=NexusUi.Accent,modifier=Modifier.size(36.dp))
                Text(if(PhoneMirrorService.active)"Compartilhamento ativo"
                     else "Compartilhar tela do celular",
                    color=NexusUi.Text,fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.titleMedium)
                Text("Mostre a tela Android no programa NEXUS para Windows. O Android sempre pedirá sua autorização antes de iniciar. Somente o PC pareado recebe as imagens.",
                    color=NexusUi.Muted,style=MaterialTheme.typography.bodySmall)
                Button(
                    enabled=Api.connected&&!PhoneMirrorService.active,
                    onClick={request.launch(manager.createScreenCaptureIntent())},
                    modifier=Modifier.fillMaxWidth()
                ){Text("INICIAR ESPELHAMENTO")}
                OutlinedButton(
                    enabled=PhoneMirrorService.active,
                    onClick={ctx.stopService(Intent(ctx,PhoneMirrorService::class.java))},
                    modifier=Modifier.fillMaxWidth()
                ){Text("PARAR ESPELHAMENTO")}
                Text(if(PhoneMirrorService.active)"Transmissão JPEG experimental, sem áudio • cerca de 6 FPS"
                     else "Conecte o celular ao PC para iniciar",
                    color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
                if(status.isNotBlank())Text(status,color=NexusUi.Accent,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

class PhoneMirrorService:Service(){
    companion object { var active by mutableStateOf(false) }
    private val channel="nexus_phone_mirror"
    private val uploading=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor()
    private var projection:MediaProjection?=null
    private var virtualDisplay:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var captureThread:HandlerThread?=null
    private var lastFrame=0L
    override fun onBind(intent:Intent?):IBinder?=null

    override fun onCreate(){
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel,"NEXUS • Tela do celular",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action == "NEXUS_STOP_MIRROR") { stopSelf(); return START_NOT_STICKY }
        val stop=PendingIntent.getService(this,75,Intent(this,PhoneMirrorService::class.java).setAction("NEXUS_STOP_MIRROR"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,channel)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("NEXUS: compartilhando tela do celular")
            .setContentText("Volte ao NEXUS para interromper o espelhamento.")
            .addAction(Notification.Action.Builder(null,"PARAR",stop).build())
            .setOngoing(true).build()
        if(Build.VERSION.SDK_INT>=29)
            startForeground(74,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(74,notification)

        if(active)return START_NOT_STICKY
        val resultCode=intent?.getIntExtra("resultCode",Activity.RESULT_CANCELED)
            ?:Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data=if(Build.VERSION.SDK_INT>=33)
            intent?.getParcelableExtra("projectionData",Intent::class.java)
            else intent?.getParcelableExtra("projectionData")
        if(resultCode!=Activity.RESULT_OK || data==null || !Api.connected){
            stopSelf();return START_NOT_STICKY
        }
        try{
            val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection=manager.getMediaProjection(resultCode,data)
            captureThread=HandlerThread("NexusMirrorCapture").also{it.start()}
            val handler=Handler(captureThread!!.looper)
            projection!!.registerCallback(object:MediaProjection.Callback(){
                override fun onStop(){stopSelf()}
            },handler)
            val metrics=resources.displayMetrics
            val originalW=metrics.widthPixels.coerceAtLeast(1)
            val originalH=metrics.heightPixels.coerceAtLeast(1)
            val scale=(960f/maxOf(originalW,originalH)).coerceAtMost(1f)
            val width=(originalW*scale).roundToInt().coerceAtLeast(1)
            val height=(originalH*scale).roundToInt().coerceAtLeast(1)
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
            reader!!.setOnImageAvailableListener({ source->
                val image=source.acquireLatestImage()?:return@setOnImageAvailableListener
                val now=SystemClock.elapsedRealtime()
                if(now-lastFrame<150L || !uploading.compareAndSet(false,true)){
                    image.close();return@setOnImageAvailableListener
                }
                lastFrame=now
                try{
                    worker.execute{
                        try{
                            val plane=image.planes[0]
                            val stride=plane.rowStride
                            val pixel=plane.pixelStride
                            if(pixel!=4) return@execute
                            val paddedWidth=stride/pixel
                            val bitmap=Bitmap.createBitmap(paddedWidth,height,Bitmap.Config.ARGB_8888)
                            try{
                                plane.buffer.rewind()
                                bitmap.copyPixelsFromBuffer(plane.buffer)
                                val cropped=Bitmap.createBitmap(bitmap,0,0,width,height)
                                try{
                                    val output=ByteArrayOutputStream(400000)
                                    cropped.compress(Bitmap.CompressFormat.JPEG,58,output)
                                    if(output.size()<1500000)
                                        RemoteClient.post("/phone-screen/frame",output.toByteArray())
                                }finally{cropped.recycle()}
                            }finally{bitmap.recycle()}
                        }catch(_:Exception){}
                        finally{image.close();uploading.set(false)}
                    }
                }catch(_:Exception){image.close();uploading.set(false)}
            },handler)
            virtualDisplay=projection!!.createVirtualDisplay(
                "NEXUS Phone Mirror",width,height,metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface,null,handler)
            active=true
        }catch(_:Exception){stopSelf()}
        return START_NOT_STICKY
    }
    override fun onDestroy(){
        active=false
        reader?.setOnImageAvailableListener(null,null)
        virtualDisplay?.release();reader?.close()
        projection?.stop()
        captureThread?.quitSafely()
        worker.shutdown()
        super.onDestroy()
    }
}
