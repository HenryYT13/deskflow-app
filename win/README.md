# DeskFlow Windows — Native C# + WinUI 3 Desktop Application

Native Windows 10/11 desktop application for the DeskFlow ecosystem built with **C# (.NET 8) + WinUI 3 (`Microsoft.WindowsAppSDK`)**.

## Features
- Native `MicaBackdrop` and `NavigationView` with 8 feature panels.
- `ContentDialog` confirmation before note deletion + authoritative backend state sync.
- `InfoBar` status and error feedback.
- Automatic **Scheduled Dark Mode** via `DispatcherQueueTimer` and `FrameworkElement.RequestedTheme`.

## Building & Testing (Windows 10/11 + .NET 8 SDK)
```powershell
dotnet test win/Tests/DeskFlow.Tests.csproj -c Release
dotnet build win/DeskFlow/DeskFlow.csproj -c Release -p:Platform=x64 -o artifacts/windows
```
