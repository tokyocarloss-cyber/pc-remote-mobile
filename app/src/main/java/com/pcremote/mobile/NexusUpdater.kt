package com.pcremote.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Update is initiated only by the person on the device.
 * An Android OS-confirmed install must succeed BEFORE the Windows update is requested.
 */
internal object NexusUpdateProtocol {
    private const val PREF = "nexus_pending_update"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    fun currentCode(ctx: Context): Long {
        @Suppress("DEPRECATION")
        val installed = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return code(installed)
    }

    @Suppress("DEPRECATION")
    fun checkArchive(ctx: Context, apk: File): Long {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                    else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IllegalArgumentException("APK inválido ou ilegível")
        if (candidate.packageName != ctx.packageName)
            throw IllegalArgumentException("Este APK não pertence ao NEXUS PC Remote")
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        if (Build.VERSION.SDK_INT >= 28) {
            val a = candidate.signingInfo?.apkContentsSigners.orEmpty()
            val b = installed.signingInfo?.apkContentsSigners.orEmpty()
            if (a.isEmpty() || b.isEmpty() || a.size != b.size ||
                !a.all { x -> b.any { y -> x == y } })
                throw IllegalArgumentException(
                    "Assinatura diferente: o Android não permite atualizar esta instalação. " +
                    "É preciso usar a mesma chave de assinatura em todos os builds."
                )
        } else {
            val a = candidate.signatures.orEmpty()
            val b = installed.signatures.orEmpty()
            if (a.isEmpty() || b.isEmpty() || a.size != b.size || !a.all { x -> b.any { y -> x == y } })
                throw IllegalArgumentException("APK assinado com chave diferente da versão instalada")
        }
        val newCode = code(candidate)
        if (newCode <= code(installed))
            throw IllegalArgumentException("O APK selecionado não é mais novo que o instalado")
        return newCode
    }

