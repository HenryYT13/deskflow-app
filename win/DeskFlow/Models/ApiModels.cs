using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace DeskFlow.Models;

public static class ScheduledDarkModeEvaluator
{
    public static int ParseMinutes(string? timeStr, int fallbackMinutes)
    {
        if (string.IsNullOrWhiteSpace(timeStr))
            return fallbackMinutes;

        var parts = timeStr.Trim().Split(':');
        if (parts.Length != 2)
            return fallbackMinutes;

        if (!int.TryParse(parts[0], out int hour) || !int.TryParse(parts[1], out int minute))
            return fallbackMinutes;

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59)
            return fallbackMinutes;

        return hour * 60 + minute;
    }

    public static string NormalizeTimeStr(string? raw, string fallback = "20:00")
    {
        int fbMin = ParseMinutes(fallback, 20 * 60);
        int parsed = ParseMinutes(raw, fbMin);
        return $"{parsed / 60:D2}:{parsed % 60:D2}";
    }

    public static string NormalizeTheme(string? raw)
    {
        var clean = (raw ?? string.Empty).Trim().ToLowerInvariant();
        return clean switch
        {
            "light" => "light",
            "dark" => "dark",
            "scheduled" => "scheduled",
            _ => "system"
        };
    }

    public static bool IsScheduledDarkActive(
        int nowMinutes,
        string startStr = "20:00",
        string endStr = "06:00",
        bool fallbackWhenEqual = false)
    {
        int startMin = ParseMinutes(startStr, 20 * 60);
        int endMin = ParseMinutes(endStr, 6 * 60);
        int current = ((nowMinutes % 1440) + 1440) % 1440;

        if (startMin == endMin)
            return fallbackWhenEqual;

        if (startMin < endMin)
        {
            // Same-day window, e.g., 09:00 -> 17:00
            return current >= startMin && current < endMin;
        }

        // Overnight window across midnight, e.g., 20:00 -> 06:00
        return current >= startMin || current < endMin;
    }

    public static bool ResolveDarkTheme(
        string themePreference,
        bool systemIsDark,
        string startStr,
        string endStr,
        int nowMinutes)
    {
        return NormalizeTheme(themePreference) switch
        {
            "light" => false,
            "dark" => true,
            "scheduled" => IsScheduledDarkActive(nowMinutes, startStr, endStr, false),
            _ => systemIsDark
        };
    }
}

public static class MorseCodeConverter
{
    private static readonly Dictionary<char, string> CharToMorse = new()
    {
        ['A'] = ".-", ['B'] = "-...", ['C'] = "-.-.", ['D'] = "-..", ['E'] = ".",
        ['F'] = "..-.", ['G'] = "--.", ['H'] = "....", ['I'] = "..", ['J'] = ".---",
        ['K'] = "-.-", ['L'] = ".-..", ['M'] = "--", ['N'] = "-.", ['O'] = "---",
        ['P'] = ".--.", ['Q'] = "--.-", ['R'] = ".-.", ['S'] = "...", ['T'] = "-",
        ['U'] = "..-", ['V'] = "...-", ['W'] = ".--", ['X'] = "-..-", ['Y'] = "-.--",
        ['Z'] = "--..",
        ['0'] = "-----", ['1'] = ".----", ['2'] = "..---", ['3'] = "...--", ['4'] = "....-",
        ['5'] = ".....", ['6'] = "-....", ['7'] = "--...", ['8'] = "---..", ['9'] = "----.",
        ['.'] = ".-.-.-", [','] = "--..--", ['?'] = "..--..", ['!'] = "-.-.--",
        ['-'] = "-....-", ['/'] = "-..-.", ['@'] = ".--.-."
    };

    private static readonly Dictionary<string, char> MorseToChar =
        CharToMorse.ToDictionary(kv => kv.Value, kv => kv.Key);

