$baseUrl = "http://localhost:9999"
$sessionId = "test-session-" + (Get-Random).ToString()

Write-Host "Enviando mensaje 1..." -ForegroundColor Cyan
$body1 = @{ sessionId = $sessionId; instruction = "Hola, ¿cómo estás?" } | ConvertTo-Json
$response1 = Invoke-RestMethod -Uri "$baseUrl/boris/v1/chat/completions" -Method Post -Body $body1 -ContentType "application/json"
Write-Host "Respuesta 1: $($response1.result)" -ForegroundColor Green

Start-Sleep -Seconds 2

Write-Host "`nEnviando mensaje 2..." -ForegroundColor Cyan
$body2 = @{ sessionId = $sessionId; instruction = "¿Qué puedes hacer?" } | ConvertTo-Json
$response2 = Invoke-RestMethod -Uri "$baseUrl/boris/v1/chat/completions" -Method Post -Body $body2 -ContentType "application/json"
Write-Host "Respuesta 2: $($response2.result)" -ForegroundColor Green

Write-Host "`nPrueba completada con sessionId: $sessionId" -ForegroundColor Yellow
