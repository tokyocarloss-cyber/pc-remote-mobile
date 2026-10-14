package com.pcremote.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Marketplace-style poster-first results, without invented source availability. */
@Composable
internal fun EntertainmentResultCard(
    item: HubItem, isFavorite: Boolean, games: Boolean,
    onFavorite: () -> Unit, onOpen: () -> Unit,
    onAction: () -> Unit, onSources: () -> Unit
) {
    val media = !games && item.kind in setOf("movie", "series")
    Surface(color = NexusUi.Panel.copy(alpha = 0.97f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NexusUi.Border),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top) {
                val picture = Modifier.width(122.dp).height(176.dp)
                    .clip(RoundedCornerShape(10.dp))
                if (item.image.startsWith("https://")) {
                    AsyncImage(model = item.image,
                        contentDescription = "Capa: ${item.title}",
                        modifier = picture, contentScale = ContentScale.Crop)
                } else {
                    Box(picture.background(NexusUi.PanelRaised),
                        contentAlignment = Alignment.Center) {
                        Icon(if (games) Icons.Default.SportsEsports else Icons.Default.Movie,
                            contentDescription = null, tint = NexusUi.Accent,
                            modifier = Modifier.size(42.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(when (item.kind) {
                            "steamrip" -> "SteamRIP"
                            "game" -> "Steam"
                            "movie" -> "Filme"
                            "series" -> "Série"
                            else -> "No meu PC"
                        }, color = NexusUi.Accent,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) {
                            Icon(if (isFavorite) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = NexusUi.Accent, modifier = Modifier.size(22.dp))
                        }
                    }
                    Text(item.title, color = NexusUi.Text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) Text(
                        item.subtitle, color = NexusUi.Muted,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2)
                    if (item.description.isNotBlank()) Text(
                        item.description, color = NexusUi.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (media) {
                OutlinedButton(onClick = onSources, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Layers, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Pesquisar em todas as fontes")
                }
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction, modifier = Modifier.weight(1f)) {
                    Icon(if (media) Icons.Default.Search else Icons.Default.Download,
                        null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(when (item.kind) {
                        "game" -> "Instalar no PC"
                        "steamrip" -> "Ver downloads"
                        "movie", "series" -> "Encontrar arquivo"
                        "installed-game" -> "Jogar no PC"
                        "local-video" -> "Reproduzir"
                        else -> "Abrir"
                    }, maxLines = 1)
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Ver no NEXUS", maxLines = 1)
                }
            }
        }
    }
}
