"""Full DeskFlow REST API client for the Linux GTK4 + Libadwaita application."""

from __future__ import annotations

import json
import logging
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Set, Tuple

from .models import (
    CalendarEventItem,
    CalendarStatus,
    ConfigStore,
    CountdownItem,
    DualDayTimetable,
    NoteItem,
    TimetableDay,
    WeatherInterval,
)


def _configure_logger() -> logging.Logger:
    lgr = logging.getLogger("DeskFlow.Linux")
    if not lgr.handlers:
        handler = logging.StreamHandler(sys.stderr)
        formatter = logging.Formatter(
            fmt="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
            datefmt="%H:%M:%S",
        )
        handler.setFormatter(formatter)
        lgr.addHandler(handler)
        lgr.setLevel(logging.INFO)
    return lgr


logger = _configure_logger()


ERROR_CODE_MESSAGES: Dict[str, str] = {
    "pair_code_invalid": "Invalid, expired, or already-used pairing code. Generate a new code in Discord (/pair).",
    "invalid_request": "Invalid request rejected by server (check endpoint URL and request parameters).",
    "rate_limited": "Too many attempts. Please wait before trying again.",
    "cooldown": "Cooldown active due to repeated failed attempts.",
    "token_invalid": "Device session expired or revoked. Please pair again.",
    "unauthorized": "Authentication required or session expired (HTTP 401).",
    "insufficient_scope": "This device was paired without 'control' permission, so notes/alarms are read-only. Unpair and re-pair using a full-access code from Discord (/pair with full access).",
    "forbidden": "Action forbidden by server policy (HTTP 403).",
    "not_found": "Requested resource or endpoint was not found on server (HTTP 404).",
    "note_not_found": "Sticky note was not found on the server (HTTP 404).",
    "storage_unavailable": "Backend storage is temporarily unavailable (HTTP 503).",
    "internal": "Internal backend server error (HTTP 500).",
}


@dataclass
class ApiResult:
    ok: bool
    status_code: int
    data: Any = None
    error_code: str = ""
    error_message: str = ""
    latency_ms: int = 0
    retry_after: Optional[int] = None


