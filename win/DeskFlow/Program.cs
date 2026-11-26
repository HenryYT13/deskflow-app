using System;
using System.IO;
using System.Linq;
using System.Threading;
using DeskFlow.Services;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using WinRT;

namespace DeskFlow;

public static class Program
{
    [System.Runtime.InteropServices.DllImport("user32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode)]
    private static extern int MessageBox(IntPtr hWnd, string text, string caption, uint type);

    public static void ShowErrorDialog(string title, string message)
    {
        try
        {
            MessageBox(IntPtr.Zero, message, title, 0x10);
        }
        catch { }
    }

    public static void WriteCrashLog(string source, object? ex)
    {
        try
        {
            var folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DeskFlow");
            Directory.CreateDirectory(folder);
            File.AppendAllText(Path.Combine(folder, "crash.log"), $"[{DateTime.UtcNow:o}] {source}: {ex}\n");
        }
        catch { }
    }

    [STAThread]
    public static void Main(string[] args)
    {
        AppDomain.CurrentDomain.UnhandledException += (_, e) => WriteCrashLog("AppDomain.UnhandledException", e.ExceptionObject);
        System.Threading.Tasks.TaskScheduler.UnobservedTaskException += (_, e) => WriteCrashLog("UnobservedTaskException", e.Exception);
        try
        {
            RunApp(args);
        }
        catch (Exception ex)
        {
            WriteCrashLog("Startup", ex);
            ShowErrorDialog("DeskFlow Fatal Error", $"DeskFlow encountered a fatal startup error:\n\n{ex.Message}\n\nSee details at:\n%LOCALAPPDATA%\\DeskFlow\\crash.log");
            throw;
        }
    }

    private static void RunApp(string[] args)
    {
        if (args.Contains("--version"))
        {
            Console.WriteLine("DeskFlow Station Windows (C# + WinUI 3) v2.4.7");
            Console.Out.Flush();
            return;
        }


        if (args.Contains("--self-test"))
        {
            var folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DeskFlow");
            Directory.CreateDirectory(folder);
            var logPath = Path.Combine(folder, "self-test.log");
            File.WriteAllText(logPath, $"[{DateTime.UtcNow:o}] Self-test started\n");

            var client = new DeskFlowApiClient();
            var (ok, msg) = client.CheckHealthAsync().GetAwaiter().GetResult();
            var result = $"[{DateTime.UtcNow:o}] [DeskFlow-Windows] Self-Test verified health: ok={ok}, status={msg}\n";
            File.AppendAllText(logPath, result);
            Console.WriteLine(result);
            Console.Out.Flush();
            Console.Error.Flush();
            Environment.Exit(ok ? 0 : 1);
            return;
        }

        ComWrappersSupport.InitializeComWrappers();
        Application.Start((p) =>
        {
            var context = new DispatcherQueueSynchronizationContext(DispatcherQueue.GetForCurrentThread());
            SynchronizationContext.SetSynchronizationContext(context);
            _ = new App();
        });
    }
}
