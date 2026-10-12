# Build with an installed Java 11+ JDK and Maven 3.x (or the local project's tools).
$ErrorActionPreference = 'Stop'
$academyRoot = Split-Path -Parent $PSScriptRoot
$localJdk = Join-Path $academyRoot 'tools\jdk-11.0.32.1+1'
if (Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME = $localJdk }
if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'Set JAVA_HOME to a Java 11+ JDK before building.'
}
$localMaven = Join-Path $academyRoot 'tools\apache-maven-3.9.16\bin\mvn.cmd'
if (Test-Path -LiteralPath $localMaven) { $maven = $localMaven }
else {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (!$command) { throw 'Install Maven 3.x and add mvn.cmd to PATH.' }
    $maven = $command.Source
}
& $maven -B -ntp -P no-git-rev '-Dgit.commit.id.describe=v0.31-816eff5-academy' -f (Join-Path $academyRoot 'digital-source\pom.xml') package
if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed.' }
New-Item -ItemType Directory -Path (Join-Path $academyRoot 'app') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $academyRoot 'digital-source\target\Digital.jar') -Destination (Join-Path $academyRoot 'app\Digital.jar')
