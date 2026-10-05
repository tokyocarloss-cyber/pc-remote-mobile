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
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();startForegroundMode();showTarget()}
 private fun startForegroundMode(){
  val id="pc_drop_edge"; val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel(id,"PC Drop Edge",NotificationManager.IMPORTANCE_LOW))
  val n=Notification.Builder(this,id).setContentTitle("PC Drop Edge ativo").setContentText("Arraste conteúdo para o topo para enviar ao PC").setSmallIcon(android.R.drawable.stat_sys_upload).build()
  startForeground(41,n)
 }
 private fun showTarget(){
  if(!Settings.canDrawOverlays(this))return
  wm=getSystemService(WINDOW_SERVICE) as WindowManager
  target=TextView(this).apply{
   text=""; setBackgroundColor(0x01000000)
   setOnDragListener{_,e->
    when(e.action){
     DragEvent.ACTION_DRAG_ENTERED->{text="↑ SOLTE PARA ENVIAR";setTextColor(0xFFFFFFFF.toInt());setBackgroundColor(0xCC111629.toInt());true}
     DragEvent.ACTION_DRAG_EXITED->{text="";setBackgroundColor(0x01000000);true}
     DragEvent.ACTION_DROP->{sendClip(e.clipData);text="✓ ENVIANDO AO PC";postDelayed({text="";setBackgroundColor(0x01000000)},900);true}
     else->true
    }
   }
  }
  val p=WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,96,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP}
  wm?.addView(target,p)
 }
 private fun sendClip(clip:android.content.ClipData?){if(clip==null)return
  thread{
   for(i in 0 until clip.itemCount){
    val item=clip.getItemAt(i); val uri:Uri?=item.uri
    if(uri!=null) sendUri(uri) else {
     val value=item.text?.toString() ?: item.coerceToText(this).toString()
     if(value.isNotBlank()) RemoteClient.post("/clipboard",value.toByteArray())
    }
   }
  }
 }
 private fun displayName(uri:Uri):String=try{
  contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0) else null}
   ?:uri.lastPathSegment?.substringAfterLast('/')?:"drop-file"
 }catch(_:Exception){uri.lastPathSegment?.substringAfterLast('/')?:"drop-file"}
 private fun sendUri(uri:Uri){try{
  val bytes=contentResolver.openInputStream(uri)?.use{it.readBytes()}?:return
  if(bytes.isEmpty())return
  val name=displayName(uri).takeLast(180)
  RemoteClient.post("/upload/"+URLEncoder.encode(name,"UTF-8"),bytes)
 }catch(_:Exception){}}
 override fun onDestroy(){target?.let{runCatching{wm?.removeView(it)}};super.onDestroy()}
}