class DeskFlowApiClient:
    """Thread-safe REST client matching the DeskFlow Backend & Android API contract."""

    def __init__(self, config_store: ConfigStore, timeout_sec: float = 10.0) -> None:
        self.config_store = config_store
        self.timeout_sec = timeout_sec
        self.notes: List[NoteItem] = []
        self.active_note_id: Optional[str] = None
        self.countdowns: List[CountdownItem] = []
        self.active_countdown_id: Optional[str] = None
        self.calendar_status: CalendarStatus = CalendarStatus()
        self._in_flight_note_deletes: Set[str] = set()
        self._in_flight_countdown_deletes: Set[str] = set()

    @property
    def base_url(self) -> str:
        return (self.config_store.config.api_url or "").rstrip("/")

    def _headers(self, authenticated: bool = True) -> Dict[str, str]:
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "DeskFlow-Linux-GTK4/2.4.9",
        }
        token = self.config_store.config.device_token
        if authenticated and token:
            headers["Authorization"] = f"Bearer {token}"
        return headers

    def request(
        self,
        method: str,
        path: str,
        payload: Optional[Dict[str, Any]] = None,
        authenticated: bool = True,
    ) -> ApiResult:
        url = f"{self.base_url}{path}"
        payload_keys = sorted(payload.keys()) if isinstance(payload, dict) else []
        has_auth = bool(authenticated and self.config_store.config.device_token)
        logger.info(
            "[HTTP] -> %s %s (auth=%s, payload_keys=%s)",
            method.upper(),
            url,
            "bearer" if has_auth else "none",
            payload_keys,
        )

        body_bytes = (
            json.dumps(payload, ensure_ascii=False).encode("utf-8")
            if payload is not None
            else None
        )
        req = urllib.request.Request(
            url=url,
            data=body_bytes,
            headers=self._headers(authenticated=authenticated),
            method=method.upper(),
        )
        t0 = time.monotonic()
        try:
            with urllib.request.urlopen(req, timeout=self.timeout_sec) as resp:
                latency = int((time.monotonic() - t0) * 1000)
                raw = resp.read().decode("utf-8", errors="replace")
                logger.info(
                    "[HTTP] <- %s %s status=%d latency=%dms bytes=%d",
                    method.upper(),
                    path,
                    resp.status,
                    latency,
                    len(raw),
                )
                if not raw.strip():
                    return ApiResult(ok=True, status_code=resp.status, data={}, latency_ms=latency)
                try:
                    parsed = json.loads(raw)
                except json.JSONDecodeError as je:
                    logger.error(
                        "[HTTP] Malformed JSON response from %s %s: %s",
                        method.upper(),
                        path,
                        je,
                    )
                    return ApiResult(
                        ok=False,
                        status_code=resp.status,
                        error_code="malformed_response",
                        error_message="Server returned malformed JSON response.",
                        latency_ms=latency,
                    )
                return ApiResult(
                    ok=True,
                    status_code=resp.status,
                    data=parsed,
                    latency_ms=latency,
                )
        except urllib.error.HTTPError as exc:
            latency = int((time.monotonic() - t0) * 1000)
            raw_err = ""
            try:
                raw_err = exc.read().decode("utf-8", errors="replace")
            except Exception:
                pass
            finally:
                try:
                    exc.close()
                except Exception:
                    pass
            code, msg, retry_after = self._parse_error_payload(exc.code, raw_err)
            if code == "insufficient_scope" or (exc.code == 403 and "scope" in raw_err.lower()):
                code, msg = "insufficient_scope", ERROR_CODE_MESSAGES["insufficient_scope"]
            logger.warning(
                "[HTTP] <- %s %s status=%d latency=%dms error_code=%s message=%s",
                method.upper(),
                path,
                exc.code,
                latency,
                code,
                msg,
            )
            if exc.code == 401 and authenticated:
                logger.warning("[Auth] HTTP 401 received on authenticated request; clearing local token.")
                self.config_store.clear_auth()
            return ApiResult(
                ok=False,
                status_code=exc.code,
                error_code=code,
                error_message=msg,
                latency_ms=latency,
                retry_after=retry_after,
            )
        except urllib.error.URLError as exc:
            latency = int((time.monotonic() - t0) * 1000)
            reason = str(getattr(exc, "reason", exc))
            err_code = "timeout" if "timed out" in reason.lower() else "network_error"
            logger.error(
                "[HTTP] Network failure on %s %s (%dms): %s",
                method.upper(),
                url,
                latency,
                reason,
            )
            return ApiResult(
                ok=False,
                status_code=0,
                error_code=err_code,
                error_message=f"Server unreachable ({reason}). Check API URL and network connection.",
                latency_ms=latency,
            )
        except Exception as exc:
            latency = int((time.monotonic() - t0) * 1000)
            logger.error(
                "[HTTP] Unexpected exception on %s %s: %s",
                method.upper(),
                url,
                exc,
                exc_info=True,
            )
            return ApiResult(
                ok=False,
                status_code=0,
                error_code="unknown_error",
                error_message=str(exc),
                latency_ms=latency,
            )

    @staticmethod
    def _parse_error_payload(status_code: int, raw_body: str) -> Tuple[str, str, Optional[int]]:
        retry_after: Optional[int] = None
        if raw_body.strip():
            try:
                obj = json.loads(raw_body)
                if isinstance(obj, dict):
                    err = obj.get("error")
                    if isinstance(err, dict):
                        c = str(err.get("code") or f"http_{status_code}")
                        if err.get("retry_after") is not None:
                            try:
                                retry_after = int(err["retry_after"])
                            except (ValueError, TypeError):
                                pass
                        explicit_msg = err.get("message") or obj.get("message") or obj.get("detail")
                        if explicit_msg:
                            m = str(explicit_msg)
                        else:
                            m = ERROR_CODE_MESSAGES.get(c, f"Server error: {c} (HTTP {status_code})")
                        if retry_after and f"{retry_after}s" not in m:
                            m = f"{m} (Retry after {retry_after}s)"
                        return c, m, retry_after
                    if isinstance(err, str):
                        explicit_msg = obj.get("message") or obj.get("detail")
                        m = (
                            str(explicit_msg)
                            if explicit_msg
                            else ERROR_CODE_MESSAGES.get(err, f"{err} (HTTP {status_code})")
                        )
                        return err, m, retry_after
                    if "detail" in obj:
                        return f"http_{status_code}", str(obj["detail"]), retry_after
            except Exception:
                pass
        default_map = {
            400: ("bad_request", "Invalid request parameters (HTTP 400)"),
            401: ("unauthorized", ERROR_CODE_MESSAGES["unauthorized"]),
            403: ("forbidden", ERROR_CODE_MESSAGES["forbidden"]),
            404: ("not_found", ERROR_CODE_MESSAGES["not_found"]),
            429: ("rate_limited", ERROR_CODE_MESSAGES["rate_limited"]),
            500: ("internal_error", ERROR_CODE_MESSAGES["internal"]),
        }
        code, msg = default_map.get(status_code, (f"http_{status_code}", f"HTTP Error {status_code}"))
        return code, msg, retry_after

    # -------------------------------------------------------------------------
    # Health & Pairing (Backend contract: /api/app/health & /api/focus/pair)
    # -------------------------------------------------------------------------
    def check_health(self) -> ApiResult:
        return self.request("GET", "/api/app/health", authenticated=False)

    def pair_device(self, pairing_code: str, device_name: Optional[str] = None) -> ApiResult:
        clean_code = (pairing_code or "").strip().upper()
        logger.info("[Pairing] Validating pairing code input (length=%d)", len(clean_code))

        if not clean_code:
            logger.warning("[Pairing] Rejected empty pairing code.")
            return ApiResult(
                ok=False,
                status_code=400,
                error_code="empty_pairing_code",
                error_message="Pairing code cannot be empty. Generate a code in Discord (/pair).",
            )

        if len(clean_code) < 6 or not clean_code.isalnum():
            logger.warning(
                "[Pairing] Rejected malformed pairing code (length=%d, isalnum=%s).",
                len(clean_code),
                clean_code.isalnum(),
            )
            return ApiResult(
                ok=False,
                status_code=400,
                error_code="invalid_pairing_code_format",
                error_message="Pairing code must be at least 6 alphanumeric characters.",
            )

        name = (device_name or self.config_store.config.device_name or "DeskFlow Linux GTK4").strip()
        res = self.request(
            "POST",
            "/api/focus/pair",
            payload={
                "pairing_code": clean_code,
                "code": clean_code,
                "device_name": name,
                "platform": "linux",
                "client_version": "2.4.9",
            },
            authenticated=False,
        )
        if not res.ok:
            logger.warning(
                "[Pairing] Pairing request failed: status=%d code=%s message=%s",
                res.status_code,
                res.error_code,
                res.error_message,
            )
            return res

        if not isinstance(res.data, dict):
            logger.error("[Pairing] Server returned non-object JSON payload during pairing.")
            return ApiResult(
                ok=False,
                status_code=502,
                error_code="malformed_response",
                error_message="Malformed pairing response from server (expected JSON object).",
            )

        token = res.data.get("device_token")
        dev_id = res.data.get("device_id")
        if not isinstance(token, str) or not token.strip() or not isinstance(dev_id, str) or not dev_id.strip():
            logger.error(
                "[Pairing] Server 200 response missing required device_token or device_id (keys=%s).",
                sorted(res.data.keys()),
            )
            return ApiResult(
                ok=False,
                status_code=502,
                error_code="malformed_response",
                error_message="Server response missing device_token or device_id.",
            )

        self.config_store.config.device_token = token.strip()
        self.config_store.config.device_id = dev_id.strip()
        self.config_store.config.device_name = str(res.data.get("device_name") or name)
        if isinstance(res.data.get("scopes"), list):
            self.config_store.config.scopes = [str(s) for s in res.data["scopes"]]
        self.config_store.save()
        logger.info(
            "[Pairing] Device paired: device_id=%s device_name=%s scopes=%s",
            self.config_store.config.device_id,
            self.config_store.config.device_name,
            self.config_store.config.scopes,
        )
        return res

    def create_pair_session(self, device_name: Optional[str] = None, platform: str = "linux") -> ApiResult:
        name = (device_name or self.config_store.config.device_name or "DeskFlow Linux GTK4").strip()
        logger.info("[PairingSession] Creating dynamic QR pairing session for device_name='%s'", name)
        return self.request(
            "POST",
            "/api/focus/pair/session/create",
            payload={"device_name": name, "platform": platform},
            authenticated=False,
        )

    def get_pair_session_status(self, session_id: str) -> ApiResult:
        res = self.request(
            "GET",
            f"/api/focus/pair/session/{urllib.parse.quote(str(session_id).strip(), safe='')}",
            authenticated=False,
        )
        if res.ok and isinstance(res.data, dict) and res.data.get("status") == "approved":
            token = res.data.get("device_token")
            dev_id = res.data.get("device_id")
            if token and dev_id:
                self.config_store.config.device_token = str(token).strip()
                self.config_store.config.device_id = str(dev_id).strip()
                if res.data.get("device_name"):
                    self.config_store.config.device_name = str(res.data["device_name"]).strip()
                if isinstance(res.data.get("scopes"), list):
                    self.config_store.config.scopes = [str(s) for s in res.data["scopes"]]
                self.config_store.save()
                logger.info("[PairingSession] Session approved! Saved device credentials: %s", dev_id)
        return res

    def unpair_device(self, device_id: Optional[str] = None) -> ApiResult:
        target_id = device_id or self.config_store.config.device_id
        payload = {"device_id": target_id} if target_id else {}
        res = self.request("POST", "/api/focus/unpair", payload=payload)
        if not device_id or device_id == self.config_store.config.device_id:
            self.config_store.clear_auth()
            logger.info("[Pairing] Local device credentials cleared (server_status=%d).", res.status_code)
        return res

    def rename_device(self, device_id: str, new_name: str) -> ApiResult:
        return self.request("POST", f"/api/focus/devices/{device_id}/rename", payload={"name": new_name})

    def get_selected_display_device(self) -> ApiResult:
        return self.request("GET", "/api/app/control/device/selected")

    def set_selected_display_device(self, device_id: str) -> ApiResult:
        return self.request("POST", "/api/app/control/device/selected", payload={"device_id": device_id})

    def get_alarms(self) -> ApiResult:
        return self.request("GET", "/api/app/control/alarms")

    def save_alarm(
        self,
        time_str: str,
        label: str,
        repeat_days: List[int],
        buzzer_duration: int = 250,
        alarm_id: Optional[str] = None,
    ) -> ApiResult:
        data: Dict[str, Any] = {
            "time": time_str,
            "label": label,
            "repeat_days": repeat_days,
            "buzzer_duration": buzzer_duration,
            "math_challenge": True,
        }
        if alarm_id:
            data["id"] = alarm_id
            return self.request("PUT", f"/api/app/control/alarms/{alarm_id}", payload=data)
        return self.request("POST", "/api/app/control/alarms", payload=data)

    def delete_alarm(self, alarm_id: str) -> ApiResult:
        return self.request("DELETE", f"/api/app/control/alarms/{urllib.parse.quote(str(alarm_id), safe='')}")

    def toggle_alarm(self, alarm_id: str, enabled: Optional[bool] = None) -> ApiResult:
        payload = {"enabled": enabled} if enabled is not None else {}
        return self.request("POST", f"/api/app/control/alarms/{urllib.parse.quote(str(alarm_id), safe='')}/toggle", payload=payload)

    def emergency_stop_alarm(self) -> ApiResult:
        return self.request("POST", "/api/app/control/alarm/emergency-stop", payload={})

    # -------------------------------------------------------------------------
    # Focus Mode & Challenge (Backend contract: /api/focus/*)
    # -------------------------------------------------------------------------
    def get_focus_status(self) -> ApiResult:
        return self.request("GET", "/api/focus/status")

    def enable_focus(self, duration_minutes: int = 25, reason: str = "Deep Work") -> ApiResult:
        return self.request(
            "POST",
            "/api/focus/enable",
            payload={
                "duration_minutes": max(1, int(duration_minutes)),
                "break_minutes": 5,
                "session_type": "focus",
                "reason": reason.strip() or "Deep Work",
                "language": self.config_store.config.language,
            },
        )

    def request_challenge(self, force_new: bool = False) -> ApiResult:
        return self.request(
            "POST",
            "/api/focus/challenge",
            payload={"language": self.config_store.config.language, "force_new": force_new},
        )

    def verify_challenge(self, challenge_id: str, answer: int) -> ApiResult:
        return self.request(
            "POST",
            "/api/focus/verify",
            payload={
                "challenge_id": challenge_id,
                "answer": int(answer),
                "language": self.config_store.config.language,
            },
        )

    def get_focus_history(self) -> ApiResult:
        return self.request("GET", "/api/focus/history")

    def get_paired_devices(self) -> ApiResult:
        return self.request("GET", "/api/focus/devices")

    # -------------------------------------------------------------------------
    # Notes CRUD & Pinning (Authoritative Backend Sync: /api/app/control/notes)
    # -------------------------------------------------------------------------
    def _sync_notes_from_payload(self, body: Dict[str, Any]) -> List[NoteItem]:
        raw_list = body.get("notes") if isinstance(body.get("notes"), list) else body.get("data", [])
        if not isinstance(raw_list, list):
            raw_list = []
        active_id = body.get("active_station_note_id") or body.get("active_note_id")
        self.active_note_id = str(active_id) if active_id else None
        self.notes = [
            NoteItem.from_dict(item, active_id=self.active_note_id)
            for item in raw_list
            if isinstance(item, dict)
        ]
        return list(self.notes)

    def get_notes(self) -> ApiResult:
        res = self.request("GET", "/api/app/control/notes")
        if res.ok and isinstance(res.data, dict):
            self._sync_notes_from_payload(res.data)
        return res

    def create_note(self, text: str, color: str = "#FDE047") -> ApiResult:
        clean_text = (text or "").strip()
        if not clean_text:
            return ApiResult(
                ok=False,
                status_code=400,
                error_code="invalid_text",
                error_message="Note content cannot be empty.",
            )
        res = self.request(
            "POST",
            "/api/app/control/notes",
            payload={"text": clean_text, "content": clean_text, "color": color},
        )
        if res.ok and isinstance(res.data, dict):
            self._sync_notes_from_payload(res.data)
        return res

    def delete_note(self, note_id: str) -> ApiResult:
        clean_id = (note_id or "").strip()
        if not clean_id:
            return ApiResult(
                ok=False,
                status_code=400,
                error_code="invalid_id",
                error_message="Note ID cannot be empty.",
            )
        if clean_id in self._in_flight_note_deletes:
            return ApiResult(
                ok=False,
                status_code=409,
                error_code="duplicate_request",
                error_message="Deletion already in progress for this note.",
            )
        self._in_flight_note_deletes.add(clean_id)
        try:
            encoded_id = urllib.parse.quote(clean_id, safe="")
            res = self.request("DELETE", f"/api/app/control/notes/{encoded_id}")
            if res.ok and isinstance(res.data, dict):
                self._sync_notes_from_payload(res.data)
            return res
        finally:
            self._in_flight_note_deletes.discard(clean_id)

    def pin_note(self, note_id: str) -> ApiResult:
        clean_id = (note_id or "").strip()
        if not clean_id:
            return ApiResult(
                ok=False,
                status_code=400,
                error_code="invalid_id",
                error_message="Note ID cannot be empty.",
            )
        encoded_id = urllib.parse.quote(clean_id, safe="")
        res = self.request("POST", f"/api/app/control/notes/{encoded_id}/pin", payload={})
        if res.ok:
            self.active_note_id = clean_id
            for n in self.notes:
                n.pinned = (n.id == clean_id)
        return res

    def unpin_note(self) -> ApiResult:
        res = self.request("POST", "/api/app/control/notes/unpin", payload={})
        if res.ok:
            self.active_note_id = None
            for n in self.notes:
                n.pinned = False
        return res

    # -------------------------------------------------------------------------
    # Countdowns CRUD & Pinning (/api/app/control/countdowns)
    # -------------------------------------------------------------------------
    def _sync_countdowns_from_payload(self, body: Dict[str, Any]) -> List[CountdownItem]:
        raw_list = (
            body.get("countdowns")
            if isinstance(body.get("countdowns"), list)
            else body.get("data", [])
        )
        if not isinstance(raw_list, list):
            raw_list = []
        active_id = body.get("active_station_countdown_id") or body.get("active_countdown_id")
        self.active_countdown_id = str(active_id) if active_id else None
        self.countdowns = [
            CountdownItem.from_dict(item, active_id=self.active_countdown_id)
            for item in raw_list
            if isinstance(item, dict)
        ]
        return list(self.countdowns)

    def get_countdowns(self) -> ApiResult:
        res = self.request("GET", "/api/app/control/countdowns")
        if res.ok and isinstance(res.data, dict):
            self._sync_countdowns_from_payload(res.data)
        return res

    def create_countdown(self, name: str, target_date: str) -> ApiResult:
        clean_name = (name or "").strip()
        clean_date = (target_date or "").strip()
        if not clean_name or not clean_date:
            return ApiResult(
                ok=False,
                status_code=400,
                error_code="invalid_countdown",
                error_message="Event name and target date are required.",
            )
        res = self.request(
            "POST",
            "/api/app/control/countdowns",
            payload={"name": clean_name, "title": clean_name, "target_date": clean_date},
        )
        if res.ok and isinstance(res.data, dict):
            self._sync_countdowns_from_payload(res.data)
        return res

    def delete_countdown(self, countdown_id: str) -> ApiResult:
        clean_id = (countdown_id or "").strip()
        if not clean_id:
            return ApiResult(
                ok=False,
                status_code=400,
                error_code="invalid_id",
                error_message="Countdown ID cannot be empty.",
            )
        if clean_id in self._in_flight_countdown_deletes:
            return ApiResult(
                ok=False,
                status_code=409,
                error_code="duplicate_request",
                error_message="Deletion already in progress for this countdown.",
            )
        self._in_flight_countdown_deletes.add(clean_id)
        try:
            encoded_id = urllib.parse.quote(clean_id, safe="")
            res = self.request("DELETE", f"/api/app/control/countdowns/{encoded_id}")
            if res.ok and isinstance(res.data, dict):
                self._sync_countdowns_from_payload(res.data)
            return res
        finally:
            self._in_flight_countdown_deletes.discard(clean_id)

    def pin_countdown(self, countdown_id: str) -> ApiResult:
        clean_id = (countdown_id or "").strip()
        encoded_id = urllib.parse.quote(clean_id, safe="")
        res = self.request("POST", f"/api/app/control/countdowns/{encoded_id}/pin", payload={})
        if res.ok:
            self.active_countdown_id = clean_id
            for c in self.countdowns:
                c.pinned = (c.id == clean_id)
        return res

    # -------------------------------------------------------------------------
    # Weather Forecast
    # -------------------------------------------------------------------------
    def get_weather_forecast(self, city: Optional[str] = None) -> Tuple[ApiResult, List[WeatherInterval]]:
        path = "/api/app/control/weather/forecast"
        if city and city.strip():
            path += f"?city={urllib.parse.quote(city.strip())}"
        res = self.request("GET", path)
        intervals: List[WeatherInterval] = []
        if res.ok and isinstance(res.data, dict):
            raw_list = res.data.get("intervals")
            if not isinstance(raw_list, list) and isinstance(res.data.get("data"), dict):
                raw_list = res.data["data"].get("intervals")
            if isinstance(raw_list, list):
                intervals = [
                    WeatherInterval.from_dict(item)
                    for item in raw_list
                    if isinstance(item, dict)
                ]
        return res, intervals

    # -------------------------------------------------------------------------
    # Morse Code & Buzzer
    # -------------------------------------------------------------------------
    def play_morse(self, pattern: str, unit_ms: int = 60, freq: int = 1000) -> ApiResult:
        return self.request(
            "POST",
            "/api/app/control/morse/play",
            payload={
                "pattern": (pattern or "").strip(),
                "unit_ms": max(30, min(400, int(unit_ms))),
                "freq": max(400, min(5000, int(freq))),
            },
        )

    def stop_morse(self) -> ApiResult:
        return self.request("POST", "/api/app/control/morse/stop", payload={})

    # -------------------------------------------------------------------------
    # ESP32 Station Telemetry, Display Views, Clock Style, LEDs, Spotify & Timetable
    # -------------------------------------------------------------------------
    def get_system_status(self) -> ApiResult:
        return self.request("GET", "/api/app/control/status")

    CLOCK_STYLE_ID_TO_CODE: Dict[int, str] = {
        1: "digital_classic",
        2: "digital_minimal",
        3: "digital_neon",
        4: "analog",
        5: "flip",
        6: "binary",
        7: "word",
    }

    def get_melody_presets(self) -> ApiResult:
        return self.request("GET", "/api/app/control/melody/presets")

    def play_melody_preset(self, preset_id: str) -> ApiResult:
        return self.request(
            "POST",
            "/api/app/control/melody/play",
            payload={"preset_id": preset_id},
        )

    def play_melody_file(self, filename: str, midi_bytes: bytes) -> ApiResult:
        """Upload a .mid file and play it on the ESP32 buzzer."""
        import base64

        return self.request(
            "POST",
            "/api/app/control/melody/play",
            payload={
                "midi_base64": base64.b64encode(midi_bytes).decode("ascii"),
                "filename": filename,
                "title": filename.rsplit(".", 1)[0],
            },
        )

    def stop_melody(self) -> ApiResult:
        return self.request("POST", "/api/app/control/melody/stop", payload={})

    def switch_display_view(self, view: str) -> ApiResult:
        # Canonical route first. Older deployments only expose /switch-view, and
        # some proxies answer 405 instead of 404, so retry on both.
        res = self.request(
            "POST",
            "/api/app/control/display/view",
            payload={"view": view},
            authenticated=False,
        )
        if not res.ok and res.status_code in (404, 405):
            res = self.request(
                "POST",
                "/api/app/control/display/switch-view",
                payload={"view": view},
                authenticated=False,
            )
        return res

    def get_clock_config(self) -> ApiResult:
        """Fetch current ESP32 clock/OLED config (including language) from backend."""
        return self.request(
            "GET",
            "/api/app/control/clock-style",
            authenticated=False,
        )

    def set_clock_language(self, language: str) -> ApiResult:
        """Set the ESP32 clock/OLED display language ("vi" or "en")."""
        lang = "en" if str(language).strip().lower().startswith("en") else "vi"
        return self.request(
            "POST",
            "/api/app/control/clock-style",
            payload={"language": lang},
            authenticated=False,
        )

    def set_clock_style(self, style: Any, accent_color: Optional[str] = None) -> ApiResult:
        if isinstance(style, int) or (isinstance(style, str) and style.isdigit()):
            code = self.CLOCK_STYLE_ID_TO_CODE.get(int(style), "digital_classic")
        else:
            code = str(style).strip()
        payload: Dict[str, Any] = {"style": code}
        if accent_color:
            payload["accent_color"] = str(accent_color).strip()
        return self.request(
            "POST",
            "/api/app/control/clock-style",
            payload=payload,
            authenticated=False,
        )

    def set_clock_accent_color(self, accent_color: str, style: Optional[Any] = None) -> ApiResult:
        payload: Dict[str, Any] = {"accent_color": str(accent_color).strip()}
        if style is not None:
            if isinstance(style, int) or (isinstance(style, str) and style.isdigit()):
                code = self.CLOCK_STYLE_ID_TO_CODE.get(int(style), "digital_classic")
            else:
                code = str(style).strip()
            payload["style"] = code
        return self.request(
            "POST",
            "/api/app/control/clock-style",
            payload=payload,
            authenticated=False,
        )

    def set_lamp_color(self, hex_color: str) -> ApiResult:
        return self.request("POST", "/api/app/control/lamp/color", payload={"hex": hex_color})

    def set_lamp_dim(self, led_bright: int) -> ApiResult:
        """LED ring brightness only (0-100%). The OLED backlight is fixed."""
        return self.request(
            "POST",
            "/api/app/control/lamp/dim",
            payload={"led": max(0, min(100, int(led_bright)))},
        )

    def set_led(self, action: str) -> ApiResult:
        """Explicit LED ring on/off ("on", "off", "toggle", "blink")."""
        return self.request("POST", "/api/app/control/led", payload={"action": action})

    def turn_lamp_off(self) -> ApiResult:
        return self.request("POST", "/api/app/control/lamp/off", payload={})

    def trigger_buzz(self, duration_ms: int = 300) -> ApiResult:
        return self.request(
            "POST",
            "/api/app/control/alarm/buzz",
            payload={"duration": max(50, min(5000, int(duration_ms)))},
        )

    def get_timetable_today(self) -> Tuple[ApiResult, Optional[DualDayTimetable]]:
        res = self.request("GET", "/api/app/control/timetable/today")
        tt: Optional[DualDayTimetable] = None
        if res.ok and isinstance(res.data, dict):
            raw_data = res.data.get("data") if ("data" in res.data and isinstance(res.data["data"], dict)) else res.data
            if isinstance(raw_data, dict):
                tt = DualDayTimetable.from_dict(raw_data)
        return res, tt

    def get_spotify_status(self) -> ApiResult:
        return self.request("GET", "/api/app/spotify/status")

    def control_spotify(self, command: str) -> ApiResult:
        return self.request("POST", "/api/app/spotify/playback", payload={"command": command})

    def set_spotify_volume(self, volume: int) -> ApiResult:
        return self.request(
            "POST",
            "/api/app/spotify/volume",
            payload={"volume": max(0, min(100, int(volume)))},
        )

    def report_telemetry(
        self,
        cpu: float,
        ram_pct: float,
        battery: int,
        temp: float = 0.0,
        gpu: float = 0.0,
    ) -> ApiResult:
        cfg = self.config_store.config
        return self.request(
            "POST",
            "/api/app/control/telemetry",
            payload={
                "cpu": float(cpu),
                "ram_pct": float(ram_pct),
                "battery": int(battery),
                "temp": float(temp),
                "gpu": float(gpu),
                "device_id": cfg.device_id or "linux_workstation",
                "device_name": cfg.device_name or "DeskFlow Linux",
                "timestamp": time.time(),
            },
            authenticated=bool(cfg.device_token),
        )

    def get_station_status(self) -> ApiResult:
        cfg = self.config_store.config
        return self.request(
            "GET",
            "/api/app/control/status",
            authenticated=bool(cfg.device_token),
        )

    def generate_quote(self, topic: str = "học tập và kiên trì", limit: int = 75, pin: bool = False) -> ApiResult:
        return self.request(
            "POST",
            "/api/app/control/quote/generate",
            payload={"topic": topic, "limit": limit, "pin": pin},
        )

    def set_esp32_theme(self, mode: str, start: str = "22:00", end: str = "06:00") -> ApiResult:
        return self.request(
            "POST",
            "/api/app/control/settings",
            payload={
                "darkmode_mode": mode,
                "darkmode_start": start,
                "darkmode_end": end,
            },
        )

    # -------------------------------------------------------------------------
    # Google Calendar (/api/app/calendar/*)
    # -------------------------------------------------------------------------
    def _sync_calendar_from_payload(self, body: Any) -> CalendarStatus:
        if isinstance(body, dict):
            self.calendar_status = CalendarStatus.from_dict(body)
        return self.calendar_status

    def get_calendar_status(self) -> ApiResult:
        res = self.request("GET", "/api/app/calendar/status")
        if res.ok:
            self._sync_calendar_from_payload(res.data)
        return res

    def sync_calendar(self) -> ApiResult:
        res = self.request("POST", "/api/app/calendar/sync", payload={})
        if res.ok:
            self._sync_calendar_from_payload(res.data)
        return res

    def update_calendar_config(
        self,
        ical_url: Optional[str] = None,
        google_client_id: Optional[str] = None,
        google_client_secret: Optional[str] = None,
        google_redirect_uri: Optional[str] = None,
        selected_calendar_id: Optional[str] = None,
    ) -> ApiResult:
        payload: Dict[str, Any] = {}
        if ical_url is not None:
            payload["ical_url"] = ical_url
        if google_client_id is not None:
            payload["google_client_id"] = google_client_id
        if google_client_secret is not None:
            payload["google_client_secret"] = google_client_secret
        if google_redirect_uri is not None:
            payload["google_redirect_uri"] = google_redirect_uri
        if selected_calendar_id is not None:
            payload["selected_calendar_id"] = selected_calendar_id
        res = self.request("POST", "/api/app/calendar/config", payload=payload)
        if res.ok:
            self._sync_calendar_from_payload(res.data)
        return res

    def start_calendar_oauth(self) -> ApiResult:
        return self.request("POST", "/api/app/calendar/oauth/start", payload={})

    def disconnect_calendar_oauth(self) -> ApiResult:
        res = self.request("POST", "/api/app/calendar/oauth/disconnect", payload={})
        if res.ok:
            self._sync_calendar_from_payload(res.data)
        return res

    def create_calendar_event(
        self,
        title: str,
        date_str: str,
        start_time: str = "",
        end_time: str = "",
        all_day: bool = False,
        location: str = "",
        description: str = "",
        calendar_id: Optional[str] = None,
    ) -> ApiResult:
        payload: Dict[str, Any] = {
            "title": title,
            "date": date_str,
            "start_time": start_time,
            "end_time": end_time,
            "all_day": all_day,
            "location": location,
            "description": description,
        }
        if calendar_id:
            payload["calendar_id"] = calendar_id
        res = self.request("POST", "/api/app/calendar/events", payload=payload)
        if res.ok:
            self._sync_calendar_from_payload(res.data)
        return res

    def delete_calendar_event(self, event_id: str, calendar_id: Optional[str] = None) -> ApiResult:
        encoded = urllib.parse.quote(event_id, safe="")
        q = f"?calendar_id={urllib.parse.quote(calendar_id, safe='')}" if calendar_id else ""
        res = self.request("DELETE", f"/api/app/calendar/events/{encoded}{q}")
        if res.ok:
            self._sync_calendar_from_payload(res.data)
        return res
