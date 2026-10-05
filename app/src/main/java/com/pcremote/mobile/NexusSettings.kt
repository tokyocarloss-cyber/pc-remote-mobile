package com.pcremote.mobile

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val DefaultStores = listOf("Alll", "Steam", "Epic", "Xbox", "EA", "Ubisoft", "Battle.net", "GOG", "Outros")

fun loadStoreOrder(ctx: Context): List<String> {
    val p = ctx.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)
    val saved = p.getString("store_order", null)?.split('|')?.filter { it.isNotBlank() }.orEmpty()
    return (saved + DefaultStores).distinct().filter { it in DefaultStores }
}

fun isStoreEnabled(ctx: Context, store: String) =
    ctx.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE).getBoolean("store_$store", true)

private fun themeFlavor(name: String): String = when (name) {
    "Attack on Titan" -> "militar • cortes secos • metal"
    "Frutiger Aero" -> "vidro • bolhas • movimento leve"
    "Dark Souls" -> "cinzas • âmbar • transições lentas"
    "Anime Prism" -> "prisma • linhas rápidas • brilho"
    "Naruto" -> "chakra • energia • pulsos"
    "Cyber Samurai" -> "cortes neon • ritmo rápido"
    "Sakura Night" -> "pétalas • brilho suave • fluido"
    "Retro CRT" -> "scanlines • cantos retos • bipes"
    "Arctic Glass" -> "gelo • vidro • partículas"
    else -> "grade neon • resposta rápida • sci-fi"
}

