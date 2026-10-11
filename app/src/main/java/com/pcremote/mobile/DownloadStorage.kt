package com.pcremote.mobile

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

object DownloadStorage {
    fun save(ctx: Context, name: String, mime: String, path: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) {
            val dir = java.io.File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "NEXUS").apply { mkdirs() }
            val file = java.io.File(dir, "${System.currentTimeMillis()}_${java.io.File(name).name}")
            val ok = runCatching { file.outputStream().use { RemoteClient.download(path,it) } }.getOrDefault(false)
            if (!ok) file.delete()
            return ok
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,java.io.File(name).name)
            put(MediaStore.MediaColumns.MIME_TYPE,mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/NEXUS")
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=ctx.contentResolver
        val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: return false
        var ok=false
        try {
            ok=resolver.openOutputStream(uri)?.use { RemoteClient.download(path,it) } ?: false
            if(ok) resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            return ok
        } finally { if(!ok) resolver.delete(uri,null,null) }
    }
}
