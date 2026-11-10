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
        this.InitializeComponent();
        this.UnhandledException += (sender, e) =>
        {
            try
            {
                var folder = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Deskflow");
                System.IO.Directory.CreateDirectory(folder);
                var logFile = System.IO.Path.Combine(folder, "crash.log");
                System.IO.File.AppendAllText(logFile, $"[{DateTime.UtcNow:o}] CRASH: {e.Message}\n{e.Exception}\n");
            }
            catch {}
        };
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        _window = new MainWindow();
        _window.Activate();
    }
}
