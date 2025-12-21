$body = '{"origin":{"code":"SFO","airportName":"SFO"},"destination":{"code":"LAX","airportName":"LAX"},"departureDateRange":{"start":"2026-01-10","end":"2026-01-20"},"returnDateRange":{"start":"2026-01-17","end":"2026-01-25"},"adults":1,"pageable":{"page":0,"size":10}}'
try {
  $s = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/trips/search' -Body $body -ContentType 'application/json' -TimeoutSec 30
  Write-Host 'SEARCH:'
  $s | ConvertTo-Json -Depth 5
  $url = "http://localhost:8080/api/trips/search/$($s.searchId)/options?page=0&size=10"
  $opts = Invoke-RestMethod -Uri $url -TimeoutSec 30
  Write-Host 'OPTIONS:'
  $opts | ConvertTo-Json -Depth 6
} catch {
  Write-Host 'ERROR:' $_.Exception.Message
}
