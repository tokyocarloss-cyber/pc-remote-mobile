package com.pcremote.mobile

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

private fun InputStream.readSmall(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

internal data class HubItem(
    val id: String, val title: String, val subtitle: String = "",
    val description: String = "", val image: String = "",
    val url: String = "", val kind: String = ""
) {
    fun json() = JSONObject().put("id", id).put("title", title)
        .put("subtitle", subtitle).put("description", description)
        .put("image", image).put("url", url).put("kind", kind)

    companion object {
        fun from(o: JSONObject) = HubItem(
            o.optString("id"), o.optString("title"), o.optString("subtitle"),
            o.optString("description"), o.optString("image"), o.optString("url"),
            o.optString("kind")
        )
    }
}

internal data class HubTask(val id: String, val title: String, val action: String,
                            val payload: JSONObject, val status: String = "waiting") {
    fun json() = JSONObject().put("id", id).put("title", title)
        .put("action", action).put("payload", payload).put("status", status)
    companion object {
        fun from(o: JSONObject) = HubTask(o.optString("id"), o.optString("title"),
            o.optString("action"), o.optJSONObject("payload") ?: JSONObject(),
            o.optString("status", "waiting"))
    }
}

internal object HubStore {
    private val lock = Any()
    private fun prefs(context: Context) =
        context.getSharedPreferences("nexus_entertainment", Context.MODE_PRIVATE)
    private fun parseList(raw: String?): MutableList<JSONObject> {
        val a = try { JSONArray(raw ?: "[]") } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.toMutableList()
    }
    private fun write(context: Context, key: String, a: List<JSONObject>) {
        val json = JSONArray()
        a.forEach { json.put(it) }
        prefs(context).edit().putString(key, json.toString()).commit()
    }
    fun savedHost(context: Context) = prefs(context).getString("pc_host", "").orEmpty()
    fun key(context: Context) = prefs(context).getString("key", "").orEmpty()
    fun setHost(context: Context, host: String) {
        if (host.isNotBlank()) prefs(context).edit().putString("pc_host", host).apply()
    }
    fun setKey(context: Context, value: String) =
        prefs(context).edit().putString("key", value.trim()).commit()

    fun favorites(context: Context, games: Boolean): List<HubItem> = synchronized(lock) {
        parseList(prefs(context).getString(if (games) "favorite_games" else "favorite_videos", null))
            .map { HubItem.from(it) }
    }
    fun isFavorite(context: Context, item: HubItem): Boolean =
        favorites(context, item.kind in setOf("game", "steamrip", "installed-game"))
            .any { it.id == item.id && it.kind == item.kind }
    fun toggleFavorite(context: Context, item: HubItem): Boolean = synchronized(lock) {
        val k = if (item.kind in setOf("game", "steamrip", "installed-game"))
            "favorite_games" else "favorite_videos"
        val list = parseList(prefs(context).getString(k, null))
        val index = list.indexOfFirst {
            it.optString("id") == item.id && it.optString("kind") == item.kind
        }
        val added = index == -1
        if (added) list.add(0, item.json()) else list.removeAt(index)
        write(context, k, list.take(300))
        added
    }
    fun cacheResults(context: Context, kind: String, query: String, items: List<HubItem>) = synchronized(lock) {
        val key = "cached_${kind}_${query.lowercase().take(70)}"
        write(context, key, items.map { it.json() }.take(50))
        prefs(context).edit().putString("last_query_$kind", query).apply()
    }
    fun cachedResults(context: Context, kind: String, query: String): List<HubItem> = synchronized(lock) {
        parseList(prefs(context).getString("cached_${kind}_${query.lowercase().take(70)}", null))
            .map(HubItem::from)
    }
    fun cacheLocal(context: Context, type: String, items: List<HubItem>) = synchronized(lock) {
        write(context, "local_$type", items.map { it.json() }.take(300))
    }
    fun localItems(context: Context, type: String): List<HubItem> = synchronized(lock) {
        parseList(prefs(context).getString("local_$type", null)).map(HubItem::from)
    }
    fun tasks(context: Context): List<HubTask> = synchronized(lock) {
        parseList(prefs(context).getString("tasks", null)).map(HubTask::from)
    }
    fun enqueue(context: Context, title: String, action: String, data: JSONObject): HubTask = synchronized(lock) {
        val task = HubTask(UUID.randomUUID().toString(), title, action, data)
        val list = parseList(prefs(context).getString("tasks", null))
        list.add(0, task.json())
        write(context, "tasks", list.take(300))
        task
    }
    fun taskStatus(context: Context, id: String, status: String) = synchronized(lock) {
        val list = parseList(prefs(context).getString("tasks", null))
        list.find { it.optString("id") == id }?.put("status", status)
        write(context, "tasks", list)
    }
    fun removeTask(context: Context, id: String) = synchronized(lock) {
        write(context, "tasks", parseList(prefs(context).getString("tasks", null))
            .filterNot { it.optString("id") == id })
    }
}

internal object HubCatalog {
    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")
    private fun jsonGet(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6500
            connection.readTimeout = 8500
            connection.setRequestProperty("User-Agent", "NEXUS-Android/1.3")
            if (connection.responseCode !in 200..299)
                throw IllegalStateException("Catálogo indisponível: HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readSmall(1_600_000) }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    fun search(kind: String, term: String): List<HubItem> {
        if (kind == "movie" || kind == "series") return searchMedia(kind, term)
        require(term.trim().length >= 2) { "Digite pelo menos 2 letras" }
        val q = encode(term.trim().take(90))
        return when (kind) {
            "game" -> {
                val json = jsonGet("https://store.steampowered.com/api/storesearch/?cc=BR&l=portuguese&term=$q")
                val a = json.optJSONArray("items") ?: JSONArray()
                (0 until minOf(30, a.length())).mapNotNull { i ->
                    val o = a.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id")
                    if (!id.matches(Regex("[0-9]{1,10}"))) return@mapNotNull null
                    HubItem(id, o.optString("name"), "Steam · licença pode ser necessária",
                        image = o.optString("tiny_image"),
                        url = "https://store.steampowered.com/app/$id", kind = "game")
                }
            }
            "movie" -> {
                val json = jsonGet("https://itunes.apple.com/search?media=movie&entity=movie&country=BR&limit=30&term=$q")
                val a = json.optJSONArray("results") ?: JSONArray()
                (0 until a.length()).mapNotNull { i ->
                    val o = a.optJSONObject(i) ?: return@mapNotNull null
                    if (o.optString("kind") != "feature-movie") return@mapNotNull null
                    HubItem(o.optString("trackId"), o.optString("trackName"),
                        o.optString("releaseDate").take(4) + " · Apple TV",
                        o.optString("longDescription").take(350),
                        o.optString("artworkUrl100").replace("100x100bb", "300x300bb"),
                        o.optString("trackViewUrl"), "movie")
                }
            }
            "series" -> {
                val a = JSONArray(getText("https://api.tvmaze.com/search/shows?q=$q"))
                (0 until minOf(30, a.length())).mapNotNull { i ->
                    val o = a.optJSONObject(i)?.optJSONObject("show") ?: return@mapNotNull null
                    val summary = Jsoup.parse(o.optString("summary")).text().take(350)
                    HubItem(o.optString("id"), o.optString("name"),
                        o.optString("premiered").take(4) + " · TVMaze", summary,
                        o.optJSONObject("image")?.optString("medium").orEmpty(),
                        o.optString("url"), "series")
                }
            }
            "steamrip" -> {
                val url = steamRipSearchUrl(term)
                val response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Android; NEXUS)")
                    .timeout(10000).followRedirects(true).execute()
                val finalHost = URL(response.url().toString()).host.lowercase()
                require(finalHost == "steamrip.com" || finalHost == "www.steamrip.com") {
                    "A busca redirecionou para outro domínio"
                }
                val seen = mutableSetOf<String>()
                response.parse().select("a[href]").mapNotNull { a ->
                    val address = a.absUrl("href")
                    val host = try { URL(address).host.lowercase() } catch (_: Exception) { "" }
                    if (host !in setOf("steamrip.com", "www.steamrip.com")) return@mapNotNull null
                    val path = URL(address).path
                    if (!Regex("^/[^/]+-free-download/?$", RegexOption.IGNORE_CASE).matches(path))
                        return@mapNotNull null
                    val safeUrl = "https://steamrip.com" + (if (path.endsWith("/")) path else "$path/")
                    if (!seen.add(safeUrl)) return@mapNotNull null
                    val title = (a.text().ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
                        .ifBlank { path.trim('/').removeSuffix("-free-download").replace("-", " ") })
                        .replace(Regex("\\s+Free Download.*", RegexOption.IGNORE_CASE), "")
                        .trim().take(120)
                    if (title.isBlank()) return@mapNotNull null
                    val img = a.selectFirst("img")
                    val image = listOf("data-src", "data-lazy-src", "data-original", "src")
                        .map { attribute -> img?.absUrl(attribute).orEmpty() }
                        .firstOrNull { it.startsWith("https://") &&
                            !it.contains("placeholder", ignoreCase = true) }.orEmpty()
                    HubItem(safeUrl, title, "SteamRIP · página externa",
                        "Baixar e instalar por conta própria; o NEXUS não verifica executáveis.",
                        image, safeUrl, "steamrip")
                }.take(30).let { entries ->
                    // Image fallback from the public, official Steam search catalog.
                    // Never misrepresent this as a SteamRIP-hosted image.
                    val fallback = runCatching {
                        val steam = jsonGet(
                            "https://store.steampowered.com/api/storesearch/" +
                                "?cc=BR&l=portuguese&term=$q")
                        val items = steam.optJSONArray("items") ?: JSONArray()
                        (0 until items.length()).mapNotNull { index ->
                            val candidate = items.optJSONObject(index) ?: return@mapNotNull null
                            val name = candidate.optString("name")
                            val cover = candidate.optString("tiny_image")
                            if (name.isBlank() || !cover.startsWith("https://")) null
                            else normalizedTitle(name) to cover
                        }.toMap()
                    }.getOrDefault(emptyMap())
                    entries.map { item ->
                        if (item.image.isBlank())
                            item.copy(image = fallback[normalizedTitle(item.title)].orEmpty())
                        else item
                    }
                }
            }
            else -> emptyList()
        }
    }

    private fun normalizedTitle(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    private fun relevance(item: HubItem, query: String): Int {
        val title = normalizedTitle(item.title)
        val wanted = normalizedTitle(query)
        return when {
            title == wanted -> 120
            title.startsWith(wanted) -> 100
            title.contains(wanted) -> 80
            wanted.split(" ").filter { it.length >= 2 }.all { title.contains(it) } -> 60
            else -> 0
        }
    }

    /** Movie/series discovery independent of the Windows PC.
     * Search both a metadata source and an additional archive repository.
     * Archive results are listings, NOT a claim of public domain or downloadability.
     */
    private fun searchMedia(kind: String, term: String): List<HubItem> {
        val q = encode(term.trim().take(90))
        val primary: List<HubItem> = runCatching {
            if (kind == "movie") {
                val json = jsonGet(
                    "https://itunes.apple.com/search?media=movie&entity=movie" +
                        "&country=BR&lang=pt_br&limit=40&term=$q")
                val data = json.optJSONArray("results") ?: JSONArray()
                (0 until data.length()).mapNotNull { i ->
                    val o = data.optJSONObject(i) ?: return@mapNotNull null
                    if (o.optString("kind") != "feature-movie") return@mapNotNull null
                    HubItem(o.optString("trackId"), o.optString("trackName"),
                        o.optString("releaseDate").take(4) + " · Catálogo Apple",
                        (o.optString("longDescription").ifBlank {
                            o.optString("shortDescription")
                        }).take(450),
                        o.optString("artworkUrl100").replace("100x100bb", "600x600bb"),
                        o.optString("trackViewUrl"), "movie")
                }
            } else {
                val list = JSONArray(getText("https://api.tvmaze.com/search/shows?q=$q"))
                (0 until minOf(40, list.length())).mapNotNull { i ->
                    val o = list.optJSONObject(i)?.optJSONObject("show") ?: return@mapNotNull null
                    HubItem(o.optString("id"), o.optString("name"),
                        o.optString("premiered").take(4) + " · Catálogo TVmaze",
                        Jsoup.parse(o.optString("summary")).text().take(450),
                        (o.optJSONObject("image")?.optString("original")
                            ?: o.optJSONObject("image")?.optString("medium")).orEmpty(),
                        o.optString("url"), "series")
                }
            }
        }.getOrDefault(emptyList())
        val archive: List<HubItem> = runCatching {
            val keyword = term.trim().take(90)
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()
            val archiveQuery = encode("title:($keyword) AND mediatype:movies")
            val json = jsonGet("https://archive.org/advancedsearch.php?q=$archiveQuery" +
                "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year" +
                "&rows=15&page=1&output=json")
            val docs = json.optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
            (0 until docs.length()).mapNotNull { i ->
                val o = docs.optJSONObject(i) ?: return@mapNotNull null
                val identifier = o.optString("identifier")
                val title = o.optString("title")
                if (!identifier.matches(Regex("[A-Za-z0-9._-]{1,140}")) || title.isBlank())
                    return@mapNotNull null
                HubItem("archive:$identifier", title.take(130),
                    o.optString("year") + " · Internet Archive",
                    "Acervo externo: verifique licença, idioma e arquivos disponíveis.",
                    "https://archive.org/services/img/$identifier",
                    "https://archive.org/details/$identifier", kind)
            }
        }.getOrDefault(emptyList())
        val all = primary + archive
        val selected = all.filter { relevance(it, term) >= 60 }
        val filtered = if (selected.isNotEmpty()) selected else all
        return filtered.distinctBy { normalizedTitle(it.title) + ":" +
            if (it.id.startsWith("archive:")) "archive" else "catalog" }
            .sortedByDescending { relevance(it, term) }
            .take(35)
    }

    private fun getText(address: String): String {
        val c = URL(address).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 6500
            c.readTimeout = 8500
            if (c.responseCode !in 200..299) throw IllegalStateException("Catálogo indisponível")
            return c.inputStream.use { String(it.readSmall(1_600_000), Charsets.UTF_8) }
        } finally { c.disconnect() }
    }
    fun steamRipSearchUrl(query: String): String =
        "https://steamrip.com/?s=${encode(query.trim().take(90))}"
}

internal object HubPc {
    suspend fun request(context: Context, path: String, data: JSONObject? = null,
                        host: String = HubStore.savedHost(context)): JSONObject =
        withContext(Dispatchers.IO) {
            val key = HubStore.key(context)
            if (host.isBlank() || key.isBlank()) error("PC ainda não pareado")
            val c = URL("http://$host:8765$path").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 1400
                c.readTimeout = 5000
                c.setRequestProperty("X-Nexus-Central-Key", key)
                if (data != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val bytes = data.toString().toByteArray(Charsets.UTF_8)
                    c.setFixedLengthStreamingMode(bytes.size)
                    c.outputStream.use { it.write(bytes) }
                }
                val response = c.responseCode
                val body = (if (response in 200..299) c.inputStream else c.errorStream)
                    ?.use { String(it.readSmall(1_600_000), Charsets.UTF_8) }.orEmpty()
                val o = JSONObject(body.ifBlank { "{}" })
                if (response !in 200..299)
                    error(o.optString("error", "Falha na conexão com PC ($response)"))
                o
            } finally { c.disconnect() }
        }
}