    public static string StripDiacritics(string text)
    {
        if (string.IsNullOrEmpty(text)) return string.Empty;
        string replaced = text.Replace('đ', 'd').Replace('Đ', 'D');
        string normalized = replaced.Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder(normalized.Length);
        foreach (char c in normalized)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark)
                sb.Append(c);
        }
        return sb.ToString().Normalize(NormalizationForm.FormC);
    }

    public static string TextToMorse(string text)
    {
        string clean = StripDiacritics(text ?? string.Empty).ToUpperInvariant().Trim();
        if (string.IsNullOrEmpty(clean)) return string.Empty;

        var words = clean.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        var encodedWords = new List<string>();
        foreach (var word in words)
        {
            var letters = word
                .Where(ch => CharToMorse.ContainsKey(ch))
                .Select(ch => CharToMorse[ch])
                .ToList();
            if (letters.Count > 0)
                encodedWords.Add(string.Join(" ", letters));
        }
        return string.Join(" / ", encodedWords);
    }

    public static string MorseToText(string morse)
    {
        if (string.IsNullOrWhiteSpace(morse)) return string.Empty;
        var words = morse.Replace('|', '/').Split('/', StringSplitOptions.RemoveEmptyEntries);
        var decodedWords = new List<string>();
        foreach (var wordSlice in words)
        {
            var tokens = wordSlice.Trim().Split(' ', StringSplitOptions.RemoveEmptyEntries);
            if (tokens.Length == 0) continue;
            var chars = tokens.Select(tok => MorseToChar.TryGetValue(tok, out char ch) ? ch : '?');
            decodedWords.Add(new string(chars.ToArray()));
        }
        return string.Join(" ", decodedWords);
    }
}

public sealed class WindowsAppSettings
{
    public string ApiUrl { get; set; } = "https://api.10th012013.duckdns.org";
    public string? DeviceToken { get; set; }
    public string? DeviceId { get; set; }
    public string DeviceName { get; set; } = "DeskFlow Windows 11 (WinUI 3)";
    public string Language { get; set; } = "vi";
    public string Esp32Language { get; set; } = "vi";
    public string AppTheme { get; set; } = "system";
    public string DarkModeStart { get; set; } = "20:00";
    public string DarkModeEnd { get; set; } = "06:00";

    [JsonIgnore]
    public bool IsPaired => !string.IsNullOrWhiteSpace(DeviceToken);

    private static string GetSettingsPath()
    {
        var dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "DeskFlow");
        Directory.CreateDirectory(dir);
        return Path.Combine(dir, "settings.json");
    }

    public static WindowsAppSettings Load()
    {
        try
        {
            var path = GetSettingsPath();
            if (!File.Exists(path)) return new WindowsAppSettings();
            var raw = File.ReadAllText(path, Encoding.UTF8);
            var settings = JsonSerializer.Deserialize<WindowsAppSettings>(raw) ?? new WindowsAppSettings();
            if (string.IsNullOrWhiteSpace(settings.ApiUrl) || settings.ApiUrl.Contains("focus-api.10th012013.duckdns.org") || settings.ApiUrl.Contains("mock.deskflow.local"))
            {
                settings.ApiUrl = "https://api.10th012013.duckdns.org";
                settings.Save();
            }
            return settings;
        }
        catch
        {
            return new WindowsAppSettings();
        }
    }

    public void Save()
    {
        AppTheme = ScheduledDarkModeEvaluator.NormalizeTheme(AppTheme);
        DarkModeStart = ScheduledDarkModeEvaluator.NormalizeTimeStr(DarkModeStart, "20:00");
        DarkModeEnd = ScheduledDarkModeEvaluator.NormalizeTimeStr(DarkModeEnd, "06:00");
        ApiUrl = (ApiUrl ?? "https://api.10th012013.duckdns.org").TrimEnd('/');
        try
        {
            var path = GetSettingsPath();
            var json = JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(path, json, Encoding.UTF8);
        }
        catch
        {
            // Ignore write failures in restricted sandboxes
        }
    }
}

