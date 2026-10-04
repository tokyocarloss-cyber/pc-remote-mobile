package com.pcremote.mobile

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import org.json.JSONArray
import java.net.URLEncoder
import kotlin.concurrent.thread

class PhoneInboxService:Service(){
 @Volatile private var running=true
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();foreground();thread(start=true,name="PhoneInbox"){poll()}}
 private fun foreground(){val id="phone_inbox";val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel(id,"PC Drop Inbox",NotificationManager.IMPORTANCE_LOW));startForeground(42,Notification.Builder(this,id).setContentTitle("PC Drop conectado").setContentText("Sincronizando arquivos enviados pelo PC").setSmallIcon(android.R.drawable.stat_sys_download).build())}
 private fun poll(){while(running){try{val raw=RemoteClient.get("/outbox")?.toString(Charsets.UTF_8);if(raw!=null){val a=JSONArray(raw);for(i in 0 until a.length()){val name=a.getJSONObject(i).getString("name");receive(name)}}}catch(_:Exception){};try{Thread.sleep(1500)}catch(_:InterruptedException){break}}}
 private fun receive(name:String){val encoded=URLEncoder.encode(name,"UTF-8").replace("+","%20");val data=RemoteClient.get("/outbox/$encoded")?:return
  val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/PC Remote")}
  val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:return
  try{contentResolver.openOutputStream(uri)?.use{it.write(data)}?:return;RemoteClient.post("/outbox-ack/$encoded",ByteArray(0));notifyReceived(name)}catch(_:Exception){contentResolver.delete(uri,null,null)}
 }
 private fun notifyReceived(name:String){getSystemService(NotificationManager::class.java).notify(name.hashCode(),Notification.Builder(this,"phone_inbox").setContentTitle("Recebido do PC").setContentText(name+" • Downloads/PC Remote").setSmallIcon(android.R.drawable.stat_sys_download_done).build())}
 override fun onDestroy(){running=false;super.onDestroy()}
}
