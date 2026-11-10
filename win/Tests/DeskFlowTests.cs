using System;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using DeskFlow.Models;
using DeskFlow.Services;
using Xunit;

namespace DeskFlow.Tests;

internal sealed class StubHttpMessageHandler : HttpMessageHandler
{
    private readonly Func<HttpRequestMessage, Task<HttpResponseMessage>> _responder;

    public StubHttpMessageHandler(Func<HttpRequestMessage, Task<HttpResponseMessage>> responder)
    {
        _responder = responder;
    }

    protected override Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        return _responder(request);
    }
}

public class DeskFlowTests
{
    [Fact]
    public void ScheduledDarkMode_OvernightAndSameDayRanges_EvaluateCorrectly()
    {
        // Overnight 20:00 -> 06:00
        Assert.False(ScheduledDarkModeEvaluator.IsScheduledDarkActive(19 * 60 + 59, "20:00", "06:00"));
        Assert.True(ScheduledDarkModeEvaluator.IsScheduledDarkActive(20 * 60, "20:00", "06:00"));
        Assert.True(ScheduledDarkModeEvaluator.IsScheduledDarkActive(0, "20:00", "06:00"));
        Assert.True(ScheduledDarkModeEvaluator.IsScheduledDarkActive(5 * 60 + 59, "20:00", "06:00"));
        Assert.False(ScheduledDarkModeEvaluator.IsScheduledDarkActive(6 * 60, "20:00", "06:00"));

        // Same-day 09:00 -> 17:00
        Assert.False(ScheduledDarkModeEvaluator.IsScheduledDarkActive(8 * 60 + 59, "09:00", "17:00"));
        Assert.True(ScheduledDarkModeEvaluator.IsScheduledDarkActive(9 * 60, "09:00", "17:00"));
        Assert.False(ScheduledDarkModeEvaluator.IsScheduledDarkActive(17 * 60, "09:00", "17:00"));

        // Equal start & end -> disabled
        Assert.False(ScheduledDarkModeEvaluator.IsScheduledDarkActive(12 * 60, "06:00", "06:00"));
    }

    [Fact]
    public void MorseCodeConverter_BidirectionalAndVietnameseDiacritics()
    {
        string morse = MorseCodeConverter.TextToMorse("SOS Đạt");
        Assert.Equal("... --- ... / -.. .- -", morse);
        string decoded = MorseCodeConverter.MorseToText(morse);
        Assert.Equal("SOS DAT", decoded);
    }

    [Fact]
    public void NotesListResponse_EmptyListOnLastNoteDelete_DeserializesCleanly()
    {
        string json = """{"success":true,"deleted_id":"note-1","notes":[]}""";
        var parsed = JsonSerializer.Deserialize<NotesListResponse>(json);
        Assert.NotNull(parsed);
        Assert.Equal("note-1", parsed!.DeletedId);
        Assert.Empty(parsed.AllNotes);
    }

    [Fact]
    public async Task PairDeviceAsync_ValidPairingCode_CallsFocusPairAndSavesState()
    {
        string? capturedPath = null;
        string? capturedBody = null;
        var handler = new StubHttpMessageHandler(async req =>
        {
            capturedPath = req.RequestUri?.AbsolutePath;
            capturedBody = req.Content != null ? await req.Content.ReadAsStringAsync() : null;
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(
                    """{"device_token":"tok_win_123456","device_id":"dev_win_1"}""",
                    Encoding.UTF8,
                    "application/json")
            };
        });

        var settings = new WindowsAppSettings { ApiUrl = "https://mock.deskflow.local" };
        var client = new DeskFlowApiClient(settings, new HttpClient(handler));