public sealed class NoteItemDto
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("content")]
    public string? Content { get; set; }

    [JsonPropertyName("text")]
    public string? Text { get; set; }

    [JsonPropertyName("color")]
    public string Color { get; set; } = "#FDE047";

    [JsonPropertyName("pinned")]
    public bool Pinned { get; set; }

    [JsonPropertyName("is_pinned")]
    public bool IsPinned { get; set; }

    [JsonPropertyName("is_active_on_station")]
    public bool IsActiveOnStation { get; set; }

    [JsonIgnore]
    public string DisplayText => !string.IsNullOrEmpty(Content) ? Content! : (Text ?? string.Empty);

    [JsonIgnore]
    public bool EffectivePinned => Pinned || IsPinned || IsActiveOnStation;
}

public sealed class NotesListResponse
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("notes")]
    public List<NoteItemDto>? Notes { get; set; }

    [JsonPropertyName("data")]
    public List<NoteItemDto>? Data { get; set; }

    [JsonPropertyName("deleted_id")]
    public string? DeletedId { get; set; }

    [JsonIgnore]
    public List<NoteItemDto> AllNotes => Notes ?? Data ?? new List<NoteItemDto>();
}

public sealed class CountdownItemDto
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("title")]
    public string? Title { get; set; }

    [JsonPropertyName("target_date")]
    public string TargetDate { get; set; } = string.Empty;

    [JsonPropertyName("days_left")]
    public int DaysLeft { get; set; }

    [JsonIgnore]
    public string DisplayName => !string.IsNullOrEmpty(Name) ? Name! : (Title ?? string.Empty);
}

public sealed class CountdownsListResponse
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("countdowns")]
    public List<CountdownItemDto>? Countdowns { get; set; }

    [JsonPropertyName("data")]
    public List<CountdownItemDto>? Data { get; set; }

    [JsonIgnore]
    public List<CountdownItemDto> AllCountdowns => Countdowns ?? Data ?? new List<CountdownItemDto>();
}

public sealed class WeatherIntervalDto
{
    [JsonPropertyName("time")]
    public string? Time { get; set; }

    [JsonPropertyName("dt_txt")]
    public string? DtTxt { get; set; }

    [JsonPropertyName("temp")]
    public double Temp { get; set; }

    [JsonPropertyName("description")]
    public string? Description { get; set; }

    [JsonPropertyName("weather_desc")]
    public string? WeatherDesc { get; set; }

    [JsonPropertyName("pop")]
    public double Pop { get; set; }

    [JsonPropertyName("rain_prob")]
    public double? RainProb { get; set; }

    [JsonPropertyName("is_rain")]
    public bool IsRain { get; set; }

    [JsonIgnore]
    public string DisplayTime => !string.IsNullOrEmpty(Time) ? Time! : (DtTxt ?? string.Empty);

    [JsonIgnore]
    public string DisplayDescription => !string.IsNullOrEmpty(Description) ? Description! : (WeatherDesc ?? string.Empty);

    [JsonIgnore]
    public double EffectiveRainProb => RainProb ?? (Pop * 100.0);
}

public sealed class WeatherForecastResponse
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("city")]
    public string? City { get; set; }

    [JsonPropertyName("intervals")]
    public List<WeatherIntervalDto>? Intervals { get; set; }

    [JsonIgnore]
    public List<WeatherIntervalDto> AllIntervals => Intervals ?? new List<WeatherIntervalDto>();
}

public sealed class AlarmItemDto
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("time")]
    public string Time { get; set; } = "07:00";

    [JsonPropertyName("label")]
    public string Label { get; set; } = "Alarm";

    [JsonPropertyName("enabled")]
    public bool Enabled { get; set; } = true;

    [JsonPropertyName("repeat_days")]
    public List<int> RepeatDays { get; set; } = new();

    [JsonPropertyName("buzzer_duration")]
    public int BuzzerDuration { get; set; } = 250;

    [JsonPropertyName("math_challenge")]
    public bool MathChallenge { get; set; } = true;

    [JsonIgnore]
    public string RepeatSummary => RepeatDays.Count == 0 ? "Once" : string.Join(", ", RepeatDays.Select(d => d switch {
        0 => "Mon", 1 => "Tue", 2 => "Wed", 3 => "Thu", 4 => "Fri", 5 => "Sat", 6 => "Sun", _ => "?"
    }));
}

