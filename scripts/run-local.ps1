param([string]$SecretsPath=(Join-Path $PSScriptRoot '..\.env.local'))
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$allowed=@('JAVA_HOME','DB_URL','DB_USERNAME','DB_PASSWORD','PORT','COACH_INTERNAL_API_KEY','TELEGRAM_ENABLED','TELEGRAM_BOT_TOKEN','TELEGRAM_BOT_USERNAME','TELEGRAM_WEBHOOK_SECRET','COACH_AI_ENABLED','COACH_AI_APIKEY','COACH_AI_TEXTMODEL','COACH_AI_SPEECHMODEL','COACH_AI_TRANSCRIPTIONMODEL','COACH_AI_VOICE','COACH_AI_FFMPEGEXECUTABLE','COACH_AI_MAXSPEECHOUTPUTTOKENS')
if(Test-Path -LiteralPath $SecretsPath) {
    foreach($line in Get-Content -LiteralPath $SecretsPath) {
        if($line.TrimStart().StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $parts=$line.Split('=',2)
        if($parts.Count -ne 2 -or $parts[0] -notin $allowed) { throw 'Unknown or invalid configuration entry. Use .env.example keys.' }
        [Environment]::SetEnvironmentVariable($parts[0],$parts[1],'Process')
    }
}
if(-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'Set JAVA_HOME to an installed Java 21 JDK in .env.local.' }
$env:Path="$env:JAVA_HOME\bin;$env:Path"
Push-Location $repo
try { & .\mvnw.cmd --batch-mode --no-transfer-progress spring-boot:run; if($LASTEXITCODE -ne 0) { throw 'Application exited unsuccessfully.' } }
finally { Pop-Location }
