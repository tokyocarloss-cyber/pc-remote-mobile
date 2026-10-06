package com.pcremote.mobile

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import org.json.JSONArray
import java.net.URLEncoder
import kotlin.concurrent.thread

class PhoneInboxService:Service(){
 @Volatile private var running=true
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();foreground();thread(start=true,name="PhoneInbox"){poll()}}
 private fun foreground(){
  val id="phone_inbox"
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel(id,"NEXUS Inbox",NotificationManager.IMPORTANCE_LOW))
  startForeground(42,Notification.Builder(this,id).setContentTitle("NEXUS conectado ao PC").setContentText("Recebendo arquivos enviados pelo computador").setSmallIcon(android.R.drawable.stat_sys_download).build())
 }
 private fun poll(){while(running){
  try{
   val raw=RemoteClient.get("/outbox")?.toString(Charsets.UTF_8)
   if(raw!=null){
    val a=JSONArray(raw)
    for(i in 0 until a.length()){
     val name=a.getJSONObject(i).getString("name")
     receive(name)
    }
   }
  }catch(_:Exception){}
  try{Thread.sleep(1200)}catch(_:InterruptedException){break}
 }}

 private fun mime(name:String):String{
  val ext=name.substringAfterLast('.', "").lowercase()
  return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when(ext){
   "mp4"->"video/mp4";"mkv"->"video/x-matroska";"webm"->"video/webm"
   "jpg","jpeg"->"image/jpeg";"png"->"image/png";"webp"->"image/webp"
   else->"application/octet-stream"
  }
 }

 private fun receive(name:String){
  val encoded=URLEncoder.encode(name,"UTF-8").replace("+","%20")
  val data=RemoteClient.get("/outbox/$encoded")?:return
  val type=mime(name)
  val (collection,relative)=when{
   type.startsWith("image/")->MediaStore.Images.Media.EXTERNAL_CONTENT_URI to (Environment.DIRECTORY_PICTURES+"/NEXUS")
   type.startsWith("video/")->MediaStore.Video.Media.EXTERNAL_CONTENT_URI to (Environment.DIRECTORY_MOVIES+"/NEXUS")
   else->MediaStore.Downloads.EXTERNAL_CONTENT_URI to (Environment.DIRECTORY_DOWNLOADS+"/NEXUS")
  }
  val values=ContentValues().apply{
   put(MediaStore.MediaColumns.DISPLAY_NAME,name)
   put(MediaStore.MediaColumns.MIME_TYPE,type)
   put(MediaStore.MediaColumns.RELATIVE_PATH,relative)
  }
  val uri=contentResolver.insert(collection,values)?:return
  try{
   contentResolver.openOutputStream(uri)?.use{it.write(data)}?:return
   RemoteClient.post("/outbox-ack/$encoded",ByteArray(0))
   notifyReceived(name,relative)
  }catch(_:Exception){contentResolver.delete(uri,null,null)}
 }

 private fun notifyReceived(name:String,relative:String){
  getSystemService(NotificationManager::class.java).notify(
   name.hashCode(),
   Notification.Builder(this,"phone_inbox")
    .setContentTitle("Recebido do PC")
    .setContentText("$name • $relative")
    .setSmallIcon(android.R.drawable.stat_sys_download_done)
    .build()
  )
 }
 override fun onDestroy(){running=false;super.onDestroy()}
}
