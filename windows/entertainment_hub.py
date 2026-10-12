"""NEXUS Entertainment Hub: authorized catalogs, local playback and PC downloads.

All commands require a separate pairing key. Steam installation goes through
Steam's own client/account. Movie searches return metadata, not pirated media.
Only archive.org /download/ video URLs can enter the download queue.
"""
from __future__ import annotations

import hashlib
import hmac
import html
import json
import os
import re
import secrets
import threading
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from datetime import datetime

import pc_remote_server as servermod

STATE_DIR = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "NEXUS PC Remote"
STATE_DIR.mkdir(parents=True, exist_ok=True)
KEY_FILE = STATE_DIR / "entertainment_key.txt"
VIDEO_DIR = Path.home() / "Videos" / "NEXUS"
VIDEO_DIR.mkdir(parents=True, exist_ok=True)
SUFFIXES = {".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v"}
MAX_BYTES = 10 * 1024 ** 3
GAME_STORES = {"Steam", "Epic", "Xbox", "EA", "Ubisoft", "Battle.net", "GOG"}
_LOCK = threading.Lock()
_JOBS = {}
_CANCELLATIONS = {}


def pairing_key():
    if not KEY_FILE.exists():
        # Exclusive creation preserves a single secret if startup races.
        try:
            with KEY_FILE.open("x", encoding="utf-8") as stream:
                stream.write(secrets.token_urlsafe(24))
        except FileExistsError:
            pass
    return KEY_FILE.read_text(encoding="utf-8").strip()


def authenticated(candidate):
    key = pairing_key()
    return bool(candidate) and hmac.compare_digest(str(candidate).strip(), key)


def _fetch_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": "NEXUS-Entertainment/1.0"})
    with urllib.request.urlopen(request, timeout=8) as response:
        if int(response.headers.get("Content-Length", 0) or 0) > 1_500_000:
            raise ValueError("Resposta do catálogo muito grande")
        return json.loads(response.read(1_500_001).decode("utf-8"))


def _strip_html(text):
    return html.unescape(re.sub(r"<[^>]*>", " ", str(text or ""))).strip()[:350]


def search(kind, query):
    query = str(query or "").strip()[:90]
    if len(query) < 2:
        return []
    q = urllib.parse.quote(query)
    if kind == "movie":
        data = _fetch_json(
            "https://itunes.apple.com/search?media=movie&entity=movie"
            f"&country=BR&limit=24&term={q}"
        )
        items = []
        for movie in data.get("results", []):
            if movie.get("kind") != "feature-movie":
                continue
            items.append({
                "id": str(movie.get("trackId", "")),
                "title": str(movie.get("trackName", ""))[:120],
                "subtitle": str(movie.get("releaseDate", ""))[:4] + " · Apple TV",
                "description": str(movie.get("longDescription") or movie.get("shortDescription") or "")[:350],
                "image": str(movie.get("artworkUrl100", "")).replace("100x100bb", "300x300bb"),
                "url": str(movie.get("trackViewUrl", "")),
                "kind": "movie"
            })
        return items
    if kind == "series":
        data = _fetch_json("https://api.tvmaze.com/search/shows?q=" + q)
        items = []
        for entry in data[:24]:
            show = entry.get("show") or {}
            img = show.get("image") or {}
            items.append({
                "id": str(show.get("id", "")),
                "title": str(show.get("name", ""))[:120],
                "subtitle": str(show.get("premiered") or "")[:4] + " · TVMaze",
                "description": _strip_html(show.get("summary")),
                "image": str(img.get("medium") or ""),
                "url": str(show.get("url", "")),
                "kind": "series"
            })
        return items
    if kind == "game":
        data = _fetch_json(
            "https://store.steampowered.com/api/storesearch/"
            "?cc=BR&l=portuguese&term=" + q
        )
        return [{
            "id": str(item.get("id", "")),
            "title": str(item.get("name", ""))[:120],
            "subtitle": "Steam · compra ou licença pode ser necessária",
            "description": "",
            "image": str(item.get("tiny_image", "")),
            "url": "https://store.steampowered.com/app/" + str(item.get("id", "")),
            "kind": "game"
        } for item in data.get("items", [])[:24] if str(item.get("id", "")).isdigit()]
    raise ValueError("Categoria de busca inválida")


