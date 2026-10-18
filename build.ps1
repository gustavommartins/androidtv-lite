<#
.SYNOPSIS
  Compila o androidtv-lite em um APK assinado, sem Gradle.

.DESCRIPTION
  Usa apenas ferramentas do Android SDK (aapt2, zipalign, apksigner), o javac do seu JDK
  e o D8. O APK sai em build\androidtv-lite.apk, assinado com uma chave de teste criada
  localmente em build\debug.keystore (nao e versionada).

.PARAMETER SdkRoot
  Pasta do Android SDK (precisa de build-tools e platforms\android-28).
  Padrao: variavel ANDROID_HOME ou ANDROID_SDK_ROOT.

.PARAMETER R8Jar
  (Opcional) Caminho de um r8.jar mais novo para usar como D8. Recomendado com JDK 22+,
  porque o D8 que vem nas build-tools 34 falha com bytecode gerado por JDKs muito novos.

.EXAMPLE
  .\build.ps1 -SdkRoot C:\Android\sdk
  .\build.ps1 -SdkRoot C:\Android\sdk -R8Jar C:\tools\r8-8.7.18.jar
#>
param(
    [string]$SdkRoot = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }),
    [string]$R8Jar
)

function Check($what) { if ($LASTEXITCODE -ne 0) { throw "$what falhou (codigo $LASTEXITCODE)" } }

if (-not $SdkRoot -or -not (Test-Path $SdkRoot)) {
    throw "Informe -SdkRoot (ou defina ANDROID_HOME) apontando para o Android SDK."
}
$bt = Get-ChildItem (Join-Path $SdkRoot "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (-not $bt) { throw "Nenhuma versao de build-tools em $SdkRoot\build-tools" }
$bt = $bt.FullName
$jar = Join-Path $SdkRoot "platforms\android-28\android.jar"
if (-not (Test-Path $jar)) { throw "Falta a plataforma android-28 ($jar)." }

$root = $PSScriptRoot
$out = Join-Path $root "build"
New-Item -ItemType Directory -Force $out | Out-Null
# Limpa a saida, mas MANTEM a chave de assinatura: com uma chave nova a cada build,
# o 'adb install -r' falharia (assinatura diferente da versao ja instalada).
Get-ChildItem $out -Force | Where-Object { $_.Name -ne "debug.keystore" } | Remove-Item -Recurse -Force
foreach ($d in "compiled", "gen", "classes", "dex") { New-Item -ItemType Directory -Force (Join-Path $out $d) | Out-Null }

Write-Host "==> Recursos (aapt2 compile/link)"
& "$bt\aapt2.exe" compile --dir (Join-Path $root "res") -o (Join-Path $out "compiled"); Check "aapt2 compile"
$flat = @(Get-ChildItem (Join-Path $out "compiled") -Filter *.flat | ForEach-Object FullName)
& "$bt\aapt2.exe" link -o (Join-Path $out "base.apk") -I $jar `
    --manifest (Join-Path $root "AndroidManifest.xml") --java (Join-Path $out "gen") `
    --min-sdk-version 26 --target-sdk-version 28 @flat; Check "aapt2 link"

Write-Host "==> Java (javac --release 8)"
$src = @(Get-ChildItem (Join-Path $root "src"), (Join-Path $out "gen") -Recurse -Filter *.java | ForEach-Object FullName)
& javac --release 8 -g:none -encoding UTF-8 -Xlint:-options -cp $jar -d (Join-Path $out "classes") @src; Check "javac"

Write-Host "==> DEX"
$cls = @(Get-ChildItem (Join-Path $out "classes") -Recurse -Filter *.class | ForEach-Object FullName)
if ($R8Jar) {
    & java -cp $R8Jar com.android.tools.r8.D8 --lib $jar --min-api 26 --output (Join-Path $out "dex") @cls
} else {
    & "$bt\d8.bat" --lib $jar --min-api 26 --output (Join-Path $out "dex") @cls
}
Check "d8"

Write-Host "==> Empacotando, alinhando e assinando"
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$unaligned = Join-Path $out "unaligned.apk"
Copy-Item (Join-Path $out "base.apk") $unaligned -Force
$zip = [IO.Compression.ZipFile]::Open($unaligned, 'Update')
[void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $out "dex\classes.dex"), "classes.dex", 'Optimal')
$zip.Dispose()
$aligned = Join-Path $out "aligned.apk"
& "$bt\zipalign.exe" -f -p 4 $unaligned $aligned; Check "zipalign"

$ks = Join-Path $out "debug.keystore"
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool -and $env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe" }
if (-not $keytool) {
    $jh = (& java -XshowSettings:properties -version 2>&1 | Select-String "java.home").ToString().Split("=")[1].Trim()
    $keytool = Join-Path $jh "bin\keytool.exe"
}
if (-not (Test-Path $ks)) {
    & $keytool -genkeypair -keystore $ks -storepass android -keypass android -alias key `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=androidtv-lite" 2>&1 | Out-Null
    Check "keytool"
}

$apk = Join-Path $out "androidtv-lite.apk"
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out $apk $aligned 2>&1 |
    Where-Object { $_ -notmatch "WARNING" }
Check "apksigner"

Write-Host ""
Write-Host "Pronto: $apk"
