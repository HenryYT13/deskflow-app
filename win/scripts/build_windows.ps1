$ErrorActionPreference = "Stop"

$UserDotnet = Join-Path $env:USERPROFILE ".dotnet"
if (Test-Path (Join-Path $UserDotnet "dotnet.exe")) {
    $env:DOTNET_ROOT = $UserDotnet
    $env:PATH = "$UserDotnet;$env:PATH"
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$WinRoot = Split-Path -Parent $ScriptDir
$OutDir = Join-Path $WinRoot "dist"

Write-Host "=== [1/5] Inspecting Windows Toolchain ===" -ForegroundColor Cyan
dotnet --info

Write-Host "=== [2/5] Running Native Windows Unit & Pairing Contract Tests ===" -ForegroundColor Cyan
Push-Location $WinRoot
try {
    dotnet test .\Tests\DeskFlow.Tests.csproj -c Release --logger "console;verbosity=normal"
    if ($LASTEXITCODE -ne 0) {
        throw "dotnet test failed with exit code $LASTEXITCODE"
    }

    Write-Host "=== [3/5] Building Native WinUI 3 Desktop Application (win-x64) ===" -ForegroundColor Cyan
    if (Test-Path $OutDir) {
        Remove-Item -Recurse -Force $OutDir
    }
    New-Item -ItemType Directory -Path $OutDir | Out-Null

    dotnet publish .\DeskFlow\DeskFlow.csproj `
        -c Release `
        -r win-x64 `
        -p:Platform=x64 `
        -p:WindowsPackageType=None `
        -p:WindowsAppSDKSelfContained=true `
        --self-contained true `
        -o (Join-Path $OutDir "DeskFlow-win-x64")
    if ($LASTEXITCODE -ne 0) {
        throw "dotnet publish failed with exit code $LASTEXITCODE"
    }

    $ZipPath = Join-Path $OutDir "DeskFlow-1.0.0-win-x64.zip"
    Compress-Archive -Path (Join-Path $OutDir "DeskFlow-win-x64\*") -DestinationPath $ZipPath -Force

    Write-Host "=== [4/5] Building Native Windows Setup Installer (.exe) ===" -ForegroundColor Cyan
    $IsccCandidates = @(
        (Join-Path $env:USERPROFILE "InnoSetup6\ISCC.exe"),
        "C:\Users\10th01_13\InnoSetup6\ISCC.exe",
        "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
        "C:\Program Files\Inno Setup 6\ISCC.exe"
    )
    $IsccPath = $null
    foreach ($cand in $IsccCandidates) {
        if (Test-Path $cand) {
            $IsccPath = $cand
            break
        }
    }

    if ($IsccPath) {
        Write-Host "Using Inno Setup Compiler: $IsccPath"
        & $IsccPath (Join-Path $WinRoot "packaging\DeskFlowInstaller.iss")
        if ($LASTEXITCODE -ne 0) {
            throw "ISCC compiler failed with exit code $LASTEXITCODE"
        }
    } else {
        Write-Warning "Inno Setup compiler (ISCC.exe) not found; skipping installer creation."
    }

    Write-Host "=== [5/5] Verifying Windows Build Artifacts ===" -ForegroundColor Green
    Get-ChildItem -Path $OutDir
    Get-ChildItem -Path (Join-Path $OutDir "DeskFlow-win-x64") -Filter "DeskFlow*"
    if (Test-Path (Join-Path $OutDir "DeskFlow-1.0.0-windows-setup.exe")) {
        Get-FileHash -Path (Join-Path $OutDir "DeskFlow-1.0.0-windows-setup.exe") -Algorithm SHA256
    }
}
finally {
    Pop-Location
}