    fun sha256(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer=ByteArray(65536)
            while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun rememberPending(ctx: Context, host: String, manifest: JSONObject, version: Long) {
        val p=prefs(ctx)
        val android=manifest.getJSONObject("android")
        val windows=manifest.getJSONObject("windows")
        check(p.edit()
            .putString("host",host)
            .putString("nonce",manifest.getString("nonce"))
            .putString("android",android.getString("sha256"))
            .putString("windows",windows.getString("sha256"))
            .putLong("version",version).commit())
    }

    /** Called by the system after the new signed APK replaces the installed app. */
    fun confirmInstalledWithPc(ctx: Context): Boolean {
        val p=prefs(ctx)
        val host=p.getString("host","").orEmpty()
        val nonce=p.getString("nonce","").orEmpty()
        val android=p.getString("android","").orEmpty()
        val windows=p.getString("windows","").orEmpty()
        val expected=p.getLong("version",0)
        if(host.isBlank() || nonce.isBlank() || android.length!=64 || windows.length!=64 ||
            expected<=0 || currentCode(ctx)!=expected) return false

        val body=JSONObject().put("nonce",nonce)
            .put("android_sha256",android).put("windows_sha256",windows)
            .toString().toByteArray(Charsets.UTF_8)
        var conn: java.net.HttpURLConnection?=null
        return try{
            SecureConnection.init(ctx.applicationContext)
            conn=SecureConnection.open("/update/apply",host)
            conn.requestMethod="POST"
            conn.connectTimeout=1200
            conn.readTimeout=2200
            conn.doOutput=true
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val ok=conn.responseCode in 200..299
            if(ok) prefs(ctx).edit().clear().apply()
            ok
        }catch (_:Exception){false}
        finally{conn?.disconnect()}
    }
}

/** System-only broadcast: no update request is accepted from another application. */
class NexusUpdateInstalledReceiver: BroadcastReceiver(){
    override fun onReceive(context: Context, intent: Intent?){
        if(intent?.action!=Intent.ACTION_MY_PACKAGE_REPLACED)return
        val pending=goAsync()
        thread(name="NexusConfirmUpdate",isDaemon=true){
            try { NexusUpdateProtocol.confirmInstalledWithPc(context.applicationContext) }
            finally { pending.finish() }
        }
    }
}

@Composable
fun NexusUpdater() {
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var manifest by remember { mutableStateOf<JSONObject?>(null) }
    var status by remember { mutableStateOf("Escolha um APK e um EXE no painel do Windows.") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    // If the Android upgrade succeeded while the computer was temporarily offline,
    // allow a later retry without ever applying Windows before the APK is installed.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { NexusUpdateProtocol.confirmInstalledWithPc(ctx) }
    }

    fun fetchManifest() {
        scope.launch {
            if(!Api.connected){status="Conecte primeiro ao PC pareado";return@launch}
            busy=true
            try{
                val response=withContext(Dispatchers.IO){ RemoteClient.get("/update/manifest") }
                val parsed=JSONObject(String(response?:throw IllegalStateException("PC sem resposta"),Charsets.UTF_8))
                manifest=if(parsed.optBoolean("ready"))parsed else null
                status=if(manifest!=null)"Pacote preparado no Windows. Pronto para baixar por TLS."
                       else parsed.optString("detail","Prepare o pacote no Windows.")
            }catch(e:Exception){
                manifest=null;status="Erro ao consultar atualização: ${e.message}"
            }finally{busy=false}
        }
    }

    LaunchedEffect(Api.host) { if(Api.connected)fetchManifest() }

    fun updateBoth() {
        val data=manifest?:return
        scope.launch {
            if(!Api.connected){status="Conecte ao PC";return@launch}
            if(Build.VERSION.SDK_INT>=26 && !ctx.packageManager.canRequestPackageInstalls()){
                status="Autorize a instalação pelo NEXUS nas configurações do Android e volte aqui."
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+ctx.packageName)))
                return@launch
            }
            busy=true;progress=0f
            val temp=File(ctx.cacheDir,"updates/nexus-update.apk")
            try{
                val apkManifest=data.getJSONObject("android")
                val expectedSize=apkManifest.getLong("size")
                val expectedSha=apkManifest.getString("sha256")
                if(expectedSize !in 1024L..(250L*1024*1024) ||
                    !expectedSha.matches(Regex("[a-f0-9]{64}")))
                    throw IllegalArgumentException("Metadados de atualização inválidos")
                temp.parentFile?.mkdirs()
                status="Baixando novo APK pela conexão segura…"
                val ok=withContext(Dispatchers.IO){
                    temp.outputStream().use{output->
                        RemoteClient.download("/update/android.apk",output,
                            progress={done,total->
                                progress=(done.toFloat()/expectedSize).coerceIn(0f,1f)
                            })
                    }
                }
                if(!ok || temp.length()!=expectedSize)
                    throw IllegalStateException("Download incompleto ou interrompido")
                status="Verificando integridade e assinatura…"
                val digest=withContext(Dispatchers.IO){ NexusUpdateProtocol.sha256(temp) }
                if(!MessageDigest.isEqual(digest.toByteArray(),expectedSha.toByteArray()))
                    throw IllegalArgumentException("O hash do APK não confere. Instalação bloqueada.")
                val version=withContext(Dispatchers.IO){ NexusUpdateProtocol.checkArchive(ctx,temp) }
                NexusUpdateProtocol.rememberPending(ctx,Api.host,data,version)
                val uri=FileProvider.getUriForFile(ctx,ctx.packageName+".updates",temp)
                val intent=Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri,"application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                status="Confirme no Android. O Windows atualizará após a instalação terminar."
                ctx.startActivity(intent)
            }catch(e:Exception){
                status="Atualização não iniciada: ${e.message}"
            }finally{busy=false}
        }
    }

    Surface(color=NexusUi.Panel,shape=RoundedCornerShape(22.dp),
        border=BorderStroke(1.dp,NexusUi.Border),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row {
                Icon(Icons.Default.SystemUpdate,"Atualizar",tint=NexusUi.Accent)
                Spacer(Modifier.width(8.dp))
                Text("ATUALIZAR ANDROID + WINDOWS",color=NexusUi.Text,fontWeight=FontWeight.Black)
            }
            Text("1. No programa Windows, clique em Preparar atualização dos dois e escolha os novos APK e EXE.\n" +
                 "2. Aqui, baixe pelo Wi-Fi e confirme a instalação do Android.\n" +
                 "3. Depois que o Android atualizar, o PC troca o EXE e reinicia sozinho.",
                color=NexusUi.Muted,style=MaterialTheme.typography.bodySmall)
            Text("Versão instalada: código ${NexusUpdateProtocol.currentCode(ctx)}",
                 color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
            if(manifest!=null){
                val a=manifest!!.getJSONObject("android")
                val w=manifest!!.getJSONObject("windows")
                Text("Pacote no PC • Android ${a.getLong("size")/1048576} MB • Windows ${w.getLong("size")/1048576} MB",
                    color=NexusUi.Accent,style=MaterialTheme.typography.labelSmall)
                Text("Hash APK: ${a.getString("sha256").take(16)}…",
                    color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
            }
            Button(onClick=::fetchManifest,enabled=!busy&&Api.connected,
                modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(6.dp));Text("VERIFICAR PACOTE")
            }
            Button(onClick=::updateBoth,enabled=!busy&&Api.connected&&manifest!=null,
                modifier=Modifier.fillMaxWidth(),
                colors=ButtonDefaults.buttonColors(containerColor=NexusUi.AccentStrong)){
                Icon(Icons.Default.SystemUpdateAlt,null);Spacer(Modifier.width(6.dp))
                Text("ATUALIZAR OS DOIS")
            }
            if(busy)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
            Text(status,color=NexusUi.Muted,style=MaterialTheme.typography.bodySmall)
            Text("Não funciona com APK assinado por outra chave. O Android exige confirmação; nada é instalado silenciosamente.",
                color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
        }
    }
}
