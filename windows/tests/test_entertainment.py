"""Tests for the NEXUS Entertainment Hub without Internet access."""
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest import mock

import entertainment_hub as hub
from companion_v2 import CompanionHandler


class HubTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.key_patch = mock.patch.object(hub, "KEY_FILE", Path(self.temp.name) / "key.txt")
        self.video_patch = mock.patch.object(hub, "VIDEO_DIR", Path(self.temp.name))
        self.key_patch.start()
        self.video_patch.start()

    def tearDown(self):
        self.video_patch.stop()
        self.key_patch.stop()
        self.temp.cleanup()

    def test_pairing_key_persists_and_rejects_wrong_key(self):
        key = hub.pairing_key()
        self.assertGreaterEqual(len(key), 24)
        self.assertEqual(key, hub.pairing_key())
        self.assertTrue(hub.authenticated(key))
        self.assertFalse(hub.authenticated("wrong"))

    def test_public_downloads_only(self):
        self.assertIn("/download/", hub._archive_initial_url(
            "https://archive.org/download/example/movie.mp4"))
        with self.assertRaises(ValueError):
            hub._archive_initial_url("http://archive.org/download/example/movie.mp4")
        with self.assertRaises(ValueError):
            hub._archive_initial_url("https://example.com/download/example/movie.mp4")
        with self.assertRaises(ValueError):
            hub._archive_initial_url("https://archive.org/download/example/setup.exe")
        with self.assertRaises(ValueError):
            hub._archive_host("https://192.168.0.1/private")
        with self.assertRaises(ValueError):
            hub._archive_host("https://archive.org.evil.com/video.mp4")

    def test_steam_install_uses_official_client(self):
        with mock.patch.object(hub.os, "startfile", create=True) as startfile:
            hub.steam_action("620", "install")
            startfile.assert_called_once_with("steam://install/620")
        with self.assertRaises(ValueError):
            hub.steam_action("620;cmd", "install")

    def test_search_metadata_only(self):
        with mock.patch.object(hub, "_fetch_json", return_value={"results": [{
            "kind": "feature-movie", "trackId": 1, "trackName": "Example",
            "releaseDate": "2020-01-01", "trackViewUrl": "https://itunes.apple.com/movie"
        }]}):
            item = hub.search("movie", "Example")[0]
        self.assertEqual(item["title"], "Example")
        self.assertNotIn("download", item)

    def test_local_video_plays_from_catalog_only(self):
        movie = Path(self.temp.name) / "sample.mp4"
        movie.write_bytes(b"video")
        video_id = hub.library("video")[0]["id"]
        with mock.patch.object(hub.os, "startfile", create=True) as startfile:
            hub.play_video(video_id)
            startfile.assert_called_once_with(str(movie.resolve()))
        with self.assertRaises(ValueError):
            hub.play_video("arbitrary-path")


class ApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.key_patch = mock.patch.object(hub, "KEY_FILE",
                                          Path(cls.temp.name) / "key.txt")
        cls.key_patch.start()
        cls.http = ThreadingHTTPServer(("127.0.0.1", 0), CompanionHandler)
        cls.url = "http://127.0.0.1:" + str(cls.http.server_port)
        cls.thread = threading.Thread(target=cls.http.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()
        cls.key_patch.stop()
        cls.temp.cleanup()

    def call(self, path, headers=None, body=None):
        request = urllib.request.Request(self.url + path, headers=headers or {}, data=body)
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as err:
            raw = err.read().decode("utf-8")
            try:
                return err.code, json.loads(raw)
            except json.JSONDecodeError:
                return err.code, {"error": raw}

    def test_pairing_required_for_new_routes(self):
        self.assertEqual(self.call("/entertainment/status")[0], 401)
        key = hub.pairing_key()
        self.assertEqual(self.call("/entertainment/status",
                                  {"X-Nexus-Central-Key": key})[0], 200)
        self.assertEqual(self.call("/entertainment/status",
                                  {"X-Nexus-Central-Key": key,
                                   "Origin": "https://evil.example"})[0], 403)

    def test_download_requires_archive_video_url(self):
        key = hub.pairing_key()
        status, result = self.call(
            "/entertainment/download",
            {"X-Nexus-Central-Key": key, "Content-Type": "application/json"},
            json.dumps({"url": "https://example.com/file.mp4"}).encode()
        )
        self.assertEqual(status, 400)
        self.assertIn("error", result)


if __name__ == "__main__":
    unittest.main()