internal object HubSync {
    private val syncLock = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastLibraryScan = 0L
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<HubSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "nexus-entertainment-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
    suspend fun trySync(context: Context, liveHost: String = ""): Boolean {
        if (!syncLock.compareAndSet(false, true)) return false
        try {
            if (liveHost.isNotBlank()) HubStore.setHost(context, liveHost)
            if (HubStore.savedHost(context).isBlank() || HubStore.key(context).isBlank()) return false
            try { HubPc.request(context, "/entertainment/status") }
            catch (_: Exception) { return false }
            if (System.currentTimeMillis() - lastLibraryScan > 120_000L) {
                lastLibraryScan = System.currentTimeMillis()
                for (kind in listOf("game", "video")) {
                try {
                    val a = HubPc.request(context, "/entertainment/library?type=$kind").optJSONArray("items")
                    if (a != null) HubStore.cacheLocal(context, kind,
                        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(HubItem::from) })
                } catch (_: Exception) { }
                }
            }
            for (task in HubStore.tasks(context).filter { it.status == "waiting" }) {
                try {
                    val request = JSONObject().put("id", task.id)
                        .put("action", task.action).put("payload", task.payload)
                    HubPc.request(context, "/entertainment/dispatch", request)
                    HubStore.taskStatus(context, task.id, "sent")
                } catch (_: Exception) {
                    // The job remains stored on the phone for the next sync.
                    break
                }
            }
            return true
        } finally { syncLock.set(false) }
    }
}

class HubSyncWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            HubSync.trySync(applicationContext)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