def _video_files():
    result = []
    seen = set()
    for base in (Path.home() / "Videos", VIDEO_DIR):
        if not base.exists():
            continue
        visited = 0
        for root, dirs, files in os.walk(base, followlinks=False):
            # Bounded scan: prevents a huge disk tree from blocking requests.
            depth = len(Path(root).relative_to(base).parts)
            dirs[:] = [d for d in dirs if not d.startswith(".") and
                       not (Path(root) / d).is_symlink()] if depth < 3 else []
            for name in files:
                visited += 1
                if visited > 2000 or len(result) >= 160:
                    break
                p = Path(root) / name
                if p.suffix.lower() not in SUFFIXES or p.is_symlink():
                    continue
                try:
                    resolved = p.resolve(strict=True)
                    if resolved in seen:
                        continue
                    seen.add(resolved)
                    result.append((resolved, resolved.stat().st_mtime))
                except OSError:
                    continue
            if visited > 2000 or len(result) >= 160:
                break
    return sorted(result, key=lambda pair: pair[1], reverse=True)


def _video_id(path):
    return hashlib.sha256(str(path).encode("utf-8")).hexdigest()[:22]


def _installed_games():
    items = []
    try:
        apps = servermod.apps_catalog()
        for app in apps:
            if app.get("store") not in GAME_STORES:
                continue
            name = str(app.get("name", ""))
            if not name:
                continue
            items.append({
                "id": hashlib.sha256(name.encode("utf-8")).hexdigest()[:22],
                "title": name[:120],
                "subtitle": str(app.get("store", "")),
                "kind": "installed-game"
            })
    except Exception:
        pass
    return items[:200]


def library(kind):
    if kind == "game":
        return _installed_games()
    if kind == "video":
        return [{
            "id": _video_id(p),
            "title": p.stem[:130],
            "subtitle": f"{p.suffix.upper()[1:]} · {p.stat().st_size // (1024 * 1024)} MB",
            "kind": "local-video"
        } for p, _ in _video_files()]
    raise ValueError("Biblioteca inválida")


def play_video(video_id):
    for p, _ in _video_files():
        if _video_id(p) == video_id:
            os.startfile(str(p))
            return {"ok": True}
    raise ValueError("Vídeo não encontrado na biblioteca")


def launch_game(game_id):
    for item in _installed_games():
        if item["id"] == game_id:
            servermod.launch(item["title"])
            return {"ok": True}
    raise ValueError("Jogo não encontrado na biblioteca")


def steam_action(app_id, action):
    app_id = str(app_id).strip()
    if not re.fullmatch(r"[0-9]{1,10}", app_id):
        raise ValueError("Steam AppID inválido")
    if action not in ("store", "install"):
        raise ValueError("Ação Steam inválida")
    scheme = "steam://store/" if action == "store" else "steam://install/"
    os.startfile(scheme + app_id)
    return {"ok": True, "message": "Solicitação enviada ao cliente Steam"}


def open_catalog_link(url):
    parsed = urllib.parse.urlsplit(str(url))
    if (parsed.scheme != "https" or parsed.hostname not in
            {"itunes.apple.com", "music.apple.com", "tv.apple.com", "www.tvmaze.com", "tvmaze.com"}):
        raise ValueError("Link do catálogo não permitido")
    os.startfile(url)
    return {"ok": True}


def _archive_initial_url(value):
    p = urllib.parse.urlsplit(str(value).strip())
    if (p.scheme != "https" or p.hostname != "archive.org"
            or p.port not in (None, 443) or p.username or p.password
            or not p.path.startswith("/download/")
            or Path(urllib.parse.unquote(p.path)).suffix.lower() not in SUFFIXES):
        raise ValueError("Use um link HTTPS direto de vídeo em archive.org/download/")
    return p.geturl()


