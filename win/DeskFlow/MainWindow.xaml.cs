using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using DeskFlow.Models;
using DeskFlow.Services;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;

namespace DeskFlow;

public sealed partial class MainWindow : Window
{
    private readonly DeskFlowApiClient _client;
    private readonly DispatcherQueueTimer _themeTimer;
    private readonly List<PairedDeviceItemDto> _devicesCache = new();
    private DispatcherQueueTimer? _pairPollTimer;
    private string? _activePairSessionId;
    private bool _isLoaded;

    // ---- Focus Mode hard lock ----
    private const int HwndTopMost = -1;
    private const int HwndNoTopMost = -2;
    private const uint SwpNoSize = 0x0001;
    private const uint SwpNoMove = 0x0002;
    private const uint SwpNoActivate = 0x0010;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool SetWindowPos(
        IntPtr hWnd, IntPtr hWndInsertAfter, int x, int y, int cx, int cy, uint flags);

    private volatile bool _focusLocked;
    private DispatcherQueueTimer? _focusPollTimer;
    private string? _focusChallengeId;
    // Set after a correct challenge answer so an in-flight status poll that was
    // sent before the unlock cannot lock the window again.
    private DateTime _suppressRelockUntil = DateTime.MinValue;

    public MainWindow()
    {
        _client = new DeskFlowApiClient();
        this.InitializeComponent();
        this.Title = "Deskflow";

        // Seamless title bar into Mica background
        try
        {
            this.ExtendsContentIntoTitleBar = true;
            this.SetTitleBar(AppTitleBar);

            if (Microsoft.UI.Windowing.AppWindowTitleBar.IsCustomizationSupported())
            {
                var titleBar = this.AppWindow.TitleBar;
                titleBar.ExtendsContentIntoTitleBar = true;
                titleBar.ButtonBackgroundColor = Microsoft.UI.Colors.Transparent;
                titleBar.ButtonInactiveBackgroundColor = Microsoft.UI.Colors.Transparent;
                titleBar.ButtonHoverBackgroundColor = Windows.UI.Color.FromArgb(30, 255, 255, 255);
                titleBar.ButtonPressedBackgroundColor = Windows.UI.Color.FromArgb(60, 255, 255, 255);
            }
        }
        catch {}

        ApiUrlTextBox.Text = _client.Settings.ApiUrl;
        DarkStartTextBox.Text = _client.Settings.DarkModeStart;
        DarkEndTextBox.Text = _client.Settings.DarkModeEnd;
        UpdatePairingStatusLabel();

        Strings.CurrentLanguage = _client.Settings.Language ?? "vi";
        ApplyLanguage();
        if (LanguageCombo != null) LanguageCombo.SelectedIndex = (_client.Settings.Language == "en") ? 1 : 0;
        _suppressEsp32LanguageSave = true;
        if (Esp32LanguageCombo != null) Esp32LanguageCombo.SelectedIndex = (_client.Settings.Esp32Language == "en") ? 1 : 0;
        _suppressEsp32LanguageSave = false;
        // Restore the saved Windows theme into the combo. Without this the box
        // rendered empty ("") until the user picked something.
        if (ThemeModeCombo != null)
        {
            int themeIndex = ScheduledDarkModeEvaluator.NormalizeTheme(_client.Settings.AppTheme) switch
            {
                "light" => 1,
                "dark" => 2,
                "scheduled" => 3,
                _ => 0,
            };
            _suppressThemeComboSave = true;
            ThemeModeCombo.SelectedIndex = themeIndex;
            _suppressThemeComboSave = false;
        }
        if (Esp32ThemeCombo != null) Esp32ThemeCombo.SelectedIndex = 0;

        try
        {
            var iconPath = System.IO.Path.Combine(AppContext.BaseDirectory, "Assets", "app.ico");
            if (System.IO.File.Exists(iconPath))
            {
                this.AppWindow.SetIcon(iconPath);
            }
        }
        catch {}

        _themeTimer = DispatcherQueue.GetForCurrentThread().CreateTimer();
        _themeTimer.Interval = TimeSpan.FromSeconds(15);
        _themeTimer.Tick += (_, _) => ApplyEffectiveTheme();
        _themeTimer.Start();

        // Focus Mode lock: watch the window close request and re-check the
        // authoritative backend state while the app is running.
        this.AppWindow.Closing += OnAppWindowClosing;
        _focusPollTimer = DispatcherQueue.GetForCurrentThread().CreateTimer();
        _focusPollTimer.Interval = TimeSpan.FromSeconds(10);
        _focusPollTimer.Tick += async (_, _) => await PollFocusStatusAsync();
        _focusPollTimer.Start();

        // 1-second periodic telemetry and stats refresh
        var telTimer = DispatcherQueue.GetForCurrentThread().CreateTimer();
        telTimer.Interval = TimeSpan.FromSeconds(1);
        telTimer.Tick += async (_, _) =>
        {
            try
            {
                var localMetrics = await _client.GetLocalTelemetryAsync();
                await _client.ReportTelemetryAsync(localMetrics.Cpu, localMetrics.Ram, localMetrics.Battery);
            }
            catch {}

            try
            {
                var status = await _client.GetStationStatusAsync();
                if (status != null)
                {
                    if (status.Station != null)
                    {
                        EspBatteryValueText.Text = $"{status.Station.BatteryPct}%";
                        EspVoltageText.Text = $"{status.Station.Voltage:0.00}V";
                        EspWifiValueText.Text = $"{status.Station.WifiRssi} dBm";
                        EspWifiQualityText.Text = status.Station.WifiRssi > -65 ? (Strings.CurrentLanguage == "vi" ? "Mạnh" : "Strong") : (Strings.CurrentLanguage == "vi" ? "Trung bình" : "Fair");
                        EspViewValueText.Text = status.Station.CurrentView?.ToUpper() ?? "MAIN";
                        if (OledMonitorLiveViewText != null && !string.IsNullOrEmpty(_liveStationView))
                        {
                            OledMonitorLiveViewText.Text = (_liveStationView ?? "").ToUpper();
                        }

                        // Mirror the station's real screen into a separate field:
                        // overwriting _previewOledView reset the preview every
                        // second, which made "click to preview" look broken.
                        var liveView = status.Station.CurrentView?.Trim().ToLowerInvariant();
                        if (!string.IsNullOrEmpty(liveView))
                        {
                            _liveStationView = liveView;
                        }
                    }
                    if (status.Server != null)
                    {
                        ServerCpuValueText.Text = $"{status.Server.Cpu:0.1}%";
                        ServerRamValueText.Text = $"{Math.Clamp(status.Server.RamPct, 0.0, 100.0):0.1}%";
                        ServerDiskValueText.Text = status.Server.DiskStr ?? "13.3 / 19.1GB";
                    }
                    var comp = status.Computer ?? status.Laptop;
                    if (comp != null)
                    {
                        ComputerTitleText.Text = Strings.CurrentLanguage == "vi" ? $"Thông số Máy tính ({comp.DeviceName})" : $"Computer Metrics ({comp.DeviceName})";

                        // A machine that is not reporting must not look like a real
                        // 0% reading: show the last known value with an explicit
                        // offline marker instead of three zeros.
                        string dash = comp.Online ? "" : " (offline)";
                        ComputerCpuValueText.Text = comp.Cpu > 0 ? $"{Math.Clamp(comp.Cpu, 0.0, 100.0):0.1}%" : "--";
                        ComputerRamValueText.Text = comp.Ram > 0 ? $"{Math.Clamp(comp.Ram, 0.0, 100.0):0.1}%" : "--";
                        ComputerBatteryValueText.Text = comp.Battery > 0 ? $"{comp.Battery}%" : "--";
                        ComputerStatusText.Text = comp.Online
                            ? (Strings.CurrentLanguage == "vi" ? "Online" : "Online")
                            : (Strings.CurrentLanguage == "vi" ? "Offline — đang dùng số liệu cuối" : "Offline — last known values");
                        if (!string.IsNullOrEmpty(dash) && ComputerTitleText != null)
                        {
                            ComputerTitleText.Text += dash;
                        }
                    }
                }
            }
            catch {}

            try
            {
                EcosystemNotesText.Text = $"{_client.Notes.Count}";
                EcosystemCountdownsText.Text = $"{_client.Countdowns.Count}";
                EcosystemWeatherText.Text = $"{_client.WeatherIntervals.Count}";
            }
            catch {}

            try
            {
                var sp = await _client.GetSpotifyStatusAsync();
                if (sp != null)
                {
                    _lastSpotify = sp;
                    UpdateSpotifyUI(sp);
                }
            }
            catch {}
        };
        telTimer.Start();

        // Spotify progress is interpolated locally between polls, so the bar and
        // clock move smoothly instead of jumping once per second.
        var spTimer = DispatcherQueue.GetForCurrentThread().CreateTimer();
        spTimer.Interval = TimeSpan.FromMilliseconds(250);
        spTimer.Tick += (_, _) => InterpolateSpotifyProgress();
        spTimer.Start();

        // Dedicated 1-second Spotify poll: the dashboard timer refreshes every
        // second but its status call was shared, so lyrics visibly lagged.
        var spPoll = DispatcherQueue.GetForCurrentThread().CreateTimer();
        spPoll.Interval = TimeSpan.FromSeconds(1);
        spPoll.Tick += async (_, _) =>
        {
            try
            {
                var sp = await _client.GetSpotifyStatusAsync();
                if (sp != null)
                {
                    _lastSpotify = sp;
                    UpdateSpotifyUI(sp);
                }
            }
            catch {}
        };
        spPoll.Start();

        var tkbTimer = DispatcherQueue.GetForCurrentThread().CreateTimer();
        tkbTimer.Interval = TimeSpan.FromSeconds(20);
        tkbTimer.Tick += TkbAutoRefresh_Tick;
        tkbTimer.Start();

        ApplyEffectiveTheme();

        // Initial fetch of data
        _ = RefreshAllDataAsync();

        _isLoaded = true;

        // The visual tree only exists after layout, so apply the saved language
        // once more now that every control has been realised.
        ApplyLanguage();

        // If a focus session is already running (started from phone/another
        // device), lock this window as soon as the app finishes loading.
        _ = PollFocusStatusAsync();

        if (SpotifyVolumeSlider != null)
        {
            SpotifyVolumeSlider.ValueChanged += SpotifyVolumeSlider_ValueChanged;
        }

        // Default selection to System & Telemetry (macOS parity)
        MainNavView.SelectedItem = NavSystemItem;
    }

