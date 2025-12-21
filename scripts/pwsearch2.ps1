$body = '{"origin":"SFO","destination":"LAX","earliestDepartureDate":"2026-01-10","latestDepartureDate":"2026-01-20","maxBudget":1500,"numTravelers":1}'
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
