package com.pcremote.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Streams text edits in order instead of waiting for a SEND action.
 * Ordinary typing, IME commits, deletions and paste are supported.
 * Edits in the middle of this staging field are not a replacement for
 * full remote cursor/selection synchronisation.
 */
@Composable
fun TextRemote() {
    var buffer by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Digite no campo para escrever diretamente no Windows") }
    val queue = remember { Channel<String>(Channel.UNLIMITED) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Api.host) {
        for (payload in queue) {
            val ok = kotlinx.coroutines.withContext(Dispatchers.IO) {
                RemoteClient.post("/type-live", payload.toByteArray(Charsets.UTF_8))
            }
            if (!ok) status = "Não foi possível digitar no PC. Verifique a conexão."
        }
    }
    fun submitEdit(newValue: String) {
        val old = buffer
        if (newValue == old) return
        val prefix = old.zip(newValue).takeWhile { (a,b) -> a == b }.size
        // Android keyboard autocorrect normally replaces the suffix.
        val removed = old.length - prefix
        val added = newValue.substring(prefix)
        if (removed > 0) queue.trySend(JSONObject().put("op","backspace").put("count",removed.coerceAtMost(1024)).toString())
        if (added.isNotEmpty()) queue.trySend(JSONObject().put("op","insert").put("text",added).toString())
        buffer = newValue
        status = "Digitação em tempo real • o foco deve estar no Windows"
    }
    fun key(name:String) { scope.launch(Dispatchers.IO) { Api.post("/key",name) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Title("Teclado ao vivo","Escreva diretamente no aplicativo aberto no seu PC")
        Surface(
            color=NexusUi.Panel,
            shape=RoundedCornerShape(20.dp),
            border=BorderStroke(1.dp,NexusUi.Border),
            modifier=Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("MODO DIRETO",color=NexusUi.Accent,fontWeight=FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text("Clique no campo de texto desejado no Windows, depois digite aqui. Não é necessário enviar.",
                    color=NexusUi.Muted,style=MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value=buffer, onValueChange=::submitEdit,
                    modifier=Modifier.fillMaxWidth(),
                    placeholder={Text("Comece a digitar…")},
                    minLines=3,maxLines=6,
                    shape=RoundedCornerShape(16.dp)
                )
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    TextButton(onClick={buffer="";status="Campo local limpo. O texto no PC não foi apagado."}){
                        Text("LIMPAR CAMPO",color=NexusUi.Accent)
                    }
                }
            }
        }
        Text("ATALHOS",color=NexusUi.Muted,style=MaterialTheme.typography.labelMedium)
        listOf(
            listOf("ENTER","BACKSPACE","TAB"),
            listOf("ESC","SPACE","DELETE"),
            listOf("LEFT","DOWN","UP","RIGHT")
        ).forEach { line ->
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp), modifier=Modifier.fillMaxWidth()) {
                line.forEach { label ->
                    OutlinedButton(
                        onClick={key(label)},
                        modifier=Modifier.weight(1f).height(44.dp),
                        contentPadding=PaddingValues(horizontal=2.dp),
                        shape=RoundedCornerShape(12.dp)
                    ) {Text(label,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold)}
                }
            }
        }
        Text(status,color=NexusUi.Muted,style=MaterialTheme.typography.bodySmall)
        Text("Caracteres Unicode são enviados sem sobrescrever a área de transferência. Requer o companion Windows atualizado.",
            color=NexusUi.Muted,style=MaterialTheme.typography.labelSmall)
    }
}