public sealed class AlarmsListResponse
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("alarms")]
    public List<AlarmItemDto>? Alarms { get; set; }
}

public sealed class PairedDeviceItemDto
{
    [JsonPropertyName("device_id")]
    public string DeviceId { get; set; } = string.Empty;

    [JsonPropertyName("device_name")]
    public string DeviceName { get; set; } = "Device";

    [JsonPropertyName("platform")]
    public string Platform { get; set; } = "unknown";

    [JsonPropertyName("is_current")]
    public bool IsCurrent { get; set; }

    [JsonPropertyName("last_seen_at")]
    public string? LastSeenAt { get; set; }
}

public sealed class DevicesListResponse
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("devices")]
    public List<PairedDeviceItemDto>? Devices { get; set; }
}

public sealed class StationStatusData
{
    [JsonPropertyName("station")]
    public StationTelemetry? Station { get; set; }

    [JsonPropertyName("server")]
    public ServerTelemetry? Server { get; set; }

    [JsonPropertyName("computer")]
    public ComputerTelemetry? Computer { get; set; }

    [JsonPropertyName("laptop")]
    public ComputerTelemetry? Laptop { get; set; }
}

public sealed class StationTelemetry
{
    [JsonPropertyName("battery_pct")]
    public int BatteryPct { get; set; } = 100;

    [JsonPropertyName("voltage")]
    public double Voltage { get; set; } = 4.12;

    [JsonPropertyName("wifi_rssi")]
    public int WifiRssi { get; set; } = -55;

    [JsonPropertyName("current_view")]
    public string CurrentView { get; set; } = "main";
}

public sealed class ServerTelemetry
{
    [JsonPropertyName("cpu")]
    public double Cpu { get; set; }

    [JsonPropertyName("ram_pct")]
    public double RamPct { get; set; }

    [JsonPropertyName("disk_str")]
    public string DiskStr { get; set; } = "13.2 / 19.1GB";

    [JsonPropertyName("uptime")]
    public string Uptime { get; set; } = "N/A";
}

public sealed class ComputerTelemetry
{
    [JsonPropertyName("cpu")]
    public double Cpu { get; set; }

    [JsonPropertyName("ram")]
    public double Ram { get; set; }

    [JsonPropertyName("battery")]
    public int Battery { get; set; } = 100;

    [JsonPropertyName("device_name")]
    public string DeviceName { get; set; } = "Windows PC";

    [JsonPropertyName("online")]
    public bool Online { get; set; } = true;
}

public sealed class SpotifyStatusDto
{
    [JsonPropertyName("is_playing")]
    public bool IsPlaying { get; set; }

    [JsonPropertyName("connected")]
    public bool Connected { get; set; }

    [JsonPropertyName("track")]
    public string? Track { get; set; }

    [JsonPropertyName("artist")]
    public string? Artist { get; set; }

    [JsonPropertyName("album")]
    public string? Album { get; set; }

    [JsonPropertyName("progress_ms")]
    public long ProgressMs { get; set; }

    [JsonPropertyName("duration_ms")]
    public long DurationMs { get; set; } = 1;

    [JsonPropertyName("progress_pct")]
    public int ProgressPct { get; set; }

    [JsonPropertyName("lyric_curr")]
    public string? CurrentLyric { get; set; }

    [JsonPropertyName("lyric_next")]
    public string? NextLyric { get; set; }

    [JsonPropertyName("lyrics_window")]
    public List<string> LyricsWindow { get; set; } = new();

    [JsonPropertyName("active_lyric_index")]
    public int ActiveLyricIndex { get; set; } = 2;

    [JsonPropertyName("cover_url")]
    public string? CoverUrl { get; set; }
}

