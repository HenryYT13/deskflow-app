using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using DeskFlow.Models;

namespace DeskFlow.Services;

public sealed class DeskFlowApiClient
{
    private readonly HttpClient _http;
    public WindowsAppSettings Settings { get; }
    public List<NoteItemDto> Notes { get; private set; } = new();
    public List<CountdownItemDto> Countdowns { get; private set; } = new();
    public List<WeatherIntervalDto> WeatherIntervals { get; private set; } = new();
    private readonly HashSet<string> _inFlightNoteDeletes = new();

    public static readonly IReadOnlyDictionary<string, string> ErrorCodeMessages =
        new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            ["pair_code_invalid"] = "Invalid, expired, or already-used pairing code. Generate a new code in Discord (/pair).",
            ["invalid_request"] = "Invalid request format or endpoint rejected by backend.",
            ["rate_limited"] = "Too many attempts. Please wait a minute before trying again.",
            ["insufficient_scope"] = "This device was paired without 'control' permission, so notes/alarms cannot be changed. Re-pair using a full-access code from Discord (/pair).",
            ["unauthorized"] = "Authentication required or device token expired. Please pair your device.",
            ["token_invalid"] = "Device token is invalid or revoked. Please pair your device again.",
            ["token_expired"] = "Device token expired. Please re-pair your device.",
            ["internal_error"] = "DeskFlow server encountered an internal error."
        };

    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public DeskFlowApiClient(WindowsAppSettings? settings = null, HttpClient? httpClient = null)
    {
        Settings = settings ?? WindowsAppSettings.Load();
        _http = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(10) };
    }

    private static void LogDiagnostic(string message)
    {
        Console.Error.WriteLine($"[{DateTime.UtcNow:O}] [DeskFlow.Windows] {message}");
    }

    private static (string ErrorCode, string ErrorMessage) ExtractErrorInfo(string body, int statusCode)
    {
        string errorCode = $"http_{statusCode}";
        string errorMessage = $"HTTP {statusCode}";

        if (string.IsNullOrWhiteSpace(body))
            return (errorCode, errorMessage);

        try
        {
            using var doc = JsonDocument.Parse(body);
            var root = doc.RootElement;
            if (root.ValueKind == JsonValueKind.Object)
            {
                if (root.TryGetProperty("error", out var errProp))
                {
                    if (errProp.ValueKind == JsonValueKind.Object)
                    {
                        if (errProp.TryGetProperty("code", out var codeEl) && codeEl.ValueKind == JsonValueKind.String)
                            errorCode = codeEl.GetString() ?? errorCode;
                        if (errProp.TryGetProperty("message", out var msgEl) && msgEl.ValueKind == JsonValueKind.String)
                        {
                            var m = msgEl.GetString();
                            if (!string.IsNullOrWhiteSpace(m))
                                return (errorCode, m!);
                        }
                    }
                    else if (errProp.ValueKind == JsonValueKind.String)
                    {
                        var errStr = errProp.GetString();
                        if (!string.IsNullOrWhiteSpace(errStr))
                        {
                            errorCode = errStr!;
                            errorMessage = errStr!;
                        }
                    }
                }

                if (root.TryGetProperty("message", out var rootMsg) && rootMsg.ValueKind == JsonValueKind.String)
                {
                    var m = rootMsg.GetString();
                    if (!string.IsNullOrWhiteSpace(m))
                        return (errorCode, m!);
                }

                if (ErrorCodeMessages.TryGetValue(errorCode, out var mapped))
                    return (errorCode, mapped);
            }
        }
        catch (JsonException)
        {
            // Ignore non-JSON error payloads
        }

        return (errorCode, errorMessage);
    }

    private static async Task<string> FailMessageAsync(HttpResponseMessage resp)
    {
        var body = await resp.Content.ReadAsStringAsync();
        var (code, msg) = ExtractErrorInfo(body, (int)resp.StatusCode);
        if (code == "insufficient_scope" && ErrorCodeMessages.TryGetValue(code, out var m)) return m;
        return msg;
    }

    private HttpRequestMessage CreateRequest(
        HttpMethod method,
        string path,
        object? payload = null,
        bool authenticated = true)
    {
        string baseUrl = Settings.ApiUrl.TrimEnd('/');
        string fullUrl = $"{baseUrl}{path}";
        var req = new HttpRequestMessage(method, fullUrl);
        req.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        req.Headers.UserAgent.ParseAdd("DeskFlow-Windows-WinUI3/1.0.0");

        bool hasAuth = authenticated && !string.IsNullOrWhiteSpace(Settings.DeviceToken);
        if (hasAuth)
        {
            req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", Settings.DeviceToken);
        }

        if (payload != null)
        {
            string json = JsonSerializer.Serialize(payload);
            req.Content = new StringContent(json, Encoding.UTF8, "application/json");
        }

        LogDiagnostic($"[HTTP] -> {method.Method} {fullUrl} (auth={(hasAuth ? "bearer" : "none")})");
        return req;
    }

    public async Task<(bool Ok, string Message)> CheckHealthAsync()
    {
        var sw = Stopwatch.StartNew();
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/app/health", authenticated: false);
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            sw.Stop();
            LogDiagnostic($"[HTTP] <- GET /api/app/health status={(int)resp.StatusCode} latency={sw.ElapsedMilliseconds}ms");
            return (resp.IsSuccessStatusCode, $"HTTP {(int)resp.StatusCode} ({sw.ElapsedMilliseconds} ms)");
        }
        catch (Exception ex)
        {
            sw.Stop();
            LogDiagnostic($"[HTTP] !! GET /api/app/health failed after {sw.ElapsedMilliseconds}ms: {ex.Message}");
            return (false, $"Offline ({ex.Message})");
        }
    }

    public async Task<(bool Ok, string Message)> PairDeviceAsync(string code)
    {
        var clean = (code ?? string.Empty).Trim().Replace(" ", "").Replace("-", "").ToUpperInvariant();
        LogDiagnostic($"[Pairing] Validating pairing code (length={clean.Length})");

        if (string.IsNullOrEmpty(clean))
        {
            LogDiagnostic("[Pairing] Validation failed: empty pairing code.");
            return (false, "Pairing code cannot be empty.");
        }

        if (clean.Length < 6 || !clean.All(char.IsLetterOrDigit))
        {
            LogDiagnostic($"[Pairing] Validation failed: invalid format (length={clean.Length}).");
            return (false, "Pairing code must be at least 6 alphanumeric characters.");
        }

        var sw = Stopwatch.StartNew();
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/focus/pair",
                new
                {
                    pairing_code = clean,
                    code = clean,
                    device_name = Settings.DeviceName,
                    platform = "windows",
                    client_version = "1.0.0"
                },
                authenticated: false);
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            sw.Stop();
            var body = await resp.Content.ReadAsStringAsync();
            LogDiagnostic($"[HTTP] <- POST /api/focus/pair status={(int)resp.StatusCode} latency={sw.ElapsedMilliseconds}ms");

            if (!resp.IsSuccessStatusCode)
            {
                var (errCode, errMsg) = ExtractErrorInfo(body, (int)resp.StatusCode);
                LogDiagnostic($"[Pairing] Failed HTTP {(int)resp.StatusCode} code={errCode} msg={errMsg}");
                return (false, errMsg);
            }

            using var doc = JsonDocument.Parse(body);
            if (doc.RootElement.ValueKind == JsonValueKind.Object &&
                doc.RootElement.TryGetProperty("device_token", out var tokEl) &&
                doc.RootElement.TryGetProperty("device_id", out var idEl) &&
                tokEl.ValueKind == JsonValueKind.String &&
                idEl.ValueKind == JsonValueKind.String)
            {
                var token = (tokEl.GetString() ?? string.Empty).Trim();
                var deviceId = (idEl.GetString() ?? string.Empty).Trim();
                if (!string.IsNullOrEmpty(token) && !string.IsNullOrEmpty(deviceId))
                {
                    Settings.DeviceToken = token;
                    Settings.DeviceId = deviceId;
                    Settings.Save();
                    LogDiagnostic($"[Pairing] Successfully paired device_id={deviceId}");
                    return (true, $"Paired device ({deviceId}).");
                }
            }

            LogDiagnostic("[Pairing] Malformed 200 response: missing device_token or device_id.");
            return (false, "Invalid pairing response: missing device_token or device_id.");
        }
        catch (JsonException)
        {
            LogDiagnostic("[Pairing] Malformed JSON in pairing response.");
            return (false, "Invalid pairing response: malformed JSON.");
        }
        catch (Exception ex)
        {
            sw.Stop();
            LogDiagnostic($"[Pairing] Network error after {sw.ElapsedMilliseconds}ms: {ex.Message}");
            return (false, $"Network error: {ex.Message}");
        }
    }

    public async Task<(bool Ok, PairSessionCreateResponse? Data, string Message)> CreatePairSessionAsync(string? deviceName = null, string platform = "windows")
    {
        try
        {
            var name = !string.IsNullOrWhiteSpace(deviceName) ? deviceName.Trim() : Settings.DeviceName;
            using var req = CreateRequest(HttpMethod.Post, "/api/focus/pair/session/create", new { device_name = name, platform = platform }, authenticated: false);
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var raw = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode)
            {
                return (false, null, $"HTTP {(int)resp.StatusCode}");
            }
            var data = JsonSerializer.Deserialize<PairSessionCreateResponse>(raw, JsonOpts);
            return (data != null && data.Success, data, "Session created.");
        }
        catch (Exception ex)
        {
            return (false, null, ex.Message);
        }
    }

    public async Task<(bool Ok, PairSessionStatusResponse? Data, string Message)> GetPairSessionStatusAsync(string sessionId)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, $"/api/focus/pair/session/{Uri.EscapeDataString(sessionId)}", authenticated: false);
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var raw = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode)
            {
                return (false, null, $"HTTP {(int)resp.StatusCode}");
            }
            var data = JsonSerializer.Deserialize<PairSessionStatusResponse>(raw, JsonOpts);
            if (data != null && data.Status == "approved" && !string.IsNullOrWhiteSpace(data.DeviceToken))
            {
                Settings.DeviceToken = data.DeviceToken;
                if (!string.IsNullOrWhiteSpace(data.DeviceId)) Settings.DeviceId = data.DeviceId;
                if (!string.IsNullOrWhiteSpace(data.DeviceName)) Settings.DeviceName = data.DeviceName;
                Settings.Save();
                LogDiagnostic($"[PairSession] Session approved! Saved token & deviceId={Settings.DeviceId}");
            }
            return (true, data, "Status retrieved.");
        }
        catch (Exception ex)
        {
            return (false, null, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> UnpairDeviceAsync()
    {
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/focus/unpair",
                new { device_id = Settings.DeviceId ?? string.Empty });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            Settings.DeviceToken = null;
            Settings.DeviceId = null;
            Settings.Save();
            LogDiagnostic($"[Pairing] Device unpaired locally (server status={(int)resp.StatusCode}).");
            return (true, "Device unpaired locally.");
        }
        catch (Exception ex)
        {
            Settings.DeviceToken = null;
            Settings.DeviceId = null;
            Settings.Save();
            return (true, $"Unpaired locally ({ex.Message}).");
        }
    }

    public async Task<(bool Ok, string Message)> FetchNotesAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/app/control/notes");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
            {
                if ((int)resp.StatusCode == 401)
                {
                    Settings.DeviceToken = null;
                    Settings.DeviceId = null;
                    Settings.Save();
                }
                var (_, errMsg) = ExtractErrorInfo(body, (int)resp.StatusCode);
                return (false, errMsg);
            }

            var parsed = JsonSerializer.Deserialize<NotesListResponse>(body);
            Notes = parsed?.AllNotes ?? new List<NoteItemDto>();
            return (true, $"Loaded {Notes.Count} notes.");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> CreateNoteAsync(string text, string color = "#FDE047")
    {
        var clean = (text ?? string.Empty).Trim();
        if (string.IsNullOrEmpty(clean))
            return (false, "Note content cannot be empty.");

        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/app/control/notes",
                new { text = clean, content = clean, color });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
            {
                var (_, errMsg) = ExtractErrorInfo(body, (int)resp.StatusCode);
                return (false, $"Create note failed ({errMsg})");
            }

            var parsed = JsonSerializer.Deserialize<NotesListResponse>(body);
            Notes = parsed?.AllNotes ?? new List<NoteItemDto>();
            return (true, "Sticky note created.");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    /// <summary>
    /// Authoritative note deletion: never mutates <see cref="Notes"/> before HTTP 200 confirmation,
    /// and properly clears <see cref="Notes"/> when the last note is deleted (<c>notes: []</c>).
    /// </summary>
    public async Task<(bool Ok, string Message)> DeleteNoteAsync(string noteId)
    {
        var cleanId = (noteId ?? string.Empty).Trim();
        if (string.IsNullOrEmpty(cleanId))
            return (false, "Note ID cannot be empty.");

        if (!_inFlightNoteDeletes.Add(cleanId))
            return (false, "Deletion already in progress for this note.");

        try
        {
            var encoded = Uri.EscapeDataString(cleanId);
            using var req = CreateRequest(HttpMethod.Delete, $"/api/app/control/notes/{encoded}");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var body = await resp.Content.ReadAsStringAsync();

            if (!resp.IsSuccessStatusCode)
            {
                if ((int)resp.StatusCode == 401)
                {
                    Settings.DeviceToken = null;
                    Settings.DeviceId = null;
                    Settings.Save();
                }
                var (errCode, errMsg) = ExtractErrorInfo(body, (int)resp.StatusCode);
                if (errCode == "insufficient_scope" && ErrorCodeMessages.TryGetValue(errCode, out var scopeMsg)) errMsg = scopeMsg;
                return (false, $"Delete failed ({errMsg})");
            }

            var parsed = JsonSerializer.Deserialize<NotesListResponse>(body);
            Notes = parsed?.AllNotes ?? new List<NoteItemDto>();
            return (true, "Sticky note deleted.");
        }
        catch (Exception ex)
        {
            return (false, $"Network error: {ex.Message}");
        }
        finally
        {
            _inFlightNoteDeletes.Remove(cleanId);
        }
    }

    public async Task<(bool Ok, string Message)> PinNoteAsync(string noteId)
    {
        var encoded = Uri.EscapeDataString((noteId ?? string.Empty).Trim());
        try
        {
            using var req = CreateRequest(HttpMethod.Post, $"/api/app/control/notes/{encoded}/pin", new { });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (resp.IsSuccessStatusCode)
            {
                await FetchNotesAsync();
                return (true, "Pinned note to ESP32 OLED.");
            }
            return (false, $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> FetchCountdownsAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/app/control/countdowns");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
                return (false, $"HTTP {(int)resp.StatusCode}");

            var parsed = JsonSerializer.Deserialize<CountdownsListResponse>(body);
            Countdowns = parsed?.AllCountdowns ?? new List<CountdownItemDto>();
            return (true, $"Loaded {Countdowns.Count} countdowns.");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> CreateCountdownAsync(string name, string targetDate)
    {
        var cleanName = (name ?? string.Empty).Trim();
        var cleanDate = (targetDate ?? string.Empty).Trim();
        if (string.IsNullOrEmpty(cleanName) || string.IsNullOrEmpty(cleanDate))
            return (false, "Countdown name and target date (YYYY-MM-DD) are required.");

        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/app/control/countdowns",
                new { name = cleanName, title = cleanName, target_date = cleanDate });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode)
                return (false, $"HTTP {(int)resp.StatusCode}");

            await FetchCountdownsAsync();
            return (true, $"Created countdown '{cleanName}'.");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> DeleteCountdownAsync(string countdownId)
    {
        var encoded = Uri.EscapeDataString((countdownId ?? string.Empty).Trim());
        try
        {
            using var req = CreateRequest(HttpMethod.Delete, $"/api/app/control/countdowns/{encoded}");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode)
                return (false, $"HTTP {(int)resp.StatusCode}");

            await FetchCountdownsAsync();
            return (true, "Deleted countdown.");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> FetchWeatherForecastAsync(string? city = null)
    {
        string path = "/api/app/control/weather/forecast";
        if (!string.IsNullOrWhiteSpace(city))
            path += $"?city={Uri.EscapeDataString(city.Trim())}";

        try
        {
            using var req = CreateRequest(HttpMethod.Get, path);
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
                return (false, $"HTTP {(int)resp.StatusCode}");

            var parsed = JsonSerializer.Deserialize<WeatherForecastResponse>(body);
            WeatherIntervals = parsed?.AllIntervals ?? new List<WeatherIntervalDto>();
            return (true, $"Loaded {WeatherIntervals.Count} forecast intervals.");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> PlayMorseAsync(string pattern, int unitMs = 60, int freq = 1000)
    {
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/app/control/morse/play",
                new { pattern, unit_ms = unitMs, freq });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Morse sent to ESP32." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> StopMorseAsync()
    {
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/app/control/morse/stop",
                new { });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Morse playback stopped." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> PlayMelodyPresetAsync(string presetId)
    {
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/app/control/melody/play",
                new { preset_id = presetId });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? (Settings.Language == "vi" ? "Đang phát nhạc trên ESP32." : "Playing melody on ESP32.") : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> PlayMidiFileAsync(byte[] bytes, string filename)
    {
        try
        {
            string b64 = Convert.ToBase64String(bytes);
            string title = System.IO.Path.GetFileNameWithoutExtension(filename);
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/app/control/melody/play",
                new { midi_base64 = b64, filename, title });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? (Settings.Language == "vi" ? $"Đang phát file MIDI '{filename}' trên ESP32." : $"Playing MIDI '{filename}' on ESP32.") : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> StopMelodyAsync()
    {
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/app/control/melody/stop",
                new { });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? (Settings.Language == "vi" ? "Đã dừng phát nhạc trên ESP32." : "Stopped melody playback.") : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public static readonly Dictionary<int, string> ClockStyleIdToCode = new()
    {
        [1] = "digital_classic",
        [2] = "digital_minimal",
        [3] = "digital_neon",
        [4] = "analog",
        [5] = "flip",
        [6] = "binary",
        [7] = "word"
    };

    public async Task<(bool Ok, string Message)> SwitchDisplayViewAsync(string view)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/display/switch-view", new { view }, authenticated: false);
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode && resp.StatusCode == HttpStatusCode.NotFound)
            {
                using var reqFallback = CreateRequest(HttpMethod.Post, "/api/app/control/display/view", new { view }, authenticated: false);
                using var respFallback = await _http.SendAsync(reqFallback).ConfigureAwait(false);
                return (respFallback.IsSuccessStatusCode, respFallback.IsSuccessStatusCode ? $"Switched display view to '{view}'." : $"HTTP {(int)respFallback.StatusCode}");
            }
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? $"Switched display view to '{view}'." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> SetClockStyleAsync(string styleCode, string? accentColor = null)
    {
        try
        {
            string code = string.IsNullOrWhiteSpace(styleCode) ? "digital_classic" : styleCode.Trim();
            object payload = !string.IsNullOrWhiteSpace(accentColor)
                ? new { style = code, accent_color = accentColor.Trim() }
                : new { style = code };

            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/clock-style", payload, authenticated: false);
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? $"Clock style '{code}' activated." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public Task<(bool Ok, string Message)> SetClockStyleAsync(int style, string? accentColor = null)
    {
        string code = ClockStyleIdToCode.TryGetValue(style, out var mapped) ? mapped : "digital_classic";
        return SetClockStyleAsync(code, accentColor);
    }

    public async Task<(bool Ok, string Message)> SetClockAccentColorAsync(string accentColor, string? styleCode = null)
    {
        try
        {
            object payload = !string.IsNullOrWhiteSpace(styleCode)
                ? new { accent_color = accentColor.Trim(), style = styleCode.Trim() }
                : new { accent_color = accentColor.Trim() };

            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/clock-style", payload, authenticated: false);
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? $"Clock accent color '{accentColor}' applied." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, DualDayTimetableData? Data, string Message)> GetTimetableTodayAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/app/control/timetable/today");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var raw = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode)
            {
                return (false, null, $"HTTP {(int)resp.StatusCode}");
            }
            var result = JsonSerializer.Deserialize<TimetableTodayResponse>(raw, JsonOpts);
            return (result != null && result.Success, result?.Data, "Loaded timetable.");
        }
        catch (Exception ex)
        {
            return (false, null, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> TriggerBuzzAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/alarm/buzz", new { });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Triggered ESP32 buzzer." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> SetBrightnessAsync(int led)
    {
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/app/control/lamp/dim",
                new { led = Math.Clamp(led, 0, 100) });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Updated LED brightness." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> SetFocusModeAsync(bool enabled, int durationMinutes = 25)
    {
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/focus/enable",
                new { enabled, duration_minutes = durationMinutes, source = "windows_app" });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? (enabled ? "Focus Mode started." : "Focus Mode stopped.") : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    /// <summary>Reads the authoritative focus state from the backend.</summary>
    public async Task<(bool Ok, bool Active)> GetFocusStatusAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/focus/status");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode)
                return (false, false);
            string body = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
            return (true, FocusLockPolicy.IsFocusActive(body));
        }
        catch (Exception ex)
        {
            return (false, false);
        }
    }

    /// <summary>Asks the backend for a math challenge to unlock Focus Mode early.</summary>
    public async Task<(bool Ok, string ChallengeId, string Question, string Message)> RequestFocusChallengeAsync()
    {
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/focus/challenge",
                new { language = "vi", force_new = true });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            string body = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode)
                return (false, string.Empty, string.Empty, $"HTTP {(int)resp.StatusCode}");

            using var doc = JsonDocument.Parse(body);
            string id = ReadString(doc.RootElement, "challenge_id");
            string question = ReadString(doc.RootElement, "question");
            return (true, id, question, "Math challenge ready.");
        }
        catch (Exception ex)
        {
            return (false, string.Empty, string.Empty, ex.Message);
        }
    }

    /// <summary>Verifies the answer; a correct answer unlocks the session server-side.</summary>
    public async Task<(bool Ok, bool Correct, string NewId, string NewQuestion, string Message)> VerifyFocusChallengeAsync(
        string challengeId, int answer)
    {
        try
        {
            using var req = CreateRequest(
                HttpMethod.Post,
                "/api/focus/verify",
                new { challenge_id = challengeId, answer, language = "vi" });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            string body = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode)
                return (false, false, string.Empty, string.Empty, $"HTTP {(int)resp.StatusCode}");

            using var doc = JsonDocument.Parse(body);
            bool correct = doc.RootElement.TryGetProperty("correct", out var c)
                && c.ValueKind == JsonValueKind.True;

            string newId = string.Empty;
            string newQuestion = string.Empty;
            if (doc.RootElement.TryGetProperty("new_challenge", out var next)
                && next.ValueKind == JsonValueKind.Object)
            {
                newId = ReadString(next, "challenge_id");
                newQuestion = ReadString(next, "question");
            }

            return (true, correct, newId, newQuestion,
                correct ? "Correct! Focus Mode unlocked." : "Incorrect answer.");
        }
        catch (Exception ex)
        {
            return (false, false, string.Empty, string.Empty, ex.Message);
        }
    }

    private static string ReadString(JsonElement root, string property)
    {
        if (root.TryGetProperty(property, out var value) && value.ValueKind == JsonValueKind.String)
            return value.GetString() ?? string.Empty;
        return string.Empty;
    }

    public async Task<(bool Ok, string Message)> TriggerBuzzAsync(int durationMs = 40)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/alarm/buzz", new { duration = Math.Clamp(durationMs, 10, 5000) });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? $"Buzzer triggered ({durationMs}ms)." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> EmergencyStopAlarmAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/alarm/emergency-stop", new { });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Alarm silenced." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> SetLampColorAsync(string hex)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/lamp/color", new { hex = hex.Trim() });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? $"Lamp color set to {hex}." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> SetLedAsync(string action)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/led", new { action });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? $"LED {action}." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> TurnLampOffAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/lamp/off", new { });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Lamp turned off." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, List<AlarmItemDto> Alarms, string Message)> GetAlarmsAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/app/control/alarms");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
                return (false, new List<AlarmItemDto>(), $"HTTP {(int)resp.StatusCode}");
            var res = JsonSerializer.Deserialize<AlarmsListResponse>(body);
            return (true, res?.Alarms ?? new List<AlarmItemDto>(), "Loaded alarms.");
        }
        catch (Exception ex)
        {
            return (false, new List<AlarmItemDto>(), ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> SaveAlarmAsync(string time, string label, List<int> repeatDays, int buzzerDuration = 250, string? alarmId = null)
    {
        try
        {
            var data = new
            {
                time,
                label,
                repeat_days = repeatDays,
                buzzer_duration = buzzerDuration,
                math_challenge = true
            };
            using var req = CreateRequest(alarmId != null ? HttpMethod.Put : HttpMethod.Post, alarmId != null ? $"/api/app/control/alarms/{Uri.EscapeDataString(alarmId)}" : "/api/app/control/alarms", data);
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? $"Alarm {time} saved." : await FailMessageAsync(resp));
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> DeleteAlarmAsync(string alarmId)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Delete, $"/api/app/control/alarms/{Uri.EscapeDataString(alarmId)}");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Alarm deleted." : await FailMessageAsync(resp));
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> ToggleAlarmAsync(string alarmId, bool? enabled = null)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, $"/api/app/control/alarms/{alarmId}/toggle", enabled.HasValue ? new { enabled = enabled.Value } : new { });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Alarm toggled." : await FailMessageAsync(resp));
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, List<PairedDeviceItemDto> Devices, string Message)> GetPairedDevicesAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/focus/devices");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            var body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
                return (false, new List<PairedDeviceItemDto>(), $"HTTP {(int)resp.StatusCode}");
            var res = JsonSerializer.Deserialize<DevicesListResponse>(body);
            return (true, res?.Devices ?? new List<PairedDeviceItemDto>(), "Loaded devices.");
        }
        catch (Exception ex)
        {
            return (false, new List<PairedDeviceItemDto>(), ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> RenameDeviceAsync(string deviceId, string newName)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, $"/api/focus/devices/{deviceId}/rename", new { name = newName });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? $"Device renamed to {newName}." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Ok, string Message)> UnpairSpecificDeviceAsync(string deviceId)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/focus/unpair", new { device_id = deviceId });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (deviceId == Settings.DeviceId)
            {
                Settings.DeviceToken = null;
                Settings.DeviceId = null;
                Settings.Save();
            }
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Device unlinked." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<string?> GetSelectedDisplayDeviceAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/app/control/device/selected");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return null;
            var body = await resp.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(body);
            return doc.RootElement.TryGetProperty("selected_device_id", out var p) ? p.GetString() : null;
        }
        catch
        {
            return null;
        }
    }

    public async Task<(bool Ok, string Message)> SetSelectedDisplayDeviceAsync(string deviceId)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/device/selected", new { device_id = deviceId });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "Selected display device updated." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task ReportTelemetryAsync(double cpu, double ram, int battery)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/telemetry", new
            {
                cpu = cpu,
                ram_pct = ram,
                battery = battery,
                device_id = Settings.DeviceId ?? "win_workstation",
                device_name = Settings.DeviceName ?? "Windows PC",
                timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
            });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
        }
        catch {}
    }

    public async Task<StationStatusData?> GetStationStatusAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/app/control/status");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return null;
            var json = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.TryGetProperty("data", out var dataEl))
            {
                return JsonSerializer.Deserialize<StationStatusData>(dataEl.GetRawText(), JsonOpts);
            }
        }
        catch {}
        return null;
    }

    public async Task<(bool Ok, string Quote, string Author)> GenerateQuoteAsync(string topic = "học tập và kiên trì", int limit = 75)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/quote/generate", new { topic = topic, limit = limit });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return (false, "", "");
            var body = await resp.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(body);
            var q = doc.RootElement.TryGetProperty("quote", out var qProp) ? qProp.GetString() ?? "" : "";
            var a = doc.RootElement.TryGetProperty("author", out var aProp) ? aProp.GetString() ?? "Groq AI" : "Groq AI";
            return (true, q, a);
        }
        catch
        {
            return (false, "", "");
        }
    }

    public async Task<(bool Ok, string Message)> SetEsp32ThemeAsync(string mode, string start = "22:00", string end = "06:00")
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/control/settings", new
            {
                darkmode_mode = mode,
                darkmode_start = start,
                darkmode_end = end
            });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return (resp.IsSuccessStatusCode, resp.IsSuccessStatusCode ? "ESP32 display appearance updated." : $"HTTP {(int)resp.StatusCode}");
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<SpotifyStatusDto?> GetSpotifyStatusAsync()
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Get, "/api/app/spotify/status");
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return null;
            var json = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.TryGetProperty("data", out var dataEl))
            {
                return JsonSerializer.Deserialize<SpotifyStatusDto>(dataEl.GetRawText(), JsonOpts);
            }
            return JsonSerializer.Deserialize<SpotifyStatusDto>(json, JsonOpts);
        }
        catch
        {
            return null;
        }
    }

    public async Task<bool> ControlSpotifyPlaybackAsync(string command)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/spotify/playback", new { command = command });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return resp.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> SetSpotifyVolumeAsync(int volume)
    {
        try
        {
            using var req = CreateRequest(HttpMethod.Post, "/api/app/spotify/volume", new { volume = Math.Clamp(volume, 0, 100) });
            using var resp = await _http.SendAsync(req).ConfigureAwait(false);
            return resp.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }
}
