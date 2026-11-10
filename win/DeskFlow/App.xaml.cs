using System;
using System.Linq;
using DeskFlow.Services;
using Microsoft.UI.Xaml;

namespace DeskFlow;

public partial class App : Application
{
    private Window? _window;

    public App()
    {
        this.UnhandledException += (sender, e) =>
            Program.WriteCrashLog("App.UnhandledException: " + e.Message, e.Exception);
        try
        {
            this.InitializeComponent();
        }
        catch (Exception ex)
        {
            Program.WriteCrashLog("App.InitializeComponent", ex);
            throw;
        }
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        try
        {
            _window = new MainWindow();
            _window.Activate();
        }
        catch (Exception ex)
        {
            Program.WriteCrashLog("OnLaunched", ex);
            throw;
        }
    }
}
