# run-local.ps1
$env:MYSQL_URL="jdbc:mysql://localhost:3306/evacuation_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="root"
$env:JWT_SECRET="EvacuationSystem2026VerySecretKeyMustBe32CharsLong"
$env:ADMIN_CODE="EvacuationAdmin2026Secret"
$env:ANTHROPIC_API_KEY="your-anthropic-api-key-here"
$env:BREVO_USER="your-brevo-smtp-user@smtp-brevo.com"
$env:BREVO_SMTP_KEY="your-brevo-smtp-key-here"
$env:MAIL_FROM="francisdahryljava@gmail.com"
$env:HMAC_SECRET="EvacSys2026@SecretHmacKey#XyZ99"

Write-Host "Starting Evacuation System Backend..."
mvn clean spring-boot:run