    private async Task RefreshAllDataAsync()
    {
        try
        {
            await _client.FetchNotesAsync();
            await _client.FetchCountdownsAsync();
            await _client.FetchWeatherForecastAsync();
            RenderNotesList();
            RenderCountdownsList();
            RenderWeatherGallery();
        }
        catch {}
    }

    private void UpdatePairingStatusLabel()
    {
        bool isPaired = _client.Settings.IsPaired;
        if (PairingGatePanel != null)
        {
            PairingGatePanel.Visibility = isPaired ? Visibility.Collapsed : Visibility.Visible;
        }
        if (MainNavView != null)
        {
            MainNavView.Visibility = isPaired ? Visibility.Visible : Visibility.Collapsed;
        }

        if (!isPaired)
        {
            _ = StartQrPairingSessionAsync();
        }
        else
        {
            StopQrPairingSession();
        }

        if (PairStatusDot != null)
        {
            PairStatusDot.Fill = new SolidColorBrush(isPaired ? Windows.UI.Color.FromArgb(255, 16, 185, 129) : Windows.UI.Color.FromArgb(255, 245, 158, 11));
        }

        if (PairingStatusText != null)
        {
            bool isVi = Strings.CurrentLanguage == "vi";
            PairingStatusText.Text = isPaired
                ? (isVi ? $"Trạng thái: Đã liên kết ({_client.Settings.DeviceName ?? _client.Settings.DeviceId ?? "Windows PC"})" : $"Status: Paired ({_client.Settings.DeviceName ?? _client.Settings.DeviceId ?? "Windows PC"})")
                : (isVi ? "Trạng thái: Chưa liên kết (Chế độ giám sát)" : "Status: Unpaired (Inspection Mode)");
        }
    }

    public void ApplyEffectiveTheme()
    {
        var mode = ScheduledDarkModeEvaluator.NormalizeTheme(_client.Settings.AppTheme);
        if (mode == "light")
        {
            RootGrid.RequestedTheme = ElementTheme.Light;
        }
        else if (mode == "dark")
        {
            RootGrid.RequestedTheme = ElementTheme.Dark;
        }
        else if (mode == "scheduled")
        {
            var now = DateTime.Now;
            int nowMin = now.Hour * 60 + now.Minute;
            bool isDark = ScheduledDarkModeEvaluator.IsScheduledDarkActive(
                nowMin,
                _client.Settings.DarkModeStart,
                _client.Settings.DarkModeEnd,
                fallbackWhenEqual: false);
            RootGrid.RequestedTheme = isDark ? ElementTheme.Dark : ElementTheme.Light;
        }
        else
        {
            RootGrid.RequestedTheme = ElementTheme.Default;
        }
    }

    private DispatcherQueueTimer? _dismissTimer;

    private void ShowBanner(string message, bool isError = false)
    {
        StatusInfoBar.Severity = isError ? InfoBarSeverity.Error : InfoBarSeverity.Success;
        StatusInfoBar.Title = isError ? (Strings.CurrentLanguage == "vi" ? "Lỗi" : "Error") : "Deskflow";
        StatusInfoBar.Message = message;
        StatusInfoBar.IsOpen = true;

        _dismissTimer?.Stop();
        _dismissTimer = DispatcherQueue.GetForCurrentThread().CreateTimer();
        _dismissTimer.Interval = TimeSpan.FromMilliseconds(1500);
        _dismissTimer.Tick += (s, e) =>
        {
            _dismissTimer?.Stop();
            StatusInfoBar.IsOpen = false;
        };
        _dismissTimer.Start();
    }

    private void MainNavView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (!_isLoaded) return;
        if (args.SelectedItem is not NavigationViewItem item) return;
        string tag = item.Tag?.ToString() ?? "system";

        SystemPanel.Visibility = (tag == "system" || tag == "dashboard") ? Visibility.Visible : Visibility.Collapsed;
        StationPanel.Visibility = (tag == "station" || tag == "hardware") ? Visibility.Visible : Visibility.Collapsed;
        SpotifyPanel.Visibility = (tag == "spotify") ? Visibility.Visible : Visibility.Collapsed;
        AlarmsPanel.Visibility = (tag == "alarms") ? Visibility.Visible : Visibility.Collapsed;
        TkbPanel.Visibility = (tag == "timetable") ? Visibility.Visible : Visibility.Collapsed;
        ToolsPanel.Visibility = (tag == "tools" || tag == "notes" || tag == "countdowns" || tag == "weather" || tag == "morse" || tag == "focus") ? Visibility.Visible : Visibility.Collapsed;
        SettingsPanel.Visibility = (tag == "settings") ? Visibility.Visible : Visibility.Collapsed;

