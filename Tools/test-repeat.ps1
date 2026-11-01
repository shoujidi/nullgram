$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $projectRoot '.tools/jdk-17/bin'
if (-not (Test-Path (Join-Path $jdk 'javac.exe'))) { $jdk = Join-Path $env:JAVA_HOME 'bin' }
$output = Join-Path $projectRoot 'artifacts/repeat-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @('RepeatConfig.java', 'RepeatTaskState.java', 'RepeatTask.java', 'RepeatTaskManager.java') | ForEach-Object {
    Join-Path $projectRoot "TMessagesProj/src/main/java/com/nullgram/client/repeat/$_"
}
& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -d $output @sources (Join-Path $projectRoot 'tests/repeat/RepeatTaskManagerTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Repeat test compilation failed' }
& (Join-Path $jdk 'java.exe') -cp $output com.nullgram.client.repeat.RepeatTaskManagerTest
if ($LASTEXITCODE -ne 0) { throw 'Repeat behavior tests failed' }
