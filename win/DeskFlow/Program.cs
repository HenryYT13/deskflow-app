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
    [STAThread]
    public static void Main(string[] args)
    {
        if (args.Contains("--version"))
        {
            Console.WriteLine("DeskFlow Station Windows (C# + WinUI 3) v1.0.0");
            Console.Out.Flush();
            return;
        }


        if (args.Contains("--self-test"))
        {
            var folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Deskflow");
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