def _archive_host(url):
    p = urllib.parse.urlsplit(url)
    host = (p.hostname or "").lower()
    if (p.scheme != "https" or p.port not in (None, 443) or p.username or p.password
            or not (host == "archive.org" or host.endswith(".archive.org"))):
        raise ValueError("Redirecionamento para origem não permitida")
    return url


class _ArchiveRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        _archive_host(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def _job_update(job_id, **kwargs):
    with _LOCK:
        if job_id in _JOBS:
            _JOBS[job_id].update(kwargs)


def _download(job_id, url):
    with _LOCK:
        cancelled = _CANCELLATIONS[job_id]
    part = None
    try:
        opener = urllib.request.build_opener(_ArchiveRedirects())
        request = urllib.request.Request(url, headers={"User-Agent": "NEXUS-Entertainment/1.0"})
        _job_update(job_id, state="connecting")
        with opener.open(request, timeout=25) as response:
            total = int(response.headers.get("Content-Length", "0") or 0)
            if total > MAX_BYTES:
                raise ValueError("Arquivo excede 10 GB")
            p = urllib.parse.urlsplit(url)
            filename = Path(urllib.parse.unquote(p.path)).name
            filename = re.sub(r'[<>:"/\\|?*\\x00-\\x1f]', "_", filename).strip(" .")[:140]
            if not filename or Path(filename).suffix.lower() not in SUFFIXES:
                raise ValueError("Extensão de vídeo inválida")
            target = VIDEO_DIR / filename
            n = 1
            while target.exists():
                target = VIDEO_DIR / f"{Path(filename).stem}_{n}{Path(filename).suffix}"
                n += 1
            part = VIDEO_DIR / (job_id + ".nexus-part")
            downloaded = 0
            _job_update(job_id, state="downloading", total=total, title=target.name)
            with part.open("wb") as out:
                while True:
                    if cancelled.is_set():
                        raise InterruptedError("Cancelado")
                    chunk = response.read(256 * 1024)
                    if not chunk:
                        break
                    downloaded += len(chunk)
                    if downloaded > MAX_BYTES:
                        raise ValueError("Arquivo excede 10 GB")
                    out.write(chunk)
                    _job_update(job_id, downloaded=downloaded)
            if cancelled.is_set():
                raise InterruptedError("Cancelado")
            os.replace(part, target)
            _job_update(job_id, state="finished", downloaded=downloaded,
                        total=downloaded, video_id=_video_id(target))
    except InterruptedError:
        _job_update(job_id, state="cancelled")
    except Exception as exc:
        _job_update(job_id, state="error", error=str(exc)[:180])
    finally:
        if part is not None:
            part.unlink(missing_ok=True)
        with _LOCK:
            _CANCELLATIONS.pop(job_id, None)


def start_download(value):
    url = _archive_initial_url(value)
    with _LOCK:
        if sum(j.get("state") in ("connecting", "downloading") for j in _JOBS.values()) >= 2:
            raise ValueError("Máximo de dois downloads simultâneos")
        job_id = uuid.uuid4().hex[:14]
        _JOBS[job_id] = {"id": job_id, "title": "Preparando download",
                         "state": "queued", "downloaded": 0, "total": 0, "error": ""}
        _CANCELLATIONS[job_id] = threading.Event()
    threading.Thread(target=_download, args=(job_id, url),
                     daemon=True, name="NexusEntertainmentDownload").start()
    return {"ok": True, "id": job_id}


def list_downloads():
    with _LOCK:
        return list(reversed(list(_JOBS.values())[-30:]))


def cancel_download(job_id):
    with _LOCK:
        event = _CANCELLATIONS.get(str(job_id))
        if not event:
            raise ValueError("Download já concluído ou inexistente")
        event.set()
    return {"ok": True}
