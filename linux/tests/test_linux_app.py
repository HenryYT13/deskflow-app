"""Unit and integration tests for the DeskFlow Linux GTK4 + Libadwaita application."""

from __future__ import annotations

import io
import json
import logging
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any, Dict, List, Optional

ROOT_DIR = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT_DIR / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from deskflow.api_client import DeskFlowApiClient, logger as deskflow_logger
from deskflow.models import (
    ConfigStore,
    MorseCodeConverter,
    ScheduledDarkModeEvaluator,
)


class _MockBackendHandler(BaseHTTPRequestHandler):
    notes_db: List[Dict[str, Any]] = []
    next_error_code: int = 0
    malformed_mode: Optional[str] = None
    last_request_path: str = ""
    last_request_headers: Dict[str, str] = {}
    last_request_body: Dict[str, Any] = {}

    def log_message(self, format: str, *args: Any) -> None:
        pass

    def _send_json(self, status: int, body: Dict[str, Any]) -> None:
        raw = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self) -> None:
        _MockBackendHandler.last_request_path = self.path
        _MockBackendHandler.last_request_headers = dict(self.headers)

        if _MockBackendHandler.next_error_code == 401:
            _MockBackendHandler.next_error_code = 0
            self._send_json(401, {"error": {"code": "token_invalid"}})
            return

        if self.path == "/api/app/health":
            self._send_json(
                200,
                {"status": "healthy", "service": "deskflow-unified-backend", "version": "1.0.0"},
            )
            return
        if self.path == "/api/focus/status":
            self._send_json(
                200,
                {"enabled": True, "focus_active": True, "version": 3},
            )
            return
        if self.path == "/api/app/control/notes":
            self._send_json(200, {"success": True, "notes": list(self.notes_db)})
            return
        if self.path.startswith("/api/app/control/weather/forecast"):
            self._send_json(
                200,
                {
                    "success": True,
                    "city": "Ho Chi Minh",
                    "intervals": [
                        {
                            "time": "2026-11-07 18:00:00",
                            "temp": 28.4,
                            "description": "light rain",
                            "rain_prob": 75.0,
                            "is_rain": True,
                        }
                    ],
                },
            )
            return
        # Emulate backend cors_middleware behavior on unknown route (404 -> invalid_request)
        self._send_json(404, {"error": {"code": "invalid_request"}})

    def do_POST(self) -> None:
        _MockBackendHandler.last_request_path = self.path
        _MockBackendHandler.last_request_headers = dict(self.headers)
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length).decode("utf-8")) if length > 0 else {}
        _MockBackendHandler.last_request_body = body

        # Note: Backend mounts pairing at /api/focus/pair, NOT /api/app/pair!
        if self.path == "/api/focus/pair":
            if _MockBackendHandler.malformed_mode == "bad_json":
                _MockBackendHandler.malformed_mode = None
                raw = b"<<<not valid json>>>"
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)
                return

            if _MockBackendHandler.malformed_mode == "missing_token":
                _MockBackendHandler.malformed_mode = None
                self._send_json(200, {"success": True, "device_name": "Incomplete"})
                return

            code = body.get("code") or body.get("pairing_code")
            if code in ("123456", "ABCD1234"):
                self._send_json(
                    200,
                    {
                        "success": True,
                        "device_token": "secret-linux-token-999",
                        "device_id": "dev_linux_01",
                        "device_name": body.get("device_name", "Linux"),
                        "platform": body.get("platform", "linux"),
                        "scopes": ["focus", "control", "spotify"],
                        "paired_at": "2026-11-07T18:00:00Z",
                        "expires_at": "2026-12-07T18:00:00Z",
                    },
                )
            else:
                self._send_json(403, {"error": {"code": "pair_code_invalid"}})
            return

        if self.path == "/api/focus/unpair":
            self._send_json(200, {"success": True, "revoked": True})
            return

        if self.path == "/api/app/control/notes":
            new_note = {
                "id": f"n-{len(self.notes_db) + 1}",
                "content": body.get("text", ""),
                "color": body.get("color", "#FDE047"),
                "pinned": False,
            }
            self.notes_db.append(new_note)
            self._send_json(200, {"success": True, "notes": list(self.notes_db)})
            return

        # Unknown POST route -> backend returns 404/405 wrapped as invalid_request
        self._send_json(405, {"error": {"code": "invalid_request"}})

    def do_DELETE(self) -> None:
        if _MockBackendHandler.next_error_code != 0:
            code = _MockBackendHandler.next_error_code
            _MockBackendHandler.next_error_code = 0
            self._send_json(code, {"error": f"err_{code}", "message": f"Simulated {code}"})
            return

        if self.path.startswith("/api/app/control/notes/"):
            nid = self.path.rsplit("/", 1)[-1]
            before = len(self.notes_db)
            _MockBackendHandler.notes_db = [n for n in self.notes_db if n["id"] != nid]
            if len(self.notes_db) == before:
                self._send_json(404, {"error": "note_not_found", "message": "Note not found"})
                return
            self._send_json(
                200,
                {
                    "success": True,
                    "deleted_id": nid,
                    "notes": list(self.notes_db),
                },
            )
            return

        self._send_json(404, {"error": "not_found"})


