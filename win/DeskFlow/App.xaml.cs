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
        {
            Program.WriteCrashLog("App.UnhandledException: " + e.Message, e.Exception);
            Program.ShowErrorDialog("DeskFlow App Error", $"An unhandled exception occurred:\n\n{e.Message}");
        };
        try
        {
            this.InitializeComponent();
        }
        catch (Exception ex)
        {
            Program.WriteCrashLog("App.InitializeComponent", ex);
            Program.ShowErrorDialog("DeskFlow Initialization Error", $"Failed to initialize WinUI components:\n\n{ex.Message}\n\nEnsure resources.pri is present.");
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
