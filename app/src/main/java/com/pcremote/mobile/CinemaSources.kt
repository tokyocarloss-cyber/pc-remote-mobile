package com.pcremote.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.URLEncoder

internal data class CinemaDirectory(
    val id: String, val title: String, val description: String,
    val official: Boolean, val searchable: Boolean
)

internal object CinemaDirectories {
    val all = listOf(
        CinemaDirectory("justwatch","JustWatch Brasil",
            "Onde assistir: streaming, aluguel, compra e opções gratuitas.",true,false),
        CinemaDirectory("pluto","Pluto TV",
            "Streaming gratuito de filmes e séries com anúncios.",true,false),
        CinemaDirectory("apple","Apple TV",
            "Loja e streaming oficial de filmes e séries.",true,false),
        CinemaDirectory("tvmaze","TVmaze",
            "Informações, temporadas e sinopses de séries.",true,false),
        CinemaDirectory("archive","Internet Archive",
            "Acervo público; confira os direitos de cada arquivo.",true,true),
        CinemaDirectory("megathread","Megathread Pirataria",
            "Diretório comunitário PT-BR. Sites externos não são verificados pelo NEXUS.",false,false),
        CinemaDirectory("torrentdosfilmes","Torrent dos Filmes",
            "Há vários domínios usando este nome. Abre pesquisa na web; domínio oficial não confirmado.",false,true),
        CinemaDirectory("btdig","BTDig",
            "Buscador DHT de torrents; idioma e licença dos arquivos não são verificados.",false,true),
        CinemaDirectory("ext","EXT Torrents",
            "Índice de torrents pesquisável por título, idioma e resolução.",false,true),
        CinemaDirectory("hattered","Hattered",
            "Projeto comunitário de diretórios, ferramentas e guias.",false,false)
    )

    // Keep IDs and search URL templates in sync with windows/entertainment_hub.py.
    fun url(id: String, text: String): String {
        val q = URLEncoder.encode(text.trim().take(120), "UTF-8")
        return when (id) {
            "justwatch" -> "https://br.justwatch.com/br"
            "pluto" -> "https://pluto.tv/br/on-demand"
            "apple" -> "https://tv.apple.com/br"
            "tvmaze" -> "https://www.tvmaze.com"
            "archive" -> if (q.isEmpty()) "https://archive.org/details/movies" else "https://archive.org/search?query=$q"
            "megathread" -> "https://pirataria.link/"
            "torrentdosfilmes" -> "https://www.google.com/search?q=" +
                URLEncoder.encode("Torrent dos Filmes " + text.trim().take(90), "UTF-8")
            "btdig" -> if (q.isEmpty()) "https://btdig.com" else "https://btdig.com/search?q=$q&p=0"
            "ext" -> if (q.isEmpty()) "https://ext.to" else "https://ext.to/browse/?q=$q"
            "hattered" -> "https://mefrraz.github.io/hattered/index.html"
            else -> throw IllegalArgumentException("Fonte não cadastrada")
        }
    }
}

@Composable
internal fun CinemaSources(
    query: String, changeQuery: (String) -> Unit,
    open: (String) -> Unit,
    queue: (CinemaDirectory, String) -> Unit,
    revision: Int, refreshSaved: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var group by remember { mutableStateOf("all") }
    var audio by remember { mutableStateOf("any") }
    var quality by remember { mutableStateOf("any") }
    fun phrase(s: CinemaDirectory): String = buildString {
        append(query.trim())
        if (!s.official && s.searchable && query.isNotBlank()) {
            if (audio == "dub") append(" dublado PT-BR")
            if (audio == "dual") append(" dual áudio português")
            if (quality != "any") append(" $quality")
        }
    }
    val providers = CinemaDirectories.all.filter {
        group == "all" || (group == "official") == it.official
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fontes oficiais e da comunidade", color = NexusUi.Text,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text("Acesse os sites pelo celular, sem PC. Links de terceiros são externos.",
            color = NexusUi.Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, onValueChange = changeQuery,
            label = { Text("Nome do filme ou da série") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "Todas", "official" to "Oficiais",
                "community" to "Comunidade / torrents").forEach { (id, label) ->
                FilterChip(selected = group == id, onClick = { group = id },
                    label = { Text(label) })
            }
        }
        if (group != "official") {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("any" to "Áudio livre", "dub" to "PT-BR dublado",
                    "dual" to "Dual áudio").forEach { (id, label) ->
                    FilterChip(selected = audio == id, onClick = { audio = id },
                        label = { Text(label) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("any" to "Qualidade livre", "720p" to "720p",
                    "1080p" to "1080p", "2160p" to "4K").forEach { (id, label) ->
                    FilterChip(selected = quality == id, onClick = { quality = id },
                        label = { Text(label) })
                }
            }
            Text("Os filtros adicionam termos à busca; não garantem idioma ou qualidade.",
                color = NexusUi.Muted, style = MaterialTheme.typography.labelSmall)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(providers, key = { it.id }) { source ->
                val item = HubItem(source.id, source.title, source.description,
                    url = CinemaDirectories.url(source.id, ""), kind = "cinema-source")
                val saved = remember(revision, source.id) { HubStore.isFavorite(ctx, item) }
                Surface(color = NexusUi.Panel, shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, NexusUi.Border)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (source.official) Icons.Default.Verified
                                else Icons.Default.Language, null, tint = NexusUi.Accent)
                            Spacer(Modifier.width(7.dp))
                            Text(source.title, color = NexusUi.Text, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                HubStore.toggleFavorite(ctx, item)
                                refreshSaved()
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favoritar fonte", tint = NexusUi.Accent)
                            }
                        }
                        Text(source.description, color = NexusUi.Muted,
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { open(CinemaDirectories.url(source.id, phrase(source))) },
                                modifier = Modifier.weight(1f)) {
                                Text(if (source.searchable && query.isNotBlank()) "Pesquisar"
                                     else "Abrir no celular")
                            }
                            OutlinedButton(onClick = { queue(source, phrase(source)) },
                                modifier = Modifier.weight(1f)) {
                                Text("No PC depois")
                            }
                        }
                    }
                }
            }
        }
    }
}