        if (tag == "system" || tag == "dashboard") _ = RefreshAllDataAsync();
        if (tag == "timetable") _ = LoadTimetableAsync();
        if (tag == "spotify") _ = RefreshSpotifyAsync();
        if (tag == "alarms") RefreshAlarms();
        if (tag == "tools") { RenderNotesList(); RenderCountdownsList(); }
        if (tag == "focus") BringFocusCardIntoView();
        if (tag == "settings") RefreshDevices();
    }

    private async void GatePairButton_Click(object sender, RoutedEventArgs e)
    {
        if (GatePairCodeBox == null) return;
        var code = GatePairCodeBox.Text.Trim();
        if (string.IsNullOrWhiteSpace(code))
        {
            if (GateStatusText != null)
            {
                GateStatusText.Text = Strings.CurrentLanguage == "vi" ? "Mã ghép nối không được để trống." : "Pairing code cannot be empty.";
                GateStatusText.Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 239, 68, 68));
                GateStatusText.Visibility = Visibility.Visible;
            }
            return;
        }

        if (GatePairButton != null) GatePairButton.IsEnabled = false;
        if (GateStatusText != null)
        {
            GateStatusText.Text = Strings.CurrentLanguage == "vi" ? "Đang kết nối và xác thực..." : "Connecting and authenticating...";
            GateStatusText.Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 99, 102, 241));
            GateStatusText.Visibility = Visibility.Visible;
        }

        var (success, error) = await _client.PairDeviceAsync(code);
        if (GatePairButton != null) GatePairButton.IsEnabled = true;

        if (success)
        {
            if (GatePairCodeBox != null) GatePairCodeBox.Text = string.Empty;
            if (GateStatusText != null) GateStatusText.Visibility = Visibility.Collapsed;
            UpdatePairingStatusLabel();
            ShowBanner(Strings.CurrentLanguage == "vi" ? "Ghép nối thiết bị thành công!" : "Device paired successfully!");
            _ = RefreshAllDataAsync();
        }
        else
        {
            if (GateStatusText != null)
            {
                GateStatusText.Text = error ?? (Strings.CurrentLanguage == "vi" ? "Ghép nối thất bại. Vui lòng thử lại." : "Pairing failed. Please try again.");
                GateStatusText.Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 239, 68, 68));
                GateStatusText.Visibility = Visibility.Visible;
            }
        }
    }

    private async Task StartQrPairingSessionAsync()
    {
        try
        {
            string chosenName = GateDeviceNameBox != null && !string.IsNullOrWhiteSpace(GateDeviceNameBox.Text)
                ? GateDeviceNameBox.Text.Trim()
                : (_client.Settings.DeviceName ?? "DeskFlow Windows");

            if (GateDeviceNameBox != null && string.IsNullOrWhiteSpace(GateDeviceNameBox.Text))
            {
                GateDeviceNameBox.Text = chosenName;
            }

            var (ok, data, _) = await _client.CreatePairSessionAsync(chosenName, "windows");
            if (ok && data != null && !string.IsNullOrWhiteSpace(data.QrPayload))
            {
                _activePairSessionId = data.SessionId;
                if (GateQrImage != null)
                {
                    var qrUrl = !string.IsNullOrWhiteSpace(data.QrImageUrl)
                        ? data.QrImageUrl
                        : $"https://api.qrserver.com/v1/create-qr-code/?size=220x220&data={Uri.EscapeDataString(data.QrPayload)}";
                    GateQrImage.Source = new BitmapImage(new Uri(qrUrl));
                }

                if (_pairPollTimer == null)
                {
                    _pairPollTimer = DispatcherQueue.GetForCurrentThread().CreateTimer();
                    _pairPollTimer.Interval = TimeSpan.FromSeconds(2);
                    _pairPollTimer.Tick += async (_, _) => await PollPairSessionAsync();
                }
                _pairPollTimer.Start();
            }
        }
        catch {}
    }

    private void GateDeviceNameBox_KeyDown(object sender, Microsoft.UI.Xaml.Input.KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter)
        {
            _ = StartQrPairingSessionAsync();
        }
    }

    private void StopQrPairingSession()
    {
        _pairPollTimer?.Stop();
        _pairPollTimer = null;
        _activePairSessionId = null;
    }

    private async Task PollPairSessionAsync()
    {
        if (string.IsNullOrWhiteSpace(_activePairSessionId)) return;
        try
        {
            var (ok, data, _) = await _client.GetPairSessionStatusAsync(_activePairSessionId);
            if (ok && data != null && data.Status == "approved")
            {
                StopQrPairingSession();
                UpdatePairingStatusLabel();
                ShowBanner(Strings.CurrentLanguage == "vi" ? "Ghép nối thiết bị thành công qua QR!" : "Device paired successfully via QR!");
                _ = RefreshAllDataAsync();
            }
        }
        catch {}
    }

    private async void GateRegenerateQr_Click(object sender, RoutedEventArgs e)
    {
        await StartQrPairingSessionAsync();
    }

    private async Task LoadTimetableAsync(bool showErrors = true)
    {
        try
        {
            var (ok, data, msg) = await _client.GetTimetableTodayAsync();
            if (!ok || data == null)
            {
                if (showErrors)
                {
                    ShowBanner(
                        Strings.CurrentLanguage == "vi"
                            ? $"Không tải được TKB: {msg}"
                            : $"Could not load timetable: {msg}",
                        isError: true);
                }
                return;
            }

            if (TkbWeekText != null) TkbWeekText.Text = $"Tuần {data.WeekNumber}";

            if (data.Today != null)
            {
                if (TkbTodayTitle != null) TkbTodayTitle.Text = $"Lịch học Hôm nay ({data.Today.DisplayDay ?? data.Today.Day})";
                if (TkbTodayMorningUniform != null) TkbTodayMorningUniform.Text = $"Đồng phục sáng: {data.Today.DpSang}";
                if (TkbTodayMorningClasses != null) TkbTodayMorningClasses.Text = data.Today.MorningSummary;
                if (TkbTodayAfternoonUniform != null) TkbTodayAfternoonUniform.Text = $"Đồng phục chiều: {data.Today.DpChieu ?? "Không có"}";
                if (TkbTodayAfternoonClasses != null) TkbTodayAfternoonClasses.Text = data.Today.AfternoonSummary;
            }

            if (data.Tomorrow != null)
            {
                if (TkbTomorrowTitle != null) TkbTomorrowTitle.Text = $"Lịch học Ngày mai ({data.Tomorrow.DisplayDay ?? data.Tomorrow.Day})";
                if (TkbTomorrowMorningUniform != null) TkbTomorrowMorningUniform.Text = $"Đồng phục sáng: {data.Tomorrow.DpSang}";
                if (TkbTomorrowMorningClasses != null) TkbTomorrowMorningClasses.Text = data.Tomorrow.MorningSummary;
                if (TkbTomorrowAfternoonUniform != null) TkbTomorrowAfternoonUniform.Text = $"Đồng phục chiều: {data.Tomorrow.DpChieu ?? "Không có"}";
                if (TkbTomorrowAfternoonClasses != null) TkbTomorrowAfternoonClasses.Text = data.Tomorrow.AfternoonSummary;
            }
            _lastTimetableSync = DateTime.Now;
        }
        catch (Exception ex)
        {
            if (showErrors) ShowBanner($"TKB error: {ex.Message}", isError: true);
        }
    }

    private DateTime _lastTimetableSync = DateTime.MinValue;

    // Keep the Windows TKB panel in sync with the backend instead of only
    // updating when the refresh button is pressed (source of the "TKB does not
    // sync" report).
    private async void TkbAutoRefresh_Tick(DispatcherQueueTimer sender, object args)
    {
        if (TkbPanel == null || TkbPanel.Visibility != Visibility.Visible) return;
        if ((DateTime.Now - _lastTimetableSync).TotalSeconds < 20) return;
        await LoadTimetableAsync(showErrors: false);
    }

    private async void TkbSyncEsp32_Click(object sender, RoutedEventArgs e)
    {
        await LoadTimetableAsync();
        var (ok, msg) = await _client.SwitchDisplayViewAsync("tkb");
        ShowBanner(ok
            ? (Strings.CurrentLanguage == "vi" ? "Đã đồng bộ TKB và chuyển màn hình ESP32 sang TKB." : "Timetable synced and ESP32 switched to timetable.")
            : msg, !ok);
    }

    private async void TkbRefresh_Click(object sender, RoutedEventArgs e)
    {
        await LoadTimetableAsync();
        ShowBanner(Strings.CurrentLanguage == "vi" ? "Đã làm mới Thời khóa biểu!" : "Timetable refreshed!");
    }

    private async void ClockAccentColor_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string hex)
        {
            var (ok, msg) = await _client.SetClockAccentColorAsync(hex);
            ShowBanner(msg, !ok);
        }
    }

    private void GatePairCodeBox_KeyDown(object sender, Microsoft.UI.Xaml.Input.KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter)
        {
            GatePairButton_Click(sender, e);
        }
    }

    private async void PairButton_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.PairDeviceAsync(PairCodeBox.Text);
        if (ok) PairCodeBox.Text = string.Empty;
        UpdatePairingStatusLabel();
        ShowBanner(msg, !ok);
    }

    private async void UnpairButton_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.UnpairDeviceAsync();
        UpdatePairingStatusLabel();
        ShowBanner(msg, !ok);
    }

    private async void RefreshAllButton_Click(object sender, RoutedEventArgs e)
    {
        await RefreshAllDataAsync();
        ShowBanner(Strings.CurrentLanguage == "vi" ? "Đã làm mới dữ liệu hệ sinh thái!" : "Ecosystem data refreshed!");
    }

    private async void StartFocus25_Click(object sender, RoutedEventArgs e)
    {
        FocusDurationBox.Value = 25;
        var (ok, msg) = await _client.SetFocusModeAsync(true, 25);
        EcosystemFocusText.Text = ok ? "25m Focus" : "Nghỉ";
        if (ok) LockForNewSession();
        ShowBanner(msg, !ok);
    }

    private async void StartFocus50_Click(object sender, RoutedEventArgs e)
    {
        FocusDurationBox.Value = 50;
        var (ok, msg) = await _client.SetFocusModeAsync(true, 50);
        EcosystemFocusText.Text = ok ? "50m Focus" : "Nghỉ";
        if (ok) LockForNewSession();
        ShowBanner(msg, !ok);
    }

    private async void StartFocusCustom_Click(object sender, RoutedEventArgs e)
    {
        double raw = FocusDurationBox.Value;
        if (double.IsNaN(raw))
        {
            ShowBanner(
                Strings.CurrentLanguage == "vi"
                    ? "Nhập số phút trước (1–300)."
                    : "Enter minutes first (1–300).",
                true);
            return;
        }

        int minutes = Math.Clamp((int)Math.Round(raw), 1, 300);
        FocusDurationBox.Value = minutes;

        var (ok, msg) = await _client.SetFocusModeAsync(true, minutes);
        EcosystemFocusText.Text = ok
            ? $"{minutes}m Focus"
            : (Strings.CurrentLanguage == "vi" ? "Nghỉ" : "Idle");
        if (ok) LockForNewSession();
        ShowBanner(msg, !ok);
    }

    /// <summary>Locks for a session the user just started (clears the unlock suppression).</summary>
    private void LockForNewSession()
    {
        _suppressRelockUntil = DateTime.MinValue;
        _focusChallengeId = null;
        ApplyFocusLock(true);
    }

    private async void StopFocus_Click(object sender, RoutedEventArgs e)
    {
        // /api/focus/enable only STARTS a session, so the math challenge is the
        // single legitimate way to end Focus Mode early. Never send a fake stop.
        if (_focusLocked)
        {
            await RequestFocusChallengeAsync();
            return;
        }

        // Not locked: only offer the challenge when a session really is running.
        var (ok, active) = await _client.GetFocusStatusAsync();
        if (!ok)
        {
            ShowBanner(Strings.CurrentLanguage == "vi"
                ? "Không đọc được trạng thái Focus Mode."
                : "Could not read Focus Mode status.", true);
            return;
        }

        if (!active)
        {
            ShowBanner(Strings.CurrentLanguage == "vi"
                ? "Focus Mode đang tắt, không có phiên nào cần dừng."
                : "Focus Mode is off; there is no session to stop.", false);
            return;
        }

        await RequestFocusChallengeAsync();
    }

    private async void VerifyFocusChallenge_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(_focusChallengeId))
        {
            await RequestFocusChallengeAsync();
            return;
        }

        if (!int.TryParse(FocusChallengeAnswerBox.Text.Trim(), out int answer))
        {
            ShowBanner(Strings.CurrentLanguage == "vi" ? "Đáp án phải là số nguyên." : "Answer must be an integer.", true);
            return;
        }

        var (ok, correct, newId, newQuestion, msg) =
            await _client.VerifyFocusChallengeAsync(_focusChallengeId, answer);

        if (!ok)
        {
            // A 409/404 here normally means the session already ended server-side
            // (timer ran out, or it was unlocked from another device). Release the
            // window instead of showing an error the user cannot act on.
            if (await IsFocusModeAlreadyOverAsync())
            {
                _suppressRelockUntil = DateTime.UtcNow.AddSeconds(20);
                _focusChallengeId = null;
                FocusChallengePanel.Visibility = Visibility.Collapsed;
                FocusChallengeQuestionText.Text = string.Empty;
                FocusChallengeAnswerBox.Text = string.Empty;
                ApplyFocusLock(false);
                EcosystemFocusText.Text = Strings.CurrentLanguage == "vi" ? "Nghỉ" : "Idle";
                ShowBanner(
                    Strings.CurrentLanguage == "vi"
                        ? "Phiên tập trung đã kết thúc. Đã mở khoá."
                        : "The focus session already ended. Unlocked.",
                    false);
                return;
            }

            ShowBanner(msg, true);
            return;
        }

        if (correct)
        {
            _focusChallengeId = null;
            FocusChallengePanel.Visibility = Visibility.Collapsed;
            FocusChallengeQuestionText.Text = string.Empty;
            FocusChallengeAnswerBox.Text = string.Empty;

            // The backend already disabled Focus Mode when the HMAC matched.
            // Just unlock locally - do NOT call the start endpoint.
            _suppressRelockUntil = DateTime.UtcNow.AddSeconds(20);
            ApplyFocusLock(false);
            EcosystemFocusText.Text = Strings.CurrentLanguage == "vi" ? "Nghỉ" : "Idle";
            ShowBanner(Strings.CurrentLanguage == "vi" ? "Đúng rồi! Đã mở khoá." : "Correct! Unlocked.", false);
        }
        else
        {
            if (!string.IsNullOrWhiteSpace(newId)) _focusChallengeId = newId;
            FocusChallengeQuestionText.Text = newQuestion;
            FocusChallengeAnswerBox.Text = string.Empty;
            ShowBanner(msg, true);
        }
    }

    private void FocusChallengeAnswerBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key != Windows.System.VirtualKey.Enter) return;
        e.Handled = true;
        VerifyFocusChallenge_Click(sender, new RoutedEventArgs());
    }

    /// <summary>Loads a question as soon as the window locks, so unlocking is always one step away.</summary>
    private async Task EnsureFocusChallengeAsync()
    {
        if (_focusChallengeId != null) return;

        // Reuse an active challenge (e.g. one already shown on the phone) instead
        // of replacing it, so an answer typed on another device stays valid.
        var (ok, id, question, _) = await _client.RequestFocusChallengeAsync(forceNew: false);

        // The session may have ended (or been unlocked) while the request was
        // in flight - never leave a stale question on screen.
        if (!_focusLocked)
        {
            _focusChallengeId = null;
            return;
        }

        if (!ok || string.IsNullOrWhiteSpace(id))
        {
            return; // the Stop button still retries on demand
        }

        _focusChallengeId = id;
        FocusChallengePanel.Visibility = Visibility.Visible;
        FocusChallengeQuestionText.Text = question;
    }

    private async Task RequestFocusChallengeAsync()
    {
        var (ok, id, question, msg) = await _client.RequestFocusChallengeAsync();
        if (!ok)
        {
            // focus_not_active means the session already expired or was ended
            // elsewhere: there is nothing to unlock, so never keep the window locked.
            if (await IsFocusModeAlreadyOverAsync())
            {
                _suppressRelockUntil = DateTime.UtcNow.AddSeconds(20);
                ApplyFocusLock(false);
                EcosystemFocusText.Text = Strings.CurrentLanguage == "vi" ? "Nghỉ" : "Idle";
                ShowBanner(
                    Strings.CurrentLanguage == "vi"
                        ? "Phiên tập trung đã kết thúc. Đã mở khoá."
                        : "The focus session already ended. Unlocked.",
                    false);
                return;
            }

            ShowBanner(msg, true);
            return;
        }

        _focusChallengeId = id;
        FocusChallengePanel.Visibility = Visibility.Visible;
        FocusChallengeQuestionText.Text = question;
        FocusChallengeAnswerBox.Text = string.Empty;
        FocusChallengeAnswerBox.Focus(FocusState.Programmatic);
        ShowBanner(
            Strings.CurrentLanguage == "vi"
                ? "Giải bài toán dưới đây để mở khoá sớm."
                : "Solve the challenge below to unlock early.",
            false);
    }

    /// <summary>Scrolls the Focus Mode card (and its unlock challenge) into view.</summary>
    private void BringFocusCardIntoView()
    {
        try
        {
            FocusModeCard?.StartBringIntoView();
        }
        catch
        {
            // Scrolling is a nicety; never let it break the lock flow.
        }
    }

    /// <summary>True when the backend reports no active Focus session.</summary>
    private async Task<bool> IsFocusModeAlreadyOverAsync()
    {
        var (ok, active) = await _client.GetFocusStatusAsync();
        return ok && !active;
    }

    /// <summary>Applies always-on-top and the no-close policy.</summary>
    private void ApplyFocusLock(bool locked)
    {
        _focusLocked = locked;

        try
        {
            IntPtr hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);
            SetWindowPos(
                hwnd,
                locked ? new IntPtr(HwndTopMost) : new IntPtr(HwndNoTopMost),
                0, 0, 0, 0,
                SwpNoMove | SwpNoSize | SwpNoActivate);
        }
        catch { }

        try
        {
            if (this.AppWindow.Presenter is Microsoft.UI.Windowing.OverlappedPresenter presenter)
            {
                // NOTE: IsClosable only exists in Windows App SDK 1.6+; this project
                // pins 1.5, so closing is blocked by the AppWindow.Closing handler below.
                presenter.IsMinimizable = !locked;
                presenter.IsMaximizable = !locked;
            }
        }
        catch { }

        if (FocusLockHintText != null)
        {
            FocusLockHintText.Text = Strings.CurrentLanguage == "vi"
                ? "Focus Mode đang bật: cửa sổ luôn nổi và không thể đóng. Giải bài toán để mở khoá."
                : "Focus Mode is on: this window stays on top and cannot be closed. Solve the challenge to unlock.";
            FocusLockHintText.Visibility = locked ? Visibility.Visible : Visibility.Collapsed;
        }

        if (!locked)
        {
            _focusChallengeId = null;
            if (FocusChallengePanel != null)
                FocusChallengePanel.Visibility = Visibility.Collapsed;
            if (FocusChallengeAnswerBox != null)
                FocusChallengeAnswerBox.Text = string.Empty;
        }
        else if (FocusChallengePanel != null)
        {
            // The challenge lives in the Tools page: bring it into view so the
            // question is always visible instead of hiding behind another tab.
            if (MainNavView != null)
                MainNavView.SelectedItem = NavToolsItem;
            BringFocusCardIntoView();

            // Always leave a visible way out: the math challenge.
            FocusChallengePanel.Visibility = Visibility.Visible;
            if (FocusChallengeQuestionText != null
                && string.IsNullOrWhiteSpace(FocusChallengeQuestionText.Text))
            {
                FocusChallengeQuestionText.Text = Strings.CurrentLanguage == "vi"
                    ? "Đang lấy bài toán mở khoá..."
                    : "Loading the unlock challenge...";
            }
            _ = EnsureFocusChallengeAsync();
        }
    }

    private void OnAppWindowClosing(
        Microsoft.UI.Windowing.AppWindow sender,
        Microsoft.UI.Windowing.AppWindowClosingEventArgs args)
    {
        if (!FocusLockPolicy.ShouldBlockClose(_focusLocked)) return;

        args.Cancel = true;
        ShowBanner(
            Strings.CurrentLanguage == "vi"
                ? "Không thể đóng cửa sổ khi Focus Mode đang bật."
                : "Cannot close the window while Focus Mode is active.",
            true);
    }

    /// <summary>Keeps the lock in sync if the session was ended from another device.</summary>
    private async Task PollFocusStatusAsync()
    {
        if (!_isLoaded) return;
        if (string.IsNullOrWhiteSpace(_client.Settings.DeviceToken)) return;

        var (ok, active) = await _client.GetFocusStatusAsync();
        if (!ok || active == _focusLocked) return;

        if (!FocusLockPolicy.ShouldApplyPolledState(active, DateTime.UtcNow, _suppressRelockUntil))
        {
            // Stale response from a poll that started before the unlock.
            return;
        }

        ApplyFocusLock(active);
        EcosystemFocusText.Text = active
            ? "Focus"
            : (Strings.CurrentLanguage == "vi" ? "Nghỉ" : "Idle");
    }

    private async void AddNoteButton_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.CreateNoteAsync(NewNoteTextBox.Text);
        if (ok)
        {
            NewNoteTextBox.Text = string.Empty;
            RenderNotesList();
        }
        ShowBanner(msg, !ok);
    }

    public async Task ConfirmAndDeleteNoteAsync(NoteItemDto note)
    {
        var (ok, msg) = await _client.DeleteNoteAsync(note.Id);
        RenderNotesList();
        ShowBanner(msg, !ok);
    }

    private void RenderNotesList()
    {
        if (NotesListView == null) return;
        NotesListView.Items.Clear();
        foreach (var note in _client.Notes)
        {
            var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
            panel.Children.Add(new TextBlock { Text = note.Content, Width = 320, TextWrapping = TextWrapping.Wrap });
            var delBtn = new Button { Content = Strings.CurrentLanguage == "vi" ? "Xóa" : "Delete", Tag = note };
            delBtn.Click += async (s, e) =>
            {
                if (s is Button b && b.Tag is NoteItemDto n)
                {
                    await ConfirmAndDeleteNoteAsync(n);
                }
            };
            panel.Children.Add(delBtn);
            NotesListView.Items.Add(panel);
        }
    }

    private async void AddCountdownButton_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.CreateCountdownAsync(NewCountdownNameBox.Text, NewCountdownDateBox.Text);
        if (ok)
        {
            NewCountdownNameBox.Text = string.Empty;
            NewCountdownDateBox.Text = string.Empty;
            RenderCountdownsList();
        }
        ShowBanner(msg, !ok);
    }

    private void RenderCountdownsList()
    {
        if (CountdownsListView == null) return;
        CountdownsListView.Items.Clear();
        foreach (var cd in _client.Countdowns)
        {
            var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
            panel.Children.Add(new TextBlock { Text = $"{cd.Title}: {cd.DaysLeft} days left ({cd.TargetDate})", Width = 320 });
            var delBtn = new Button { Content = Strings.CurrentLanguage == "vi" ? "Xóa" : "Delete", Tag = cd };
            delBtn.Click += async (s, e) =>
            {
                if (s is Button b && b.Tag is CountdownItemDto c)
                {
                    var (ok, msg) = await _client.DeleteCountdownAsync(c.Id);
                    RenderCountdownsList();
                    ShowBanner(msg, !ok);
                }
            };
            panel.Children.Add(delBtn);
            CountdownsListView.Items.Add(panel);
        }
    }

    private void RenderWeatherGallery()
    {
        if (WeatherGalleryStack == null) return;
        WeatherGalleryStack.Children.Clear();
        if (_client.WeatherIntervals.Count == 0)
        {
            if (WeatherEmptyText != null) WeatherEmptyText.Visibility = Visibility.Visible;
            return;
        }

        if (WeatherEmptyText != null) WeatherEmptyText.Visibility = Visibility.Collapsed;
        foreach (var iv in _client.WeatherIntervals)
        {
            var card = new Border
            {
                Background = new SolidColorBrush(Windows.UI.Color.FromArgb(20, 255, 255, 255)),
                BorderBrush = new SolidColorBrush(Windows.UI.Color.FromArgb(30, 255, 255, 255)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(10),
                Padding = new Thickness(12, 10, 12, 10),
                MinWidth = 105
            };

            var sp = new StackPanel { Spacing = 4, HorizontalAlignment = HorizontalAlignment.Center };
            string timeShort = iv.DisplayTime.Contains(" ") ? iv.DisplayTime.Substring(iv.DisplayTime.LastIndexOf(' ') + 1) : iv.DisplayTime;
            sp.Children.Add(new TextBlock { Text = timeShort, FontSize = 11, FontWeight = Microsoft.UI.Text.FontWeights.Bold, Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 148, 163, 184)), HorizontalAlignment = HorizontalAlignment.Center });
            sp.Children.Add(new TextBlock { Text = $"{iv.Temp:F1}°C", FontSize = 16, FontWeight = Microsoft.UI.Text.FontWeights.Bold, Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 0, 229, 255)), HorizontalAlignment = HorizontalAlignment.Center });
            sp.Children.Add(new TextBlock { Text = iv.DisplayDescription, FontSize = 10, MaxLines = 1, TextTrimming = TextTrimming.CharacterEllipsis, HorizontalAlignment = HorizontalAlignment.Center });

            var rainBadge = new Border
            {
                Background = new SolidColorBrush(Windows.UI.Color.FromArgb(40, 0, 180, 250)),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(6, 2, 6, 2),
                HorizontalAlignment = HorizontalAlignment.Center
            };
            rainBadge.Child = new TextBlock { Text = $"Mưa: {iv.EffectiveRainProb:F0}%", FontSize = 10, Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 56, 189, 248)) };
            sp.Children.Add(rainBadge);

            card.Child = sp;
            WeatherGalleryStack.Children.Add(card);
        }
    }

    // =========================================================================
    // LIVE BIDIRECTIONAL MORSE CODE SYNC WITH RE-ENTRANCY GUARD
    // =========================================================================
    private bool _isUpdatingMorse = false;

    private void MorsePlainTextBox_TextChanged(object sender, TextChangedEventArgs e)
    {
        if (_isUpdatingMorse) return;
        _isUpdatingMorse = true;
        try
        {
            MorsePatternTextBox.Text = MorseCodeConverter.TextToMorse(MorsePlainTextBox.Text);
        }
        catch {}
        finally
        {
            _isUpdatingMorse = false;
        }
    }

    private void MorsePatternTextBox_TextChanged(object sender, TextChangedEventArgs e)
    {
        if (_isUpdatingMorse) return;
        _isUpdatingMorse = true;
        try
        {
            MorsePlainTextBox.Text = MorseCodeConverter.MorseToText(MorsePatternTextBox.Text);
        }
        catch {}
        finally
        {
            _isUpdatingMorse = false;
        }
    }

    private void _appendMorseToken(string token)
    {
        _isUpdatingMorse = true;
        try
        {
            MorsePatternTextBox.Text = (MorsePatternTextBox.Text ?? string.Empty) + token;
            MorsePlainTextBox.Text = MorseCodeConverter.MorseToText(MorsePatternTextBox.Text);
        }
        catch {}
        finally
        {
            _isUpdatingMorse = false;
        }
    }

    private void MorseDot_Click(object sender, RoutedEventArgs e) => _appendMorseToken(".");
    private void MorseDash_Click(object sender, RoutedEventArgs e) => _appendMorseToken("-");
    private void MorseSpace_Click(object sender, RoutedEventArgs e) => _appendMorseToken(" ");
    private void MorseSlash_Click(object sender, RoutedEventArgs e) => _appendMorseToken(" / ");

    private void MorseBackspace_Click(object sender, RoutedEventArgs e)
    {
        _isUpdatingMorse = true;
        try
        {
            var cur = MorsePatternTextBox.Text ?? string.Empty;
            if (cur.Length > 0)
            {
                MorsePatternTextBox.Text = cur.Substring(0, cur.Length - 1);
                MorsePlainTextBox.Text = MorseCodeConverter.MorseToText(MorsePatternTextBox.Text);
            }
        }
        catch {}
        finally
        {
            _isUpdatingMorse = false;
        }
    }

    private void MorseClear_Click(object sender, RoutedEventArgs e)
    {
        _isUpdatingMorse = true;
        try
        {
            MorsePatternTextBox.Text = string.Empty;
            MorsePlainTextBox.Text = string.Empty;
        }
        finally
        {
            _isUpdatingMorse = false;
        }
    }

    private void CopyMorse_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var dp = new Windows.ApplicationModel.DataTransfer.DataPackage();
            dp.SetText(MorsePatternTextBox.Text ?? string.Empty);
            Windows.ApplicationModel.DataTransfer.Clipboard.SetContent(dp);
            ShowBanner(Strings.CurrentLanguage == "vi" ? "Đã sao chép mã Morse!" : "Copied Morse code!");
        }
        catch {}
    }

    private void CopyText_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var dp = new Windows.ApplicationModel.DataTransfer.DataPackage();
            dp.SetText(MorsePlainTextBox.Text ?? string.Empty);
            Windows.ApplicationModel.DataTransfer.Clipboard.SetContent(dp);
            ShowBanner(Strings.CurrentLanguage == "vi" ? "Đã sao chép văn bản!" : "Copied text!");
        }
        catch {}
    }

    private async void StopMorse_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.StopMorseAsync();
        ShowBanner(msg, !ok);
    }

    private void TextToMorse_Click(object sender, RoutedEventArgs e)
    {
        _isUpdatingMorse = true;
        try
        {
            MorsePatternTextBox.Text = MorseCodeConverter.TextToMorse(MorsePlainTextBox.Text);
        }
        finally { _isUpdatingMorse = false; }
    }

    private void MorseToText_Click(object sender, RoutedEventArgs e)
    {
        _isUpdatingMorse = true;
        try
        {
            MorsePlainTextBox.Text = MorseCodeConverter.MorseToText(MorsePatternTextBox.Text);
        }
        finally { _isUpdatingMorse = false; }
    }

    private async void PlayMorse_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.PlayMorseAsync(MorsePatternTextBox.Text);
        ShowBanner(msg, !ok);
    }

    private async void PlayMelodyPreset_Click(object sender, RoutedEventArgs e)
    {
        string preset = "mario";
        if (MelodyPresetCombo?.SelectedItem is ComboBoxItem item && item.Tag is string tag)
        {
            preset = tag;
        }
        var (ok, msg) = await _client.PlayMelodyPresetAsync(preset);
        ShowBanner(msg, !ok);
    }

    private async void SelectMidiFile_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var picker = new Windows.Storage.Pickers.FileOpenPicker();
            picker.ViewMode = Windows.Storage.Pickers.PickerViewMode.List;
            picker.SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.Downloads;
            picker.FileTypeFilter.Add(".mid");
            picker.FileTypeFilter.Add(".midi");

            var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);
            WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);

            var file = await picker.PickSingleFileAsync();
            if (file != null)
            {
                using var stream = await file.OpenStreamForReadAsync();
                using var ms = new System.IO.MemoryStream();
                await stream.CopyToAsync(ms);
                byte[] bytes = ms.ToArray();

                ShowBanner(Strings.CurrentLanguage == "vi" ? $"Đang gửi và phát file MIDI '{file.Name}'..." : $"Sending and playing MIDI '{file.Name}'...");
                var (ok, msg) = await _client.PlayMidiFileAsync(bytes, file.Name);
                ShowBanner(msg, !ok);
            }
        }
        catch (Exception ex)
        {
            ShowBanner(ex.Message, true);
        }
    }

    private async void StopMelody_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.StopMelodyAsync();
        ShowBanner(msg, !ok);
    }

    private int _previewClockStyle = 1;

    private async void SelectClockStyle_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tagStr)
        {
            if (int.TryParse(tagStr, out int styleNum))
            {
                _previewClockStyle = styleNum;
                _previewOledView = "clock";
                UpdateOledMonitorPreview();
                var (ok, msg) = await _client.SetClockStyleAsync(styleNum);
                ShowBanner(msg, !ok);
            }
            else
            {
                _previewOledView = "clock";
                UpdateOledMonitorPreview();
                var (ok, msg) = await _client.SetClockStyleAsync(tagStr);
                ShowBanner(msg, !ok);
            }
        }
    }

    private async void ApplyOledPreview_Click(object sender, RoutedEventArgs e)
    {
        if (_previewOledView == "clock")
        {
            var (ok1, msg1) = await _client.SetClockStyleAsync(_previewClockStyle);
            if (!ok1) { ShowBanner(msg1, true); return; }
            var (ok2, msg2) = await _client.SwitchDisplayViewAsync("clock");
            ShowBanner(msg2, !ok2);
        }
        else
        {
            var (ok, msg) = await _client.SwitchDisplayViewAsync(_previewOledView);
            ShowBanner(msg, !ok);
        }
    }

    private void UpdateOledMonitorPreview()
    {
        if (OledModeBadge == null || OledScreenMain == null) return;
        if (_previewOledView == "clock")
        {
            OledModeBadge.Text = $"WATCH FACE #{_previewClockStyle}";
            switch (_previewClockStyle)
            {
                case 2:
                    OledScreenTitle.Text = "MINIMALIST CLOCK";
                    OledScreenMain.Text = "12:30:45";
                    OledScreenSub.Text = "Sun, 08 Nov • 28°C";
                    break;
                case 3:
                    OledScreenTitle.Text = "CYBER STATION";
                    OledScreenMain.Text = "[ 12:30 ]";
                    OledScreenSub.Text = "SEC: 45 • NEON GLOW";
                    break;
                case 4:
                    OledScreenTitle.Text = "ANALOG DIAL";
                    OledScreenMain.Text = "(● 12:30:45)";
                    OledScreenSub.Text = "12h Dial Mode";
                    break;
                case 5:
                    OledScreenTitle.Text = "FLIP CLOCK";
                    OledScreenMain.Text = "[ 12 ] : [ 30 ]";
                    OledScreenSub.Text = "Retro Cards";
                    break;
                case 6:
                    OledScreenTitle.Text = "BINARY MATRIX";
                    OledScreenMain.Text = ":: :: :: ::";
                    OledScreenSub.Text = "BCD 4x4 LED";
                    break;
                case 7:
                    OledScreenTitle.Text = "WORD CLOCK";
                    OledScreenMain.Text = "TWELVE 30";
                    OledScreenSub.Text = "Spelled Text";
                    break;
                default:
                    OledScreenTitle.Text = "DESKFLOW CLOCK";
                    OledScreenMain.Text = "12:30 :45";
                    OledScreenSub.Text = "SUN 08 NOV • 28°C HCM";
                    break;
            }
        }
        else
        {
            OledModeBadge.Text = _previewOledView.ToUpper();
            OledScreenTitle.Text = $"VIEW: {_previewOledView.ToUpper()}";
            OledScreenMain.Text = "STATION ONLINE";
            OledScreenSub.Text = "DeskFlow TFT 320×240";
        }
    }

    private async void SwitchViewButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string view)
        {
            _previewOledView = view;
            UpdateOledMonitorPreview();
            var (ok, msg) = await _client.SwitchDisplayViewAsync(view);
            ShowBanner(msg, !ok);
        }
    }

    private bool _suppressThemeComboSave;
    private string _previewOledView = "clock";
    private string _liveStationView = "";

    private void ThemeModeCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_isLoaded || _client == null || _suppressThemeComboSave) return;
        if (ThemeModeCombo?.SelectedItem is not ComboBoxItem) return;
        if (ThemeModeCombo.SelectedItem is ComboBoxItem item && item.Tag is string theme)
        {
            _client.Settings.AppTheme = theme;
            _client.Settings.Save();
            ApplyEffectiveTheme();
        }
    }

    private void SaveScheduleButton_Click(object sender, RoutedEventArgs e)
    {
        _client.Settings.DarkModeStart = ScheduledDarkModeEvaluator.NormalizeTimeStr(DarkStartTextBox.Text, "20:00");
        _client.Settings.DarkModeEnd = ScheduledDarkModeEvaluator.NormalizeTimeStr(DarkEndTextBox.Text, "06:00");
        _client.Settings.Save();
        ApplyEffectiveTheme();
        ShowBanner(Strings.CurrentLanguage == "vi" ? "Đã lưu lịch hẹn giờ chế độ tối!" : "Scheduled dark mode saved!");
    }

    private async void TestHealthButton_Click(object sender, RoutedEventArgs e)
    {
        _client.Settings.ApiUrl = ApiUrlTextBox.Text.Trim();
        _client.Settings.Save();
        var (ok, msg) = await _client.CheckHealthAsync();
        ShowBanner($"Health: {msg}", !ok);
    }

    private async void RefreshAlarms()
    {
        var (ok, alarms, msg) = await _client.GetAlarmsAsync();
        if (ok && alarms != null)
        {
            AlarmsListView.ItemsSource = null;
            AlarmsListView.ItemsSource = alarms;
        }
    }

    private void RefreshAlarmsButton_Click(object sender, RoutedEventArgs e) => RefreshAlarms();

    private async void SaveAlarmButton_Click(object sender, RoutedEventArgs e)
    {
        int dur = int.TryParse(AlarmDurationBox.Text, out int d) ? d : 250;
        var (ok, msg) = await _client.SaveAlarmAsync(AlarmTimeBox.Text, AlarmLabelBox.Text, new List<int> { 1, 2, 3, 4, 5 }, dur);
        ShowBanner(msg, !ok);
        if (ok) RefreshAlarms();
    }

    private async void AlarmToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (sender is ToggleSwitch ts && ts.Tag is string id)
        {
            var (ok, msg) = await _client.ToggleAlarmAsync(id, ts.IsOn);
            ShowBanner(msg, !ok);
        }
    }

    private async void DeleteAlarmButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string id)
        {
            var (ok, msg) = await _client.DeleteAlarmAsync(id);
            ShowBanner(msg, !ok);
            if (ok) RefreshAlarms();
        }
    }

    private async void SetLampColorButton_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.SetLampColorAsync(LampHexBox.Text);
        ShowBanner(msg, !ok);
    }

    private async void PresetLampColor_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string hex)
        {
            LampHexBox.Text = hex;
            var (ok, msg) = await _client.SetLampColorAsync(hex);
            ShowBanner(msg, !ok);
        }
    }

    private async void TurnLampOnButton_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.SetLedAsync("on");
        ShowBanner(msg, !ok);
    }

    private int _lastLedBrightness = 100;

    private void LedBrightnessSlider_ValueChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e)
    {
        int value = (int)Math.Round(e.NewValue);
        if (LedBrightnessText != null) LedBrightnessText.Text = $"{value}%";
        if (value == _lastLedBrightness) return;
        _lastLedBrightness = value;
        _ = _client.SetBrightnessAsync(value);
    }

    private async void TurnLampOffButton_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.TurnLampOffAsync();
        ShowBanner(msg, !ok);
    }

    private async void PresetBuzz_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string msStr && int.TryParse(msStr, out int ms))
        {
            var (ok, msg) = await _client.TriggerBuzzAsync(ms);
            ShowBanner(msg, !ok);
        }
    }

    private async void EmergencyStop_Click(object sender, RoutedEventArgs e)
    {
        var (ok, msg) = await _client.EmergencyStopAlarmAsync();
        ShowBanner(msg, !ok);
    }

    private async void RefreshDevices()
    {
        var (ok, devices, msg) = await _client.GetPairedDevicesAsync();
        if (ok && devices != null)
        {
            _devicesCache.Clear();
            _devicesCache.AddRange(devices);
            DevicesListView.ItemsSource = null;
            DevicesListView.ItemsSource = _devicesCache;

            DisplayDeviceCombo.Items.Clear();
            int selectedIndex = 0;
            string? selId = await _client.GetSelectedDisplayDeviceAsync();

            for (int i = 0; i < _devicesCache.Count; i++)
            {
                var d = _devicesCache[i];
                var cbi = new ComboBoxItem { Content = $"{d.DeviceName} ({d.Platform})", Tag = d.DeviceId };
                DisplayDeviceCombo.Items.Add(cbi);
                if (d.DeviceId == selId) selectedIndex = i;
            }
            if (DisplayDeviceCombo.Items.Count > 0)
                DisplayDeviceCombo.SelectedIndex = selectedIndex;
        }
    }

    private void RefreshDevicesButton_Click(object sender, RoutedEventArgs e) => RefreshDevices();

    private async void DisplayDeviceCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_isLoaded || _client == null) return;
        if (DisplayDeviceCombo.SelectedItem is ComboBoxItem item && item.Tag is string devId)
        {
            var (ok, msg) = await _client.SetSelectedDisplayDeviceAsync(devId);
            ShowBanner(msg, !ok);
        }
    }

    private async void RenameDeviceButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string devId)
        {
            var dev = _devicesCache.Find(d => d.DeviceId == devId);
            var dialog = new ContentDialog
            {
                Title = Strings.CurrentLanguage == "vi" ? "Đổi tên thiết bị" : "Rename Device",
                PrimaryButtonText = Strings.CurrentLanguage == "vi" ? "Lưu tên" : "Save",
                CloseButtonText = Strings.CurrentLanguage == "vi" ? "Hủy" : "Cancel",
                XamlRoot = this.Content.XamlRoot
            };

            var input = new TextBox { Text = dev?.DeviceName ?? "Device", Width = 260 };
            dialog.Content = input;

            var res = await dialog.ShowAsync();
            if (res == ContentDialogResult.Primary && !string.IsNullOrWhiteSpace(input.Text))
            {
                var (ok, msg) = await _client.RenameDeviceAsync(devId, input.Text.Trim());
                ShowBanner(msg, !ok);
                if (ok) RefreshDevices();
            }
        }
    }

    private async void UnlinkDeviceButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string devId)
        {
            var (ok, msg) = await _client.UnpairSpecificDeviceAsync(devId);
            ShowBanner(msg, !ok);
            if (ok) RefreshDevices();
        }
    }

    public void ApplyLanguage()
    {
        bool isVi = Strings.CurrentLanguage == "vi";

        if (NavSystemItem != null) NavSystemItem.Content = isVi ? "Giám sát hệ thống & Đo từ xa" : "System & Telemetry";
        if (NavStationItem != null) NavStationItem.Content = isVi ? "Phần cứng & Trạm ESP32" : "Hardware & ESP32 Station";
        if (NavSpotifyItem != null) NavSpotifyItem.Content = isVi ? "Spotify & Âm nhạc" : "Spotify & Media";
        if (NavAlarmsItem != null) NavAlarmsItem.Content = isVi ? "Báo thức & Còi báo" : "Alarms & Buzzer";
        if (NavToolsItem != null) NavToolsItem.Content = isVi ? "Công cụ & Groq AI" : "Tools & Groq AI";
        if (NavSettingsItem != null) NavSettingsItem.Content = isVi ? "Cài đặt & Giao diện" : "Settings & Appearance";

        if (HeaderTitleText != null) HeaderTitleText.Text = isVi ? "Trung tâm điều khiển DeskFlow Windows" : "DeskFlow Windows Control Center";
        if (SpotifyHeaderTitle != null) SpotifyHeaderTitle.Text = isVi ? "Spotify & Âm nhạc" : "Spotify & Media";
        if (PairingSectionTitle != null) PairingSectionTitle.Text = isVi ? "Xác thực & Liên kết thiết bị" : "Device Authentication & Pairing";
        if (PairBtn != null) PairBtn.Content = isVi ? "Liên kết thiết bị" : "Pair Device";
        if (UnpairBtn != null) UnpairBtn.Content = isVi ? "Hủy liên kết" : "Unpair";
        if (RefreshAllBtn != null) RefreshAllBtn.Content = isVi ? "Làm mới dữ liệu" : "Refresh All";

        if (EspStationSectionTitle != null) EspStationSectionTitle.Text = isVi ? "Thông số ESP32 Station" : "ESP32 Station Metrics";
        if (ServerSectionTitle != null) ServerSectionTitle.Text = isVi ? "Thông số Server Companion" : "Companion Server Metrics";
        if (QuoteSectionTitle != null) QuoteSectionTitle.Text = isVi ? "Danh ngôn tạo động lực (Groq AI Quotes)" : "Motivational AI Quotes (Groq AI)";
        if (WeatherSectionTitle != null) WeatherSectionTitle.Text = isVi ? "Dự báo thời tiết 48h (Weather Forecast)" : "48-Hour Weather Forecast";
        if (EcosystemSectionTitle != null) EcosystemSectionTitle.Text = isVi ? "Tổng quan hệ sinh thái" : "Ecosystem Summary";

        if (GateTitleText != null) GateTitleText.Text = isVi ? "Yêu cầu Ghép nối Thiết bị" : "Device Pairing Required";
        if (GateSubtitleText != null) GateSubtitleText.Text = isVi ? "Để sử dụng Trung tâm điều khiển DeskFlow, bạn phải ghép nối máy tính này với tài khoản hệ sinh thái." : "To access the DeskFlow Control Center, you must pair this PC with your DeskFlow ecosystem.";
        if (GateInstructionsText != null) GateInstructionsText.Text = isVi ? "Gõ lệnh /app pair trên Discord hoặc mở app DeskFlow trên điện thoại để nhận mã ghép nối 8 ký tự:" : "Type /app pair in Discord or open the DeskFlow mobile app to get your 8-char pairing code:";
        if (GatePairButton != null) GatePairButton.Content = isVi ? "Ghép nối thiết bị" : "Pair Device";
        if (GateServerUrlText != null) GateServerUrlText.Text = isVi ? $"Máy chủ: {_client.Settings.ApiUrl}" : $"Server: {_client.Settings.ApiUrl}";

        // Retranslate the hardcoded labels in MainWindow.xaml (the XAML keeps
        // Vietnamese as the source of truth; this swaps the whole tree).
        if (Content is DependencyObject rootVisual)
        {
            UiTranslator.Apply(rootVisual, isVi);
        }

        UpdatePairingStatusLabel();
    }

    private SpotifyStatusDto? _lastSpotify;
    private bool _isSpotifyPlaying;
    private DateTime _lastSpotifySync = DateTime.MinValue;

    private async Task RefreshSpotifyAsync()
    {
        try
        {
            var sp = await _client.GetSpotifyStatusAsync();
            if (sp != null)
            {
                _lastSpotify = sp;
                UpdateSpotifyUI(sp);
            }
        }
        catch {}
    }

    private string? _loadedCoverUrl;

    private void ShowCoverPlaceholder()
    {
        if (SpotifyCoverImage != null)
        {
            SpotifyCoverImage.Source = null;
            SpotifyCoverImage.Visibility = Visibility.Collapsed;
        }
        if (SpotifyCoverPlaceholder != null)
        {
            SpotifyCoverPlaceholder.Visibility = Visibility.Visible;
        }
    }

    private void UpdateSpotifyCover(string? coverUrl)
    {
        bool hasCover = Uri.TryCreate(coverUrl, UriKind.Absolute, out var uri) &&
                        (uri.Scheme == Uri.UriSchemeHttps || uri.Scheme == Uri.UriSchemeHttp);

        // Avoid reloading the same artwork on every periodic refresh.
        if (hasCover && string.Equals(coverUrl, _loadedCoverUrl, StringComparison.Ordinal) &&
            SpotifyCoverImage?.Source != null)
        {
            return;
        }

        if (!hasCover || uri == null)
        {
            _loadedCoverUrl = null;
            ShowCoverPlaceholder();
            return;
        }

        try
        {
            _loadedCoverUrl = coverUrl;
            SpotifyCoverImage!.Source = new BitmapImage(uri);
        }
        catch
        {
            _loadedCoverUrl = null;
            ShowCoverPlaceholder();
        }
    }

    private void SpotifyCoverImage_ImageOpened(object sender, RoutedEventArgs e)
    {
        if (SpotifyCoverImage != null) SpotifyCoverImage.Visibility = Visibility.Visible;
        if (SpotifyCoverPlaceholder != null) SpotifyCoverPlaceholder.Visibility = Visibility.Collapsed;
    }

    private void SpotifyCoverImage_ImageFailed(object sender, RoutedEventArgs e)
    {
        _loadedCoverUrl = null;
        ShowCoverPlaceholder();
    }

    private void UpdateSpotifyUI(SpotifyStatusDto sp)
    {
        _isSpotifyPlaying = sp.IsPlaying;
        string track = string.IsNullOrWhiteSpace(sp.Track) ? (Strings.CurrentLanguage == "vi" ? "Chưa phát nhạc" : "Not Playing") : sp.Track;
        string artist = string.IsNullOrWhiteSpace(sp.Artist) ? "Spotify Desktop" : sp.Artist;
        string statusText = sp.IsPlaying ? (Strings.CurrentLanguage == "vi" ? "ĐANG PHÁT" : "NOW PLAYING") : (Strings.CurrentLanguage == "vi" ? "TẠM DỪNG" : "PAUSED");

        // Mini player in Dashboard
        if (MiniSpotifyTrackText != null) MiniSpotifyTrackText.Text = track;
        if (MiniSpotifyArtistText != null) MiniSpotifyArtistText.Text = $"{artist} • {statusText}";
        if (MiniSpotifyPlayBtn != null) MiniSpotifyPlayBtn.Content = sp.IsPlaying ? "\uE769" : "\uE768";

        _lastSpotifySync = DateTime.Now;

        // Full Spotify Studio
        UpdateSpotifyCover(sp.CoverUrl);
        if (SpotifyTrackTitle != null) SpotifyTrackTitle.Text = track;
        if (SpotifyArtistName != null) SpotifyArtistName.Text = artist;
        if (SpotifyAlbumName != null) SpotifyAlbumName.Text = sp.Album ?? "";
        if (SpotifyStatusBadge != null) SpotifyStatusBadge.Text = statusText;
        if (SpotifyPlayBtn != null) SpotifyPlayBtn.Content = sp.IsPlaying ? "\uE769" : "\uE768";
        if (SpotifyProgressBar != null) SpotifyProgressBar.Value = Math.Clamp(sp.ProgressPct, 0, 100);

        long pSec = Math.Max(0, sp.ProgressMs / 1000);
        long dSec = Math.Max(0, sp.DurationMs / 1000);
        if (SpotifyCurrentTimeText != null) SpotifyCurrentTimeText.Text = $"{pSec / 60:D2}:{pSec % 60:D2}";
        if (SpotifyTotalTimeText != null) SpotifyTotalTimeText.Text = $"{dSec / 60:D2}:{dSec % 60:D2}";

        if (SpotifyCurrentLyricText != null)
            SpotifyCurrentLyricText.Text = string.IsNullOrWhiteSpace(sp.CurrentLyric) ? (Strings.CurrentLanguage == "vi" ? "Đang đồng bộ lời bài hát từ Spotify / LRCLIB..." : "Syncing lyrics from Spotify / LRCLIB...") : sp.CurrentLyric;
        if (SpotifyNextLyricText != null)
            SpotifyNextLyricText.Text = sp.NextLyric ?? "...";
    }

    private void InterpolateSpotifyProgress()
    {
        var sp = _lastSpotify;
        if (sp == null || !sp.IsPlaying || sp.DurationMs <= 0) return;
        if (_lastSpotifySync == DateTime.MinValue) return;

        long elapsed = (long)(DateTime.Now - _lastSpotifySync).TotalMilliseconds;
        long pos = Math.Clamp(sp.ProgressMs + elapsed, 0, sp.DurationMs);

        if (SpotifyProgressBar != null)
        {
            SpotifyProgressBar.Value = Math.Clamp(pos * 100.0 / sp.DurationMs, 0, 100);
        }
        if (SpotifyCurrentTimeText != null)
        {
            long sec = pos / 1000;
            SpotifyCurrentTimeText.Text = $"{sec / 60:D2}:{sec % 60:D2}";
        }
    }

    private async void SpotifyPlayToggle_Click(object sender, RoutedEventArgs e)
    {
        string cmd = _isSpotifyPlaying ? "pause" : "play";
        await _client.ControlSpotifyPlaybackAsync(cmd);
        _ = RefreshSpotifyAsync();
    }

    private async void SpotifyPrev_Click(object sender, RoutedEventArgs e)
    {
        await _client.ControlSpotifyPlaybackAsync("previous");
        _ = RefreshSpotifyAsync();
    }

    private async void SpotifyNext_Click(object sender, RoutedEventArgs e)
    {
        await _client.ControlSpotifyPlaybackAsync("next");
        _ = RefreshSpotifyAsync();
    }

    private void SpotifyVolumeSlider_ValueChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e)
    {
        try
        {
            int vol = (int)e.NewValue;
            if (SpotifyVolumeText != null) SpotifyVolumeText.Text = $"{vol}%";
            if (!_isLoaded || _client == null) return;
            _ = _client.SetSpotifyVolumeAsync(vol);
        }
        catch {}
    }

    private void RefreshSpotify_Click(object sender, RoutedEventArgs e)
    {
        _ = RefreshSpotifyAsync();
    }

    private void OpenSpotifyStudio_Click(object sender, RoutedEventArgs e)
    {
        MainNavView.SelectedItem = NavSpotifyItem;
    }

    private void LampHexBox_TextChanged(object sender, TextChangedEventArgs e)
    {
        try
        {
            var hex = LampHexBox.Text.Trim();
            if (hex.StartsWith("#") && hex.Length == 7)
            {
                byte r = Convert.ToByte(hex.Substring(1, 2), 16);
                byte g = Convert.ToByte(hex.Substring(3, 2), 16);
                byte b = Convert.ToByte(hex.Substring(5, 2), 16);
                LampColorSwatch.Background = new SolidColorBrush(Windows.UI.Color.FromArgb(255, r, g, b));
            }
        }
        catch {}
    }

    private void LampColorPicker_ColorChanged(ColorPicker sender, ColorChangedEventArgs args)
    {
        try
        {
            var c = args.NewColor;
            string hex = $"#{c.R:X2}{c.G:X2}{c.B:X2}";
            if (LampHexBox != null) LampHexBox.Text = hex;
            if (!_isLoaded || _client == null) return;
            _ = _client.SetLampColorAsync(hex);
        }
        catch {}
    }

    private async void GenerateQuote_Click(object sender, RoutedEventArgs e)
    {
        GenerateQuoteBtn.IsEnabled = false;
        AiQuoteText.Text = Strings.CurrentLanguage == "vi" ? "Đang tạo danh ngôn từ Groq AI..." : "Generating quote from Groq AI...";
        string topic = QuoteTopicBox?.Text?.Trim() ?? "học tập và kiên trì";
        if (string.IsNullOrEmpty(topic)) topic = "học tập và kiên trì";
        var (ok, quote, author) = await _client.GenerateQuoteAsync(topic);
        GenerateQuoteBtn.IsEnabled = true;
        if (ok && !string.IsNullOrWhiteSpace(quote))
        {
            AiQuoteText.Text = $"“{quote}”";
            AiQuoteAuthor.Text = $"— {author}";
            ShowBanner(Strings.CurrentLanguage == "vi" ? "Đã tạo danh ngôn mới từ Groq AI!" : "Generated new motivational quote!");
        }
        else
        {
            AiQuoteText.Text = "“Kiên trì hôm nay, thành công ngày mai!”";
            AiQuoteAuthor.Text = "— Groq AI";
            ShowBanner(Strings.CurrentLanguage == "vi" ? "Không thể tạo danh ngôn." : "Could not generate quote.", isError: true);
        }
    }

    private async void PinQuote_Click(object sender, RoutedEventArgs e)
    {
        var text = (AiQuoteText.Text ?? string.Empty).Trim(' ', '“', '”', '"');
        if (string.IsNullOrWhiteSpace(text)) return;

        // Create AND pin in one call: creating a note alone left the station
        // screen unchanged, which looked like "pin does not work".
        var (created, createMsg) = await _client.CreateNoteAsync(text, pinToStation: true);
        if (!created)
        {
            ShowBanner(createMsg, true);
            return;
        }

        ShowBanner(Strings.CurrentLanguage == "vi" ? "Đã ghim danh ngôn lên màn hình ESP32!" : "Quote pinned to ESP32 Station!", false);
        RenderNotesList();
    }

    private void LanguageCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_isLoaded || _client == null) return;
        if (LanguageCombo.SelectedItem is ComboBoxItem item && item.Tag is string lang)
        {
            _client.Settings.Language = lang;
            _client.Settings.Save();
            Strings.CurrentLanguage = lang;
            ApplyLanguage();
            ShowBanner(lang == "vi" ? "Đã chuyển ngôn ngữ sang Tiếng Việt" : "Language switched to English");
        }
    }

    private bool _suppressEsp32LanguageSave;

    private async void Esp32LanguageCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_isLoaded || _client == null || _suppressEsp32LanguageSave) return;
        if (Esp32LanguageCombo.SelectedItem is ComboBoxItem item && item.Tag is string lang)
        {
            _client.Settings.Esp32Language = lang;
            _client.Settings.Save();
            var (ok, msg) = await _client.SetClockLanguageAsync(lang);
            ShowBanner(msg, !ok);
        }
    }

    private async void ApplyEsp32Theme_Click(object sender, RoutedEventArgs e)
    {
        string mode = "scheduled";
        if (Esp32ThemeCombo.SelectedItem is ComboBoxItem item && item.Tag is string m)
        {
            mode = m;
        }
        var (ok, msg) = await _client.SetEsp32ThemeAsync(mode, _client.Settings.DarkModeStart, _client.Settings.DarkModeEnd);
        ShowBanner(ok ? (Strings.CurrentLanguage == "vi" ? "Đã lưu cài đặt giao diện màn hình ESP32!" : "Saved ESP32 display theme!") : msg, !ok);
    }
}