        var (ok, msg) = await client.PairDeviceAsync("ab12-cd34");
        Assert.True(ok, msg);
        Assert.Equal("/api/focus/pair", capturedPath);
        Assert.Contains("\"pairing_code\":\"AB12CD34\"", capturedBody);
        Assert.Equal("tok_win_123456", settings.DeviceToken);
        Assert.Equal("dev_win_1", settings.DeviceId);
        Assert.True(settings.IsPaired);
    }

    [Fact]
    public async Task PairDeviceAsync_InvalidPairingCode403_ReturnsMappedErrorWithoutSavingToken()
    {
        var handler = new StubHttpMessageHandler(_ =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.Forbidden)
            {
                Content = new StringContent(
                    """{"error":{"code":"pair_code_invalid"}}""",
                    Encoding.UTF8,
                    "application/json")
            }));

        var settings = new WindowsAppSettings { ApiUrl = "https://mock.deskflow.local" };
        var client = new DeskFlowApiClient(settings, new HttpClient(handler));

        var (ok, msg) = await client.PairDeviceAsync("WRONGCOD");
        Assert.False(ok);
        Assert.Contains("Invalid, expired, or already-used pairing code", msg);
        Assert.Null(settings.DeviceToken);
    }

    [Fact]
    public async Task PairDeviceAsync_EmptyOrShortCode_RejectedClientSideWithoutNetworkCall()
    {
        int networkCalls = 0;
        var handler = new StubHttpMessageHandler(_ =>
        {
            networkCalls++;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK));
        });

        var settings = new WindowsAppSettings { ApiUrl = "https://mock.deskflow.local" };
        var client = new DeskFlowApiClient(settings, new HttpClient(handler));

        var (okEmpty, msgEmpty) = await client.PairDeviceAsync("   ");
        Assert.False(okEmpty);
        Assert.Contains("empty", msgEmpty, StringComparison.OrdinalIgnoreCase);

        var (okShort, msgShort) = await client.PairDeviceAsync("AB12");
        Assert.False(okShort);
        Assert.Contains("at least 6", msgShort, StringComparison.OrdinalIgnoreCase);
        Assert.Equal(0, networkCalls);
    }

    [Fact]
    public async Task PairDeviceAsync_MalformedResponseMissingToken_RejectedCleanly()
    {
        var handler = new StubHttpMessageHandler(_ =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"status":"ok"}""", Encoding.UTF8, "application/json")
            }));

        var settings = new WindowsAppSettings { ApiUrl = "https://mock.deskflow.local" };
        var client = new DeskFlowApiClient(settings, new HttpClient(handler));

        var (ok, msg) = await client.PairDeviceAsync("VALID123");
        Assert.False(ok);
        Assert.Contains("missing device_token", msg, StringComparison.OrdinalIgnoreCase);
        Assert.Null(settings.DeviceToken);
    }

    [Fact]
    public async Task PairDeviceAsync_ServerUnavailable_HandledGracefully()
    {
        var handler = new StubHttpMessageHandler(_ =>
            throw new HttpRequestException("Connection refused"));

        var settings = new WindowsAppSettings { ApiUrl = "https://mock.deskflow.local" };
        var client = new DeskFlowApiClient(settings, new HttpClient(handler));

        var (ok, msg) = await client.PairDeviceAsync("VALID123");
        Assert.False(ok);
        Assert.Contains("Connection refused", msg);
    }

    [Fact]
    public async Task DeleteNoteAsync_401RevokesTokenAndPreservesNotesUntil200()
    {
        int step = 0;
        var handler = new StubHttpMessageHandler(_ =>
        {
            step++;
            if (step == 1)
            {
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
                {
                    Content = new StringContent(
                        """{"success":true,"notes":[{"id":"n-1","content":"Last note"}]}""",
                        Encoding.UTF8,
                        "application/json")
                });
            }
            if (step == 2)
            {
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.Unauthorized)
                {
                    Content = new StringContent(
                        """{"error":{"code":"token_invalid"}}""",
                        Encoding.UTF8,
                        "application/json")
                });
            }
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(
                    """{"success":true,"deleted_id":"n-1","notes":[]}""",
                    Encoding.UTF8,
                    "application/json")
            });
        });

        var settings = new WindowsAppSettings
        {
            ApiUrl = "https://mock.deskflow.local",
            DeviceToken = "tok_initial",
            DeviceId = "dev_1"
        };
        var client = new DeskFlowApiClient(settings, new HttpClient(handler));

        var (loadOk, _) = await client.FetchNotesAsync();
        Assert.True(loadOk);
        Assert.Single(client.Notes);

        // 401 failure revokes token and does not clear Notes optimistically
        var (delFailOk, _) = await client.DeleteNoteAsync("n-1");
        Assert.False(delFailOk);
        Assert.Single(client.Notes);
        Assert.Null(settings.DeviceToken);

        // 200 success clears last note
        settings.DeviceToken = "tok_repaired";
        var (delPassOk, _) = await client.DeleteNoteAsync("n-1");
        Assert.True(delPassOk);
        Assert.Empty(client.Notes);
    }

    [Fact]
    public async Task SetClockStyleAsync_SendsCodeStringAndNoAuth()
    {
        string? capturedPath = null;
        string? capturedBody = null;
        bool hasAuthHeader = false;

        var handler = new StubHttpMessageHandler(async req =>
        {
            capturedPath = req.RequestUri?.AbsolutePath;
            capturedBody = req.Content != null ? await req.Content.ReadAsStringAsync() : null;
            hasAuthHeader = req.Headers.Authorization != null;
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"success":true,"style":"digital_neon"}""", Encoding.UTF8, "application/json")
            };
        });

        var settings = new WindowsAppSettings
        {
            ApiUrl = "https://mock.deskflow.local",
            DeviceToken = "tok_win_test"
        };
        var client = new DeskFlowApiClient(settings, new HttpClient(handler));

        // Test with integer 3 -> "digital_neon"
        var (okInt, msgInt) = await client.SetClockStyleAsync(3);
        Assert.True(okInt, msgInt);
        Assert.Equal("/api/app/control/clock-style", capturedPath);
        Assert.Contains("\"style\":\"digital_neon\"", capturedBody);
        Assert.False(hasAuthHeader, "Clock style endpoint must be unauthenticated");

        // Test with custom code string
        var (okStr, msgStr) = await client.SetClockStyleAsync("binary");
        Assert.True(okStr, msgStr);
        Assert.Equal("/api/app/control/clock-style", capturedPath);
        Assert.Contains("\"style\":\"binary\"", capturedBody);
    }

    [Fact]
    public async Task SwitchDisplayViewAsync_AllowsUnauthenticated()
    {
        string? capturedPath = null;
        string? capturedBody = null;
        bool hasAuthHeader = false;

        var handler = new StubHttpMessageHandler(async req =>
        {
            capturedPath = req.RequestUri?.AbsolutePath;
            capturedBody = req.Content != null ? await req.Content.ReadAsStringAsync() : null;
            hasAuthHeader = req.Headers.Authorization != null;
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"success":true,"view":"clock"}""", Encoding.UTF8, "application/json")
            };
        });

        var settings = new WindowsAppSettings { ApiUrl = "https://mock.deskflow.local" };
        var client = new DeskFlowApiClient(settings, new HttpClient(handler));

        var (ok, msg) = await client.SwitchDisplayViewAsync("clock");
        Assert.True(ok, msg);
        Assert.Equal("/api/app/control/display/view", capturedPath);
        Assert.Contains("\"view\":\"clock\"", capturedBody);
        Assert.False(hasAuthHeader);
    }

    [Fact]
    public async Task StopMorseAsync_CallsEndpoint()
    {
        string? capturedPath = null;
        var handler = new StubHttpMessageHandler(req =>
        {
            capturedPath = req.RequestUri?.AbsolutePath;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"success":true}""", Encoding.UTF8, "application/json")
            });
        });

        var settings = new WindowsAppSettings { ApiUrl = "https://mock.deskflow.local" };
        var client = new DeskFlowApiClient(settings, new HttpClient(handler));

        var (ok, msg) = await client.StopMorseAsync();
        Assert.True(ok, msg);
        Assert.Equal("/api/app/control/morse/stop", capturedPath);
    }

    [Fact]
    public async Task GetTimetableTodayAsync_ParsesPayloadCorrectly()
    {
        var handler = new StubHttpMessageHandler(req =>
        {
            Assert.Equal("/api/app/control/timetable/today", req.RequestUri?.AbsolutePath);
            var json = """
            {
                "success": true,
                "data": {
                    "week_number": "Tuần 10",
                    "today": {
                        "day": "Thứ 3",
                        "display_day": "Thứ Ba",
                        "morning": ["Toán", "Văn"],
                        "dp_sang": "Áo trắng",
                        "has_afternoon": true,
                        "afternoon": ["Thể dục"],
                        "dp_chieu": "Đồ thể dục",
                        "has_any_class": true
                    },
                    "tomorrow": {
                        "day": "Thứ 4",
                        "morning": ["Lý", "Hóa"],
                        "dp_sang": "Áo trắng",
                        "has_afternoon": false,
                        "afternoon": [],
                        "dp_chieu": null,
                        "has_any_class": true
                    }
                }
            }
            """;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json")
            });
        });

        var client = new DeskFlowApiClient(new WindowsAppSettings { ApiUrl = "https://mock.deskflow.local" }, new HttpClient(handler));
        var (ok, data, _) = await client.GetTimetableTodayAsync();

        Assert.True(ok);
        Assert.NotNull(data);
        Assert.Equal("Tuần 10", data.WeekNumber);
        Assert.Equal("Áo trắng", data.Today?.DpSang);
        Assert.Equal("Toán • Văn", data.Today?.MorningSummary);
    }

    [Fact]
    public async Task CreatePairSessionAsync_SendsRequestAndParsesResponse()
    {
        string? capturedBody = null;
        var handler = new StubHttpMessageHandler(async req =>
        {
            Assert.Equal("/api/focus/pair/session/create", req.RequestUri?.AbsolutePath);
            capturedBody = await req.Content!.ReadAsStringAsync();
            var json = """
            {
                "success": true,
                "session_id": "sess-test-win",
                "qr_payload": "deskflow://pair?session=sess-test-win",
                "expires_in": 600
            }
            """;
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json")
            };
        });

        var client = new DeskFlowApiClient(new WindowsAppSettings { ApiUrl = "https://mock.deskflow.local" }, new HttpClient(handler));
        var (ok, data, _) = await client.CreatePairSessionAsync("My PC", "windows");

        Assert.True(ok);
        Assert.NotNull(data);
        Assert.Equal("sess-test-win", data.SessionId);
        Assert.Contains("My PC", capturedBody);
    }
}
