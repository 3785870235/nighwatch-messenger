# Start the team snapshot. No downloads or model calls are made by the game.
$ErrorActionPreference = 'Stop'
$academyRoot = $PSScriptRoot
$academyJava = $null
$javaCandidates = @((Join-Path $academyRoot 'runtime\bin\javaw.exe'),
    (Join-Path $academyRoot 'tools\jdk-11.0.32.1+1\bin\javaw.exe'))
if ($env:JAVA_HOME) { $javaCandidates += Join-Path $env:JAVA_HOME 'bin\javaw.exe' }
foreach ($candidate in $javaCandidates) {
    if (Test-Path -LiteralPath $candidate) { $academyJava = $candidate; break }
}
if (!$academyJava) {
    $systemJava = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($systemJava) { $academyJava = $systemJava.Source }
}
if (!$academyJava) { throw 'Java 11+ is required. Download the Windows portable ZIP from GitHub Releases.' }
$academyJar = Join-Path $academyRoot 'app\Digital.jar'
if (!(Test-Path -LiteralPath $academyJar)) { $academyJar = Join-Path $academyRoot 'digital-source\target\Digital.jar' }
if (!(Test-Path -LiteralPath $academyJar)) { throw 'Digital.jar is missing. Download the portable ZIP or build the source.' }
$academyWork = Join-Path $academyRoot 'work'
$academyProfile = Join-Path $academyWork 'profile'
New-Item -ItemType Directory -Path $academyProfile -Force | Out-Null
$academyProtected = (Join-Path $academyRoot 'app') + ';' + (Join-Path $academyRoot 'runtime')
$academyArgs = @('-Dfile.encoding=UTF-8', ('-Dacademy.protect="' + $academyProtected + '"'), ('-Dacademy.root="' + $academyRoot + '"'),
    ('-Dacademy.work="' + $academyWork + '"'), ('-Duser.home="' + $academyProfile + '"'),
    '-cp', ('"' + $academyJar + '"'), 'de.neemann.digital.academy.AcademyMain')
Start-Process -FilePath $academyJava -ArgumentList $academyArgs -WorkingDirectory $academyRoot -WindowStyle Hidden
