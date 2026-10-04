package com.pcremote.mobile

import android.app.*
import android.content.Intent
import android.os.IBinder
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

class PhoneInboxService: Service(){
 @Volatile private var running=true
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();startForegroundMode();thread(start=true,name="PhoneInbox"){serve()}}
 private fun startForegroundMode(){
  val id="phone_inbox";val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel(id,"PC Drop Inbox",NotificationManager.IMPORTANCE_LOW))
  startForeground(42,Notification.Builder(this,id).setContentTitle("PC Drop conectado").setContentText("Pronto para receber arquivos do Windows").setSmallIcon(android.R.drawable.stat_sys_download).build())
 }
 private fun serve(){try{ServerSocket(8766).use{server->while(running){server.accept().use{socket->
  val input=socket.getInputStream();val reader=input.bufferedReader();val first=reader.readLine()?:return@use
  var len=0;while(true){val line=reader.readLine();if(line.isNullOrBlank())break;if(line.startsWith("Content-Length:",true))len=line.substringAfter(":").trim().toIntOrNull()?:0}
  if(first.startsWith("POST /inbox/")){val raw=first.substringAfter("/inbox/").substringBefore(" HTTP");val fileName=java.net.URLDecoder.decode(raw,"UTF-8").replace(Regex("[\\/:*?\"<>|]"),"_")
   val bytes=ByteArray(len);var pos=0;while(pos<len){val n=input.read(bytes,pos,len-pos);if(n<=0)break;pos+=n}
   val dir=getExternalFilesDir("PC Remote")?:filesDir;dir.mkdirs();File(dir,fileName).writeBytes(bytes.copyOf(pos))
   val reply="OK";socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+reply.length+"\r\nConnection: close\r\n\r\n"+reply).toByteArray());notifyReceived(fileName)
  }
 }}}}catch(_:Exception){}}
 private fun notifyReceived(fileName:String){getSystemService(NotificationManager::class.java).notify(43,Notification.Builder(this,"phone_inbox").setContentTitle("Recebido do PC").setContentText(fileName).setSmallIcon(android.R.drawable.stat_sys_download_done).build())}
 override fun onDestroy(){running=false;super.onDestroy()}
}
