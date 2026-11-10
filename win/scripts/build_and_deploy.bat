@echo off
setlocal
echo ========================================================
echo   Deskflow Windows 11 WinUI 3 Self-Contained Builder
echo ========================================================

set "USER_DOTNET=%USERPROFILE%\.dotnet"
if exist "%USER_DOTNET%\dotnet.exe" (
    set "PATH=%USER_DOTNET%;%PATH%"
    set "DOTNET_ROOT=%USER_DOTNET%"
)

cd /d "%~dp0\.."

echo [1/3] Publishing fully Self-Contained WinUI 3 Desktop App (win-x64)...
dotnet publish DeskFlow\DeskFlow.csproj -c Release -r win-x64 -p:Platform=x64 -p:WindowsPackageType=None -p:WindowsAppSDKSelfContained=true --self-contained true -o dist\DeskFlow-win-x64
if errorlevel 1 (
    echo [ERROR] dotnet publish failed!
    pause
    exit /b 1
)

echo [2/3] Compiling Inno Setup 6 Installer...
set "ISCC=C:\Users\10th01_13\InnoSetup6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%USERPROFILE%\InnoSetup6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=C:\Program Files\Inno Setup 6\ISCC.exe"

"%ISCC%" packaging\DeskFlowInstaller.iss
if errorlevel 1 (
    echo [ERROR] Inno Setup compilation failed!
    pause
    exit /b 1
)

echo [3/3] Deploying fresh installer to Desktop...
copy /Y dist\Deskflow-1.0.0-windows-setup.exe "%USERPROFILE%\Desktop\Deskflow-1.0.0-windows-setup.exe"
echo ========================================================
echo   SUCCESS! Deskflow installer is ready on your Desktop:
echo   %USERPROFILE%\Desktop\Deskflow-1.0.0-windows-setup.exe
echo ========================================================
pause