class TestDeskFlowLinuxApp(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.server = HTTPServer(("127.0.0.1", 0), _MockBackendHandler)
        cls.port = cls.server.server_port
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self) -> None:
        _MockBackendHandler.notes_db = []
        _MockBackendHandler.next_error_code = 0
        _MockBackendHandler.malformed_mode = None
        _MockBackendHandler.last_request_path = ""
        _MockBackendHandler.last_request_headers = {}
        _MockBackendHandler.last_request_body = {}
        self.tmp_dir = tempfile.TemporaryDirectory()
        self.cfg_path = Path(self.tmp_dir.name) / "settings.json"
        self.store = ConfigStore(self.cfg_path)
        self.store.config.api_url = f"http://127.0.0.1:{self.port}"
        self.store.save()
        self.client = DeskFlowApiClient(self.store)

    def tearDown(self) -> None:
        self.tmp_dir.cleanup()

    # =========================================================================
    # Required Pairing Tests (Phase B)
    # =========================================================================

    def test_1_valid_pairing_request_and_no_secret_leak_in_logs(self) -> None:
        log_capture = io.StringIO()
        handler = logging.StreamHandler(log_capture)
        deskflow_logger.addHandler(handler)
        try:
            res = self.client.pair_device("abcd1234", "Fedora Workstation")
        finally:
            deskflow_logger.removeHandler(handler)

        self.assertTrue(res.ok)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(_MockBackendHandler.last_request_path, "/api/focus/pair")
        self.assertEqual(_MockBackendHandler.last_request_body.get("pairing_code"), "ABCD1234")
        self.assertEqual(_MockBackendHandler.last_request_body.get("code"), "ABCD1234")
        self.assertEqual(_MockBackendHandler.last_request_body.get("platform"), "linux")
        self.assertEqual(_MockBackendHandler.last_request_body.get("device_name"), "Fedora Workstation")

        # Verify diagnostic logs were produced but raw secrets/tokens were NEVER logged
        logs = log_capture.getvalue()
        self.assertIn("/api/focus/pair", logs)
        self.assertIn("dev_linux_01", logs)
        self.assertNotIn("ABCD1234", logs)
        self.assertNotIn("secret-linux-token-999", logs)

    def test_2_invalid_pairing_code_and_format(self) -> None:
        # Format too short (< 6 chars) rejected client-side
        short_res = self.client.pair_device("123")
        self.assertFalse(short_res.ok)
        self.assertEqual(short_res.status_code, 400)
        self.assertEqual(short_res.error_code, "invalid_pairing_code_format")
        self.assertFalse(self.store.config.is_paired)

        # Invalid 8-char code rejected by backend with 403 pair_code_invalid
        bad_res = self.client.pair_device("ZZZZ9999")
        self.assertFalse(bad_res.ok)
        self.assertEqual(bad_res.status_code, 403)
        self.assertEqual(bad_res.error_code, "pair_code_invalid")
        self.assertIn("Invalid, expired, or already-used pairing code", bad_res.error_message)
        self.assertFalse(self.store.config.is_paired)

    def test_3_empty_pairing_code(self) -> None:
        res = self.client.pair_device("   ")
        self.assertFalse(res.ok)
        self.assertEqual(res.status_code, 400)
        self.assertEqual(res.error_code, "empty_pairing_code")
        self.assertEqual(_MockBackendHandler.last_request_path, "")
        self.assertFalse(self.store.config.is_paired)

    def test_4_malformed_server_response(self) -> None:
        # Case A: invalid JSON syntax
        _MockBackendHandler.malformed_mode = "bad_json"
        res_json = self.client.pair_device("ABCD1234")
        self.assertFalse(res_json.ok)
        self.assertEqual(res_json.error_code, "malformed_response")
        self.assertFalse(self.store.config.is_paired)

        # Case B: HTTP 200 JSON missing device_token / device_id
        _MockBackendHandler.malformed_mode = "missing_token"
        res_missing = self.client.pair_device("ABCD1234")
        self.assertFalse(res_missing.ok)
        self.assertEqual(res_missing.status_code, 502)
        self.assertEqual(res_missing.error_code, "malformed_response")
        self.assertFalse(self.store.config.is_paired)

    def test_5_server_unavailable(self) -> None:
        self.store.config.api_url = "http://127.0.0.1:1"
        res = self.client.pair_device("ABCD1234")
        self.assertFalse(res.ok)
        self.assertEqual(res.status_code, 0)
        self.assertIn(res.error_code, ("network_error", "timeout"))
        self.assertIn("Server unreachable", res.error_message)
        self.assertFalse(self.store.config.is_paired)

    def test_6_authentication_failure_revokes_local_session(self) -> None:
        self.client.pair_device("ABCD1234")
        self.assertTrue(self.store.config.is_paired)

        # Next authenticated GET returns HTTP 401 token_invalid
        _MockBackendHandler.next_error_code = 401
        status_res = self.client.get_focus_status()
        self.assertFalse(status_res.ok)
        self.assertEqual(status_res.status_code, 401)
        self.assertEqual(status_res.error_code, "token_invalid")
        self.assertFalse(self.store.config.is_paired)
        self.assertIsNone(self.store.config.device_token)

    def test_7_successful_pairing_state_update_and_persistence(self) -> None:
        res = self.client.pair_device("123456", "Linux Persistence Check")
        self.assertTrue(res.ok)
        self.assertTrue(self.store.config.is_paired)
        self.assertEqual(self.store.config.device_id, "dev_linux_01")
        self.assertEqual(self.store.config.device_name, "Linux Persistence Check")
        self.assertEqual(self.store.config.scopes, ["focus", "control", "spotify"])

        # Reload ConfigStore from disk to verify persistence
        reloaded = ConfigStore(self.cfg_path)
        self.assertTrue(reloaded.config.is_paired)
        self.assertEqual(reloaded.config.device_token, "secret-linux-token-999")
        self.assertEqual(reloaded.config.device_id, "dev_linux_01")

        # Subsequent authenticated request sends Bearer header to /api/focus/status
        st = self.client.get_focus_status()
        self.assertTrue(st.ok)
        self.assertEqual(_MockBackendHandler.last_request_path, "/api/focus/status")
        self.assertEqual(
            _MockBackendHandler.last_request_headers.get("Authorization"),
            "Bearer secret-linux-token-999",
        )

    # =========================================================================
    # Additional Feature Tests (Theme, Morse, Notes CRUD, Weather)
    # =========================================================================

    def test_scheduled_dark_mode_evaluator(self) -> None:
        self.assertFalse(ScheduledDarkModeEvaluator.is_scheduled_dark_active(19 * 60 + 59, "20:00", "06:00"))
        self.assertTrue(ScheduledDarkModeEvaluator.is_scheduled_dark_active(20 * 60, "20:00", "06:00"))
        self.assertTrue(ScheduledDarkModeEvaluator.is_scheduled_dark_active(0, "20:00", "06:00"))
        self.assertTrue(ScheduledDarkModeEvaluator.is_scheduled_dark_active(5 * 60 + 59, "20:00", "06:00"))
        self.assertFalse(ScheduledDarkModeEvaluator.is_scheduled_dark_active(6 * 60, "20:00", "06:00"))
        self.assertFalse(ScheduledDarkModeEvaluator.is_scheduled_dark_active(12 * 60, "06:00", "06:00"))

    def test_morse_converter_bidirectional_and_vietnamese(self) -> None:
        morse = MorseCodeConverter.text_to_morse("SOS Đạt")
        self.assertEqual(morse, "... --- ... / -.. .- -")
        decoded = MorseCodeConverter.morse_to_text(morse)
        self.assertEqual(decoded, "SOS DAT")

    def test_notes_crud_and_authoritative_deletion(self) -> None:
        pair_res = self.client.pair_device("123456", "Linux Test")
        self.assertTrue(pair_res.ok)

        c1 = self.client.create_note("First Linux Note")
        self.assertTrue(c1.ok)
        c2 = self.client.create_note("Second Linux Note")
        self.assertTrue(c2.ok)
        self.assertEqual(len(self.client.notes), 2)

        # Simulate HTTP 500 on delete -> must NOT remove note locally
        _MockBackendHandler.next_error_code = 500
        err_del = self.client.delete_note("n-1")
        self.assertFalse(err_del.ok)
        self.assertEqual(err_del.status_code, 500)
        self.assertEqual(len(self.client.notes), 2)

        # Delete first note -> HTTP 200 -> 1 note remains
        ok_del1 = self.client.delete_note("n-1")
        self.assertTrue(ok_del1.ok)
        self.assertEqual(len(self.client.notes), 1)
        self.assertEqual(self.client.notes[0].id, "n-2")

        # Delete last note -> HTTP 200 -> list must be empty []
        ok_del2 = self.client.delete_note("n-2")
        self.assertTrue(ok_del2.ok)
        self.assertEqual(len(self.client.notes), 0)

    def test_weather_forecast_parsing(self) -> None:
        res, intervals = self.client.get_weather_forecast("Ho Chi Minh")
        self.assertTrue(res.ok)
        self.assertEqual(len(intervals), 1)
        self.assertEqual(intervals[0].description, "light rain")
        self.assertTrue(intervals[0].is_rain)
        self.assertAlmostEqual(intervals[0].rain_prob, 75.0)

    def test_8_live_aiohttp_backend_pairing_contract_integration(self) -> None:
        """Verifies Linux DeskFlowApiClient against the real aiohttp FocusService backend."""
        try:
            import asyncio
            from aiohttp import web
        except ImportError:
            self.skipTest("aiohttp is not installed in the current environment")

        repo_root = ROOT_DIR.parent
        server_src = repo_root / "_server_copy" / "src"
        if str(server_src) not in sys.path:
            sys.path.insert(0, str(server_src))

        from focus_mode.storage import FocusStorage
        from focus_mode.auth import FocusAuth
        from focus_mode.challenge import MathChallengeSystem
        from focus_mode.classifier import WebsiteClassifier
        from focus_mode.service import FocusService
        from focus_mode.api import create_app

        with tempfile.TemporaryDirectory() as backend_tmp:
            storage = FocusStorage(data_dir=backend_tmp)
            storage.initialize()
            auth = FocusAuth(storage=storage)
            challenge = MathChallengeSystem(storage=storage, auth=auth, groq_client=None)
            classifier = WebsiteClassifier(storage=storage, groq_client=None)
            service = FocusService(
                storage=storage,
                auth=auth,
                challenge=challenge,
                classifier=classifier,
            )

            loop = asyncio.new_event_loop()
            runner_holder: Dict[str, Any] = {}
            ready_evt = threading.Event()

            def _run_aiohttp() -> None:
                asyncio.set_event_loop(loop)

                async def _start() -> None:
                    raw_code, _ = await service.auth.generate_pairing_code(
                        scopes=["focus", "control", "spotify"]
                    )
                    runner_holder["code"] = raw_code
                    app = create_app(service)
                    runner = web.AppRunner(app)
                    await runner.setup()
                    site = web.TCPSite(runner, "127.0.0.1", 0)
                    await site.start()
                    port = site._server.sockets[0].getsockname()[1]
                    runner_holder["runner"] = runner
                    runner_holder["port"] = port
                    ready_evt.set()

                loop.run_until_complete(_start())
                loop.run_forever()

            t = threading.Thread(target=_run_aiohttp, daemon=True)
            t.start()
            self.assertTrue(ready_evt.wait(timeout=5.0))

            try:
                live_port = runner_holder["port"]
                live_code = runner_holder["code"]
                self.store.config.api_url = f"http://127.0.0.1:{live_port}"

                # 1. Invalid code against real backend -> 403 pair_code_invalid
                bad = self.client.pair_device("WRONG999", "Fedora Client")
                self.assertFalse(bad.ok)
                self.assertEqual(bad.status_code, 403)
                self.assertEqual(bad.error_code, "pair_code_invalid")

                # 2. Valid generated code against real backend -> 200 OK + token + scopes
                good = self.client.pair_device(live_code, "Fedora Client")
                self.assertTrue(good.ok)
                self.assertEqual(good.status_code, 200)
                self.assertTrue(self.store.config.is_paired)
                self.assertEqual(self.store.config.scopes, ["focus", "control", "spotify"])

                # 3. Authenticated /api/focus/status call succeeds
                st = self.client.get_focus_status()
                self.assertTrue(st.ok)
                self.assertEqual(st.status_code, 200)

                # 4. Authenticated /api/focus/unpair call revokes token
                unp = self.client.unpair_device()
                self.assertTrue(unp.ok)
                self.assertFalse(self.store.config.is_paired)
            finally:
                loop.call_soon_threadsafe(loop.stop)
                t.join(timeout=3.0)
                storage.release_process_lock()

    def test_clock_style_mapping(self) -> None:
        """Verifies int clock styles map to string codes."""
        self.assertEqual(self.client.CLOCK_STYLE_ID_TO_CODE[1], "digital_classic")
        self.assertEqual(self.client.CLOCK_STYLE_ID_TO_CODE[2], "digital_minimal")
        self.assertEqual(self.client.CLOCK_STYLE_ID_TO_CODE[3], "digital_neon")
        self.assertEqual(self.client.CLOCK_STYLE_ID_TO_CODE[4], "analog")
        self.assertEqual(self.client.CLOCK_STYLE_ID_TO_CODE[5], "flip")
        self.assertEqual(self.client.CLOCK_STYLE_ID_TO_CODE[6], "binary")
        self.assertEqual(self.client.CLOCK_STYLE_ID_TO_CODE[7], "word")

    def test_morse_converter_bidirectional(self) -> None:
        """Verifies bidirectional live Morse conversion."""
        from deskflow.models import MorseCodeConverter
        encoded = MorseCodeConverter.text_to_morse("SOS")
        self.assertEqual(encoded, "... --- ...")
        decoded = MorseCodeConverter.morse_to_text("... --- ...")
        self.assertEqual(decoded, "SOS")


if __name__ == "__main__":
    unittest.main()
