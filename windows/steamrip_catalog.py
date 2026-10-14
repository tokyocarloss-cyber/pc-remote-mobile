"""Optional SteamRIP website discovery for NEXUS.

Read-only HTML catalog search. Never download, extract, install, or execute
content from this third-party website; open only verified steamrip.com pages
in the PC's own browser after the user explicitly requests that action.
"""
from html.parser import HTMLParser
from pathlib import PurePosixPath
import html
import os
import re
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://steamrip.com/"
MAX_HTML_BYTES = 2 * 1024 * 1024
_PAGE_PATTERN = re.compile(r"^/[^/]+-free-download/?$", re.IGNORECASE)


def _allowed_url(raw, *, allow_search=False):
    url = urllib.parse.urlsplit(urllib.parse.urljoin(BASE, str(raw or "")))
    if (url.scheme != "https" or (url.hostname or "").lower() not in
            ("steamrip.com", "www.steamrip.com") or
            url.port not in (None, 443) or url.username or url.password):
        raise ValueError("Somente páginas HTTPS de steamrip.com são aceitas")
    if allow_search and url.path in ("", "/") and not url.query:
        return BASE
    if not _PAGE_PATTERN.fullmatch(url.path):
        raise ValueError("Endereço não é uma página de jogo da SteamRIP")
    return "https://steamrip.com" + (url.path if url.path.endswith("/") else url.path + "/")


def _search_url(query):
    query = str(query or "").strip()
    if len(query) < 2 or len(query) > 90:
        raise ValueError("Digite de 2 a 90 caracteres para pesquisar")
    return BASE + "?s=" + urllib.parse.quote_plus(query)


class _SameOriginRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        parsed = urllib.parse.urlsplit(newurl)
        if (parsed.scheme != "https" or (parsed.hostname or "").lower() not in
                ("steamrip.com", "www.steamrip.com") or
                parsed.port not in (None, 443) or parsed.username or parsed.password):
            raise ValueError("Redirecionamento não permitido na pesquisa SteamRIP")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def _get_html(url):
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Mozilla/5.0 (compatible; NEXUSRemote/1.2)",
                 "Accept": "text/html,application/xhtml+xml"},
    )
    opener = urllib.request.build_opener(_SameOriginRedirects())
    try:
        with opener.open(request, timeout=9) as response:
            _allowed_url(response.geturl(), allow_search=True) if not urllib.parse.urlsplit(
                response.geturl()).query else _allowed_search_response(response.geturl())
            length = int(response.headers.get("Content-Length", "0") or 0)
            if length > MAX_HTML_BYTES:
                raise ValueError("Página da SteamRIP excedeu o limite")
            raw = response.read(MAX_HTML_BYTES + 1)
            if len(raw) > MAX_HTML_BYTES:
                raise ValueError("Página da SteamRIP excedeu o limite")
            encoding = response.headers.get_content_charset() or "utf-8"
            return raw.decode(encoding, errors="replace")
    except (urllib.error.URLError, TimeoutError) as error:
        raise ValueError("SteamRIP indisponível para pesquisa automática") from error


def _allowed_search_response(url):
    p = urllib.parse.urlsplit(url)
    if (p.scheme != "https" or (p.hostname or "").lower() not in
            ("steamrip.com", "www.steamrip.com") or
            p.port not in (None, 443) or p.username or p.password or
            p.path not in ("", "/") or
            set(urllib.parse.parse_qs(p.query)) - {"s"}):
        raise ValueError("Redirecionamento não permitido na pesquisa SteamRIP")


def _safe_image(raw):
    if not raw:
        return ""
    try:
        url = urllib.parse.urlsplit(urllib.parse.urljoin(BASE, str(raw)))
        if (url.scheme == "https" and url.hostname in ("steamrip.com", "www.steamrip.com")
                and url.port in (None, 443) and
                url.path.startswith("/wp-content/") and
                url.path.lower().endswith((".png", ".jpg", ".jpeg", ".webp"))):
            return urllib.parse.urlunsplit((url.scheme, url.netloc, url.path, "", ""))
    except ValueError:
        pass
    return ""


class _GamesParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.current = None
        self.results = []
        self.urls = set()

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "a":
            if self.current:
                self._complete()
            try:
                href = _allowed_url(attrs.get("href", ""))
            except ValueError:
                self.current = None
                return
            self.current = {"url": href, "texts": [], "image": ""}
            for field in ("title", "aria-label"):
                if attrs.get(field):
                    self.current["texts"].append(attrs[field])
        elif tag == "img" and self.current:
            for field in ("alt", "title"):
                if attrs.get(field):
                    self.current["texts"].append(attrs[field])
            for field in ("data-src", "data-lazy-src", "src"):
                img = _safe_image(attrs.get(field, ""))
                if img:
                    self.current["image"] = img
                    break

    def handle_data(self, data):
        if self.current and data.strip():
            self.current["texts"].append(data)

    def handle_endtag(self, tag):
        if tag == "a" and self.current:
            self._complete()

    def _complete(self):
        entry = self.current
        self.current = None
        if not entry or entry["url"] in self.urls:
            return
        name = " ".join(entry["texts"]).strip()
        name = re.sub(r"\s+", " ", html.unescape(name))[:120]
        if not name or len(name) > 110:
            slug = PurePosixPath(urllib.parse.urlsplit(entry["url"]).path).name
            name = slug.removesuffix("-free-download").replace("-", " ").title()
        name = re.sub(r"\s+Free Download\b.*", "", name, flags=re.IGNORECASE).strip()
        if not name:
            return
        self.urls.add(entry["url"])
        self.results.append({
            "id": entry["url"],
            "title": name,
            "subtitle": "SteamRIP · site de terceiros",
            "description": "Página externa; download e instalação não são automáticos.",
            "image": entry["image"],
            "url": entry["url"],
            "kind": "steamrip",
        })


def parse_results(html_text):
    parser = _GamesParser()
    parser.feed(html_text)
    parser.close()
    if parser.current:
        parser._complete()
    return parser.results[:24]


def search(query):
    source = _get_html(_search_url(query))
    if "checking your browser" in source.lower() or "just a moment..." in source.lower():
        raise ValueError("SteamRIP solicitou verificação no navegador; use 'Abrir busca no PC'.")
    return parse_results(source)


def open_in_browser(*, url="", query=""):
    if bool(url) == bool(query):
        raise ValueError("Informe apenas uma página ou uma pesquisa")
    dest = _allowed_url(url) if url else _search_url(query)
    os.startfile(dest)
    return {"ok": True, "message": "Página SteamRIP aberta no navegador do PC"}
