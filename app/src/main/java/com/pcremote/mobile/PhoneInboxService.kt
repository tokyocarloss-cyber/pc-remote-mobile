package com.pcremote.mobile

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.concurrent.thread

class PhoneInboxService:Service(){
 @Volatile private var running=true
 private var fileServer:ServerSocket?=null
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){
  super.onCreate()
  foreground()
  thread(start=true,name="PhoneInbox"){poll()}
  thread(start=true,name="PhoneFiles"){servePhoneFiles()}
 }
 private fun foreground(){
  val id="phone_inbox"
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel(id,"NEXUS Inbox",NotificationManager.IMPORTANCE_LOW))
  startForeground(42,Notification.Builder(this,id).setContentTitle("NEXUS conectado ao PC").setContentText("Arquivos e NEXUS Flow ativos").setSmallIcon(android.R.drawable.stat_sys_download).build())
 }
 private fun poll(){while(running){
  try{
   val raw=RemoteClient.get("/outbox")?.toString(Charsets.UTF_8)
   if(raw!=null){
    val a=JSONArray(raw)
    for(i in 0 until a.length()){receive(a.getJSONObject(i).getString("name"))}
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

 private fun servePhoneFiles(){
  try{
   ServerSocket(8767).use{server->
    fileServer=server
    while(running){
     val socket=try{server.accept()}catch(_:Exception){break}
     thread(isDaemon=true,name="PhoneFileClient"){handleClient(socket)}
    }
   }
  }catch(_:Exception){}
 }

 private fun handleClient(socket:Socket){
  socket.use{s->
   try{
    val input=BufferedInputStream(s.getInputStream())
    val request=readHttpLine(input)?:return
    while(true){val line=readHttpLine(input)?:break;if(line.isBlank())break}
    val target=request.split(' ').getOrNull(1)?:"/"
    when{
     target.startsWith("/file?")->serveFile(s,target)
     target.startsWith("/browse?")->serveBrowse(s,target)
     else->serveRoot(s)
    }
   }catch(_:Exception){}
  }
 }

 private fun readHttpLine(input:BufferedInputStream):String?{
  val out=StringBuilder()
  while(true){
   val b=input.read();if(b<0)return if(out.isEmpty())null else out.toString()
   if(b==10)break
   if(b!=13)out.append(b.toChar())
  }
  return out.toString()
 }

 private fun selectedRoot():Uri?{
  val raw=getSharedPreferences("nexus_flow",MODE_PRIVATE).getString("phone_share_uri",null)?:return null
  return runCatching{Uri.parse(raw)}.getOrNull()
 }

 private fun serveRoot(s:Socket){
  val rootUri=selectedRoot()
  if(rootUri==null){
   return sendHtml(s,"<h2>NEXUS Phone Bridge</h2><p>Abra o NEXUS no celular → Flow → selecione uma pasta para compartilhar com o PC.</p>")
  }
  val root=DocumentFile.fromTreeUri(this,rootUri)
  if(root==null)return sendHtml(s,"<h2>NEXUS</h2><p>Pasta compartilhada indisponível.</p>")
  serveDocumentList(s,root,"Memória do celular")
 }

 private fun queryValue(target:String,key:String):String{
  val query=target.substringAfter('?',"")
  return query.split('&').firstOrNull{it.substringBefore('=')==key}?.substringAfter('=',"")?.let{URLDecoder.decode(it,"UTF-8")}.orEmpty()
 }

 private fun serveBrowse(s:Socket,target:String){
  val u=queryValue(target,"u")
  val doc=runCatching{DocumentFile.fromSingleUri(this,Uri.parse(u))}.getOrNull()
  if(doc==null||!doc.isDirectory)return sendHtml(s,"<p>Pasta indisponível.</p>")
  serveDocumentList(s,doc,doc.name?:"Pasta")
 }

 private fun serveDocumentList(s:Socket,doc:DocumentFile,title:String){
  val body=StringBuilder()
  body.append("<!doctype html><meta name=viewport content='width=device-width'><style>body{background:#071019;color:#eef7ff;font-family:Segoe UI;padding:24px}a{color:#67ecff;text-decoration:none}.row{padding:12px;border-bottom:1px solid #21303c}.muted{color:#8da0ad;font-size:12px}</style>")
  body.append("<h2>NEXUS • ").append(html(title)).append("</h2><p class=muted>Arquivos do celular acessíveis pelo PC na rede local.</p>")
  doc.listFiles().sortedWith(compareBy<DocumentFile>{!it.isDirectory}.thenBy{it.name?.lowercase()?:""}).take(300).forEach{f->
   val encoded=URLEncoder.encode(f.uri.toString(),"UTF-8")
   body.append("<div class=row>")
   if(f.isDirectory)body.append("📁 <a href='/browse?u=").append(encoded).append("'>").append(html(f.name?:"Pasta")).append("</a>")
   else body.append("📄 <a href='/file?u=").append(encoded).append("'>").append(html(f.name?:"Arquivo")).append("</a> <span class=muted>").append(f.length()).append(" bytes</span>")
   body.append("</div>")
  }
  sendHtml(s,body.toString())
 }

 private fun serveFile(s:Socket,target:String){
  val u=queryValue(target,"u")
  val doc=runCatching{DocumentFile.fromSingleUri(this,Uri.parse(u))}.getOrNull()
  if(doc==null||!doc.isFile)return sendStatus(s,404,"Not Found")
  val len=doc.length()
  val name=(doc.name?:"arquivo").replace("\"","")
  val type=doc.type?:mime(name)
  val out=BufferedOutputStream(s.getOutputStream())
  val header="HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: $len\r\nContent-Disposition: attachment; filename=\"$name\"\r\nConnection: close\r\n\r\n"
  out.write(header.toByteArray())
  contentResolver.openInputStream(doc.uri)?.use{it.copyTo(out)}
  out.flush()
 }

 private fun sendHtml(s:Socket,body:String){
  val bytes=body.toByteArray(Charsets.UTF_8)
  val out=BufferedOutputStream(s.getOutputStream())
  out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
  out.write(bytes);out.flush()
 }
 private fun sendStatus(s:Socket,code:Int,text:String){
  val b=text.toByteArray();val out=BufferedOutputStream(s.getOutputStream())
  out.write("HTTP/1.1 $code $text\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray());out.write(b);out.flush()
 }
 private fun html(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")

 override fun onDestroy(){running=false;runCatching{fileServer?.close()};super.onDestroy()}
}