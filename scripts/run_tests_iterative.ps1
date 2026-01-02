$tests = Get-ChildItem -Path src/test/java -Filter '*Test.java' -Recurse | Sort-Object FullName
foreach ($f in $tests) {
  $class = $f.BaseName
  Write-Host '--- Running test:' $class
  & mvn "-Dtest=$class" test -DskipITs=true
  if ($LASTEXITCODE -ne 0) {
    Write-Host '*** TEST FAILED:' $class
    exit $LASTEXITCODE
  }
}
Write-Host 'All tests completed (no failures detected in individual runs).'