@Composable
fun NexusSettings() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var stores by remember { mutableStateOf(loadStoreOrder(ctx)) }
    var manualIp by remember { mutableStateOf(prefs.getString("manual_ip", Api.host).orEmpty()) }
    var manualResult by remember { mutableStateOf("") }
    var themeName by remember { mutableStateOf(prefs.getString("theme", "NEXUS Neon") ?: "NEXUS Neon") }
    var themeSounds by remember { mutableStateOf(prefs.getBoolean("theme_sounds", true)) }

    LaunchedEffect(Unit) { NexusUi.applyTheme(themeName) }

    fun persistStores() { prefs.edit().putString("store_order", stores.joinToString("|")).apply() }
    fun selectTheme(name: String) {
        themeName = name
        prefs.edit().putString("theme", name).apply()
        NexusUi.applyTheme(name)
        NexusEffects.play(ctx, NexusEffect.SELECT)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Title("Configurações", "Conexão, biblioteca, visual e preferências do NEXUS")

        Surface(
            color = NexusUi.Panel.copy(alpha = .94f),
            shape = RoundedCornerShape(NexusUi.cardRadius.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, null, tint = if (Api.connected) NexusUi.Success else NexusUi.Accent)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CONEXÃO COM O PC", color = NexusUi.Text, fontWeight = FontWeight.Black)
                        Text(
                            if (Api.connected) "Pareado em ${Api.host}:8765 • ${Api.connectionMethod}" else Discovery.detail,
                            color = NexusUi.Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text("IP do PC") },
                    placeholder = { Text("Ex.: 192.168.18.45") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val value = manualIp.trim()
                            prefs.edit().putString("manual_ip", value).apply()
                            manualResult = "Testando $value:8765…"
                            scope.launch {
                                val ok = connectToHost(value, "IP manual")
                                manualResult = if (ok) "Conectado com sucesso" else "Sem resposta na porta 8765. Verifique o NEXUS no PC e o Firewall."
                            }
                        },
                        enabled = manualIp.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("CONECTAR") }

                    OutlinedButton(
                        onClick = {
                            Api.connected = false
                            Discovery.detail = "Nova busca solicitada…"
                            manualResult = "Busca automática reiniciada"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("BUSCAR")
                    }
                }

                if (manualResult.isNotBlank()) {
                    Text(manualResult, color = if (Api.connected) NexusUi.Success else NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
                }

                Text(
                    "Celular: ${Discovery.networkSummary()}\nMétodo atual: ${Api.connectionMethod}\nBusca: ${Discovery.lastMethod}\nPC no cabo + celular no Wi‑Fi funciona quando os dois estão no mesmo roteador/rede local.",
                    color = NexusUi.Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Surface(
            color = NexusUi.Panel.copy(alpha = .94f),
            shape = RoundedCornerShape(NexusUi.cardRadius.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null, tint = NexusUi.Accent)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("SKINS COMPLETAS", color = NexusUi.Text, fontWeight = FontWeight.Black)
                        Text("Mudam formas, fundo animado, ritmo das transições e sons", color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Surface(color = NexusUi.BackgroundSoft, shape = RoundedCornerShape(NexusUi.cardRadius.dp), border = BorderStroke(1.dp, NexusUi.Border)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, null, tint = NexusUi.Accent)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sons do tema", color = NexusUi.Text, fontWeight = FontWeight.SemiBold)
                            Text("Cada skin usa uma resposta sonora própria", color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = themeSounds, onCheckedChange = {
                            themeSounds = it
                            prefs.edit().putBoolean("theme_sounds", it).apply()
                            if (it) NexusEffects.play(ctx, NexusEffect.SELECT)
                        })
                    }
                }

                NexusThemes.forEach { theme ->
                    val selected = themeName == theme.name
                    Surface(
                        color = if (selected) theme.panelRaised else NexusUi.BackgroundSoft,
                        shape = RoundedCornerShape(
                            when (theme.name) {
                                "Attack on Titan" -> 9.dp
                                "Dark Souls" -> 7.dp
                                "Retro CRT" -> 4.dp
                                "Frutiger Aero", "Sakura Night", "Arctic Glass" -> 24.dp
                                else -> 16.dp
                            }
                        ),
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) theme.accent else NexusUi.Border),
                        modifier = Modifier.fillMaxWidth().height(76.dp).clickable { selectTheme(theme.name) }
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(theme.accent, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, null, tint = theme.background, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(theme.name, color = NexusUi.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(themeFlavor(theme.name), color = if (selected) theme.accent else NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            if (selected) Icon(Icons.Default.CheckCircle, null, tint = theme.accent)
                        }
                    }
                }
            }
        }

        Surface(
            color = NexusUi.Panel.copy(alpha = .94f),
            shape = RoundedCornerShape(NexusUi.cardRadius.dp),
            border = BorderStroke(1.dp, NexusUi.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("FONTES DA BIBLIOTECA", color = NexusUi.Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text("'Alll' representa Desktop\\Alll no PC e serve somente como fonte extra da Biblioteca. Arquivos recebidos continuam indo para Downloads.", color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        stores.forEachIndexed { index, store ->
            var enabled by remember(store) { mutableStateOf(prefs.getBoolean("store_$store", true)) }
            Surface(
                color = NexusUi.Panel.copy(alpha = .94f),
                shape = RoundedCornerShape(NexusUi.cardRadius.dp),
                border = BorderStroke(1.dp, NexusUi.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).background(NexusUi.Accent.copy(alpha = .10f), RoundedCornerShape(NexusUi.cardRadius.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(store.take(1), color = NexusUi.Accent, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(10.dp))
                    Text(store, Modifier.weight(1f), color = NexusUi.Text, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = {
                        if(index>0){stores=stores.toMutableList().also{java.util.Collections.swap(it,index,index-1)};persistStores()}
                    },enabled=index>0,modifier=Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp,null,tint=if(index>0)NexusUi.Text else NexusUi.Border)
                    }
                    IconButton(onClick = {
                        if(index<stores.lastIndex){stores=stores.toMutableList().also{java.util.Collections.swap(it,index,index+1)};persistStores()}
                    },enabled=index<stores.lastIndex,modifier=Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown,null,tint=if(index<stores.lastIndex)NexusUi.Text else NexusUi.Border)
                    }
                    Switch(checked=enabled,onCheckedChange={enabled=it;prefs.edit().putBoolean("store_$store",it).apply()})
                }
            }
        }

        Surface(color=NexusUi.Panel.copy(alpha=.94f),shape=RoundedCornerShape(NexusUi.cardRadius.dp),border=BorderStroke(1.dp,NexusUi.Border),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("SEGURANÇA DO ANDROID",color=NexusUi.Text,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("O NEXUS não tenta desativar o Play Protect. As permissões especiais ficam limitadas ao Drop Edge e só são usadas quando você ativa essa função.",color=NexusUi.Muted,style=MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
