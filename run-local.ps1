# run-local.ps1
# Loads secrets from .env (gitignored). Copy .env.example to .env and fill in real values.
if (Test-Path ".env") {
    Get-Content ".env" | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)\s*=\s*(.+)$') {
            Set-Item "env:$($Matches[1].Trim())" $Matches[2].Trim()
        }
    }
} else {
    Write-Host "WARNING: .env file not found. Copy .env.example to .env and fill in your secrets." -ForegroundColor Yellow
}

# Non-secret defaults (override in .env if needed)
if (-not $env:MYSQL_URL)      { $env:MYSQL_URL = "jdbc:mysql://localhost:3306/evacuation_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" }
if (-not $env:MYSQL_USER)     { $env:MYSQL_USER = "root" }
if (-not $env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD = "root" }

Write-Host "Starting Evacuation System Backend..."
mvn clean spring-boot:run
