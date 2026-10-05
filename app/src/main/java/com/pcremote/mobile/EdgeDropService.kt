package com.pcremote.mobile

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.*
import android.widget.TextView
import java.net.URLEncoder
import kotlin.concurrent.thread

class EdgeDropService: Service() {
 private var wm: WindowManager?=null
 private var target: TextView?=null
 private val channel="pc_drop_edge"
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();startForegroundMode();showTarget()}

 private fun startForegroundMode(){
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel(channel,"NEXUS Drop Edge",NotificationManager.IMPORTANCE_LOW))
  val n=Notification.Builder(this,channel).setContentTitle("NEXUS Drop Edge ativo").setContentText("Arraste conteúdo para o topo ou compartilhe com NEXUS").setSmallIcon(android.R.drawable.stat_sys_upload).build()
  startForeground(41,n)
 }

 private fun showTarget(){
  if(!Settings.canDrawOverlays(this))return
  wm=getSystemService(WINDOW_SERVICE) as WindowManager
  val h=(112*resources.displayMetrics.density).toInt().coerceAtLeast(96)
  target=TextView(this).apply{
   gravity=Gravity.CENTER
   textSize=14f
   text=""
   setPadding(20,0,20,0)
   setBackgroundColor(0x01000000)
   setOnDragListener{_,e->
    when(e.action){
     DragEvent.ACTION_DRAG_STARTED->{text="";setBackgroundColor(0x01000000);true}
     DragEvent.ACTION_DRAG_ENTERED,DragEvent.ACTION_DRAG_LOCATION->{
      text="↑ SOLTE AQUI PARA ENVIAR AO PC";setTextColor(0xFFFFFFFF.toInt());setBackgroundColor(0xE5121A24.toInt());true
     }
     DragEvent.ACTION_DRAG_EXITED->{text="";setBackgroundColor(0x01000000);true}
     DragEvent.ACTION_DROP->{
      text="ENVIANDO…";setBackgroundColor(0xE5121A24.toInt())
      sendClip(e.clipData)
      postDelayed({text="";setBackgroundColor(0x01000000)},1100)
      true
     }
     DragEvent.ACTION_DRAG_ENDED->{postDelayed({text="";setBackgroundColor(0x01000000)},180);true}
     else->true
    }
   }
  }
  val p=WindowManager.LayoutParams(
   WindowManager.LayoutParams.MATCH_PARENT,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
   PixelFormat.TRANSLUCENT
  ).apply{gravity=Gravity.TOP or Gravity.START;x=0;y=0}
  runCatching{wm?.addView(target,p)}
 }

 private fun notifyResult(ok:Boolean,name:String){
  val nm=getSystemService(NotificationManager::class.java)
  val n=Notification.Builder(this,channel)
   .setSmallIcon(if(ok)android.R.drawable.stat_sys_upload_done else android.R.drawable.stat_notify_error)
   .setContentTitle(if(ok)"Enviado ao PC" else "Falha ao enviar")
   .setContentText(name)
   .setAutoCancel(true).build()
  nm.notify((System.currentTimeMillis()%100000).toInt(),n)
 }

 private fun sendClip(clip:android.content.ClipData?){if(clip==null)return
  thread{
   for(i in 0 until clip.itemCount){
    val item=clip.getItemAt(i); val uri:Uri?=item.uri
    if(uri!=null) sendUri(uri) else {
     val value=item.text?.toString() ?: item.coerceToText(this).toString()
     if(value.isNotBlank()) notifyResult(RemoteClient.post("/clipboard",value.toByteArray()),"Texto/Link")
    }
   }
  }
 }

 private fun displayName(uri:Uri):String=try{
  contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0) else null}
   ?:uri.lastPathSegment?.substringAfterLast('/')?:"drop-file"
 }catch(_:Exception){uri.lastPathSegment?.substringAfterLast('/')?:"drop-file"}

 private fun sendUri(uri:Uri){
  val name=displayName(uri).takeLast(180)
  try{
   runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
   val bytes=contentResolver.openInputStream(uri)?.use{it.readBytes()}
    ?:contentResolver.openAssetFileDescriptor(uri,"r")?.use{afd->afd.createInputStream().readBytes()}
    ?:run{notifyResult(false,name);return}
   if(bytes.isEmpty()){notifyResult(false,name);return}
   val ok=RemoteClient.post("/upload/"+URLEncoder.encode(name,"UTF-8"),bytes)
   notifyResult(ok,name)
  }catch(_:Exception){notifyResult(false,name)}
 }

 override fun onDestroy(){target?.let{runCatching{wm?.removeView(it)}};super.onDestroy()}
}