public sealed class SpotifyPlaybackRequest
{
    [JsonPropertyName("command")]
    public string Command { get; set; } = "play";
}

public sealed class SpotifyVolumeRequest
{
    [JsonPropertyName("volume")]
    public int Volume { get; set; } = 50;
}

public sealed class PairSessionCreateRequest
{
    [JsonPropertyName("device_name")]
    public string DeviceName { get; set; } = "DeskFlow Windows";

    [JsonPropertyName("platform")]
    public string Platform { get; set; } = "windows";
}

public sealed class PairSessionCreateResponse
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("session_id")]
    public string? SessionId { get; set; }

    [JsonPropertyName("qr_payload")]
    public string? QrPayload { get; set; }

    [JsonPropertyName("qr_image_url")]
    public string? QrImageUrl { get; set; }

    [JsonPropertyName("qr_data_url")]
    public string? QrDataUrl { get; set; }

    [JsonPropertyName("expires_in")]
    public int ExpiresIn { get; set; } = 600;
}

public sealed class PairSessionStatusResponse
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("status")]
    public string Status { get; set; } = "pending";

    [JsonPropertyName("device_id")]
    public string? DeviceId { get; set; }

    [JsonPropertyName("device_token")]
    public string? DeviceToken { get; set; }

    [JsonPropertyName("device_name")]
    public string? DeviceName { get; set; }

    [JsonPropertyName("scopes")]
    public List<string>? Scopes { get; set; }
}

public sealed class TimetableDayDto
{
    [JsonPropertyName("day")]
    public string Day { get; set; } = string.Empty;

    [JsonPropertyName("display_day")]
    public string? DisplayDay { get; set; }

    [JsonPropertyName("morning")]
    public List<string> Morning { get; set; } = new();

    [JsonPropertyName("dp_sang")]
    public string DpSang { get; set; } = "Tự do";

    [JsonPropertyName("has_afternoon")]
    public bool HasAfternoon { get; set; }

    [JsonPropertyName("afternoon")]
    public List<string> Afternoon { get; set; } = new();

    [JsonPropertyName("dp_chieu")]
    public string? DpChieu { get; set; }

    [JsonPropertyName("has_any_class")]
    public bool HasAnyClass { get; set; }

    [JsonIgnore]
    public string MorningSummary => Morning.Count > 0 ? string.Join(" • ", Morning) : "Nghỉ sáng";

    [JsonIgnore]
    public string AfternoonSummary => (HasAfternoon && Afternoon.Count > 0) ? string.Join(" • ", Afternoon) : "Không có tiết chiều";
}

public sealed class DualDayTimetableData
{
    // PocketBase stores week_number as a number for some weeks and as a string
    // for others, which made System.Text.Json throw
    // "The JSON value could not be converted to System.String".
    [JsonPropertyName("week_number")]
    [JsonConverter(typeof(FlexibleStringConverter))]
    public string WeekNumber { get; set; } = "N/A";

    [JsonPropertyName("today")]
    public TimetableDayDto? Today { get; set; }

    [JsonPropertyName("tomorrow")]
    public TimetableDayDto? Tomorrow { get; set; }
}

public sealed class TimetableTodayResponse
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("data")]
    public DualDayTimetableData? Data { get; set; }
}

/// <summary>
/// Reads a JSON string, number or bool into a string so mixed-type fields
/// (e.g. PocketBase week_number) never break deserialisation.
/// </summary>
public sealed class FlexibleStringConverter : JsonConverter<string>
{
    public override string Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        return reader.TokenType switch
        {
            JsonTokenType.String => reader.GetString() ?? string.Empty,
            JsonTokenType.Number => reader.TryGetInt64(out var l) ? l.ToString() : reader.GetDouble().ToString("0.##"),
            JsonTokenType.True => "true",
            JsonTokenType.False => "false",
            JsonTokenType.Null => string.Empty,
            _ => string.Empty,
        };
    }

    public override void Write(Utf8JsonWriter writer, string value, JsonSerializerOptions options)
        => writer.WriteStringValue(value);
}
