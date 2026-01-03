param(
  [string]$Profile = 'dev-no-security',
  [ValidateSet('h2','postgres')][string]$Db = 'h2',
  [switch]$Background,
  [switch]$SkipBuild,
  [switch]$Amadeus,
  [switch]$NoMl
)

function Load-EnvFile {
  param([string]$Path)
  if (Test-Path $Path) {
    Write-Host "Loading env file: $Path"
    Get-Content $Path | ForEach-Object {
      $line = $_.Trim()
      if ($line -and -not $line.StartsWith('#')) {
        $parts = $line -split '=', 2
        if ($parts.Length -ge 2) {
          $name = $parts[0].Trim()
          $value = $parts[1].Trim()
          if ($name) { Set-Item -Path ("Env:" + $name) -Value $value }
        }
      }
    }
  }
}

Write-Host "Stopping existing traveloptimizer java processes (if any)..."
$javaProcs = Get-CimInstance Win32_Process | Where-Object { ($_.Name -ieq 'java.exe' -or $_.Name -ieq 'java') -and $_.CommandLine -and $_.CommandLine -match 'traveloptimizer' }
foreach ($p in $javaProcs) {
  try {
    Write-Host "Stopping PID $($p.ProcessId) -> $($p.CommandLine)"
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
  } catch {
    Write-Warning "Failed to stop PID $($p.ProcessId): $_"
  }
}

# Load env files if present
Load-EnvFile -Path '.env.amadeus'
Load-EnvFile -Path '.env.postgres'

# Map common Postgres env names to Spring datasource vars if spring vars not present
if (-not $env:SPRING_DATASOURCE_USERNAME) {
  if ($env:POSTGRES_USER) { Set-Item Env:SPRING_DATASOURCE_USERNAME $env:POSTGRES_USER }
  elseif ($env:POSTGRES_USERNAME) { Set-Item Env:SPRING_DATASOURCE_USERNAME $env:POSTGRES_USERNAME }
  elseif ($env:DB_USER) { Set-Item Env:SPRING_DATASOURCE_USERNAME $env:DB_USER }
  elseif ($env:PGUSER) { Set-Item Env:SPRING_DATASOURCE_USERNAME $env:PGUSER }
}
if (-not $env:SPRING_DATASOURCE_PASSWORD) {
  if ($env:POSTGRES_PASSWORD) { Set-Item Env:SPRING_DATASOURCE_PASSWORD $env:POSTGRES_PASSWORD }
  elseif ($env:POSTGRES_PASS) { Set-Item Env:SPRING_DATASOURCE_PASSWORD $env:POSTGRES_PASS }
  elseif ($env:DB_PASSWORD) { Set-Item Env:SPRING_DATASOURCE_PASSWORD $env:DB_PASSWORD }
  elseif ($env:PGPASSWORD) { Set-Item Env:SPRING_DATASOURCE_PASSWORD $env:PGPASSWORD }
}

# If the data source URL isn't explicitly set, allow deriving DB name/port from .env.postgres
if (-not $env:SPRING_DATASOURCE_URL) {
  $pgDb = $env:POSTGRES_DB
  if (-not $pgDb) { $pgDb = $env:POSTGRES_DATABASE }
  $pgPort = $env:POSTGRES_PORT
  if (-not $pgPort) { $pgPort = 5432 }
  if ($pgDb) {
    Set-Item Env:SPRING_DATASOURCE_URL ("jdbc:postgresql://localhost:$pgPort/$pgDb")
  }
}
# Map generic keys to Amadeus if not set
if (-not $env:AMADEUS_API_KEY -and $env:API_KEY) { Set-Item Env:AMADEUS_API_KEY $env:API_KEY }
if (-not $env:AMADEUS_API_SECRET -and $env:API_SECRET) { Set-Item Env:AMADEUS_API_SECRET $env:API_SECRET }

# Enable Amadeus provider if -Amadeus flag or if postgres DB with Amadeus keys present
if ($Amadeus -or ($Db -eq 'postgres' -and $env:AMADEUS_API_KEY -and $env:AMADEUS_API_SECRET)) {
  Set-Item Env:TRAVEL_PROVIDERS_FLIGHTS 'amadeus'
  Write-Host "Amadeus provider enabled (TRAVEL_PROVIDERS_FLIGHTS=amadeus)"
}

# Enable ML by default unless -NoMl flag is provided
if ($NoMl) {
  Set-Item Env:ML_ENABLED 'false'
  Write-Host "ML disabled (ML_ENABLED=false)"
} else {
  Set-Item Env:ML_ENABLED 'true'
  Write-Host "ML enabled (ML_ENABLED=true)"
}

if ($SkipBuild) {
  Write-Host 'Skipping mvn build (flag -SkipBuild provided)'
} else {
  Write-Host "Building jar (mvn -DskipTests clean package)..."
  $mvn = Get-Command mvn -ErrorAction SilentlyContinue
  if (-not $mvn) {
    Write-Error "Maven (mvn) not found in PATH. Please install or add to PATH."; exit 1
  }
  $build = & mvn -DskipTests clean package
  if ($LASTEXITCODE -ne 0) { Write-Error "mvn build failed with exit code $LASTEXITCODE"; exit $LASTEXITCODE }
}

# discover jar
$jar = Get-ChildItem -Path target -Filter '*SNAPSHOT.jar' -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { Write-Error 'No SNAPSHOT jar found in target/'; exit 2 }

# Configure DB envs
if ($Db -eq 'h2') {
  Set-Item Env:SPRING_DATASOURCE_URL 'jdbc:h2:mem:traveloptimizer;DB_CLOSE_DELAY=-1;MODE=PostgreSQL'
  Set-Item Env:SPRING_DATASOURCE_USERNAME 'sa'
  Set-Item Env:SPRING_DATASOURCE_PASSWORD ''
} else {
  # postgres: expect .env.postgres or environment to provide username/password
  if (-not $env:SPRING_DATASOURCE_USERNAME -or -not $env:SPRING_DATASOURCE_PASSWORD) {
    Write-Warning 'Postgres credentials not found in environment; please set via .env.postgres or env variables.'
  }
  Set-Item Env:SPRING_DATASOURCE_URL 'jdbc:postgresql://localhost:5433/traveloptimizer'
}

# Banner
function MaskSecret($val) {
  if (-not $val) { return '<not-set>' }
  if ($val.Length -le 4) { return '****' + $val }
  return '****' + $val.Substring($val.Length - 4)
}

$jarPath = $jar.FullName
Write-Host ''
Write-Host '========================================'
Write-Host "Jar:       $jarPath"
Write-Host "Profile:   $Profile"
Write-Host "DB Mode:   $Db"
Write-Host "ML:        $($env:ML_ENABLED)"
Write-Host "Providers: $($env:TRAVEL_PROVIDERS_FLIGHTS)"
Write-Host "Amadeus Key:    $(MaskSecret $env:AMADEUS_API_KEY)"
Write-Host "Amadeus Secret: $(MaskSecret $env:AMADEUS_API_SECRET)"
Write-Host '========================================'
Write-Host ''

# set active profile
Set-Item Env:SPRING_PROFILES_ACTIVE $Profile

if ($Db -eq 'postgres') {
  # quick check if postgres reachable on 5433
  $reachable = $false
  try { $reachable = Test-NetConnection -ComputerName 'localhost' -Port 5433 -InformationLevel Quiet } catch { $reachable = $false }
  if (-not $reachable) {
    Write-Warning 'Postgres not reachable on localhost:5433.'
    $dockerCmd = 'docker compose -f docker/dev-compose.yml up -d'
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
      Write-Host "Attempting to start docker compose stack for Postgres+Redis: $dockerCmd"
      try {
        & docker compose -f docker/dev-compose.yml up -d
        Write-Host 'Docker compose start requested; waiting for Postgres to become reachable...'
        $wait = 0
        while ($wait -lt 30) {
          Start-Sleep -Seconds 2
          $wait += 2
          try { $reachable = Test-NetConnection -ComputerName 'localhost' -Port 5433 -InformationLevel Quiet } catch { $reachable = $false }
          if ($reachable) { break }
        }
        if (-not $reachable) { Write-Warning 'Postgres did not become reachable after docker compose start.' }
      } catch {
        Write-Warning "Failed to run docker compose: $_"
        Write-Host "If you prefer to start manually, run: $dockerCmd"
      }
    } else {
      Write-Warning "Docker not found. To start Postgres+Redis locally, run: $dockerCmd"
    }
  }
}

if ($Background) {
  Write-Host "Starting jar in background. Logs -> app.log, app.err"
  Start-Process -FilePath 'java' -ArgumentList '-jar', $jarPath -RedirectStandardOutput 'app.log' -RedirectStandardError 'app.err' -NoNewWindow
  Write-Host 'Process started (background).'
  exit 0
} else {
  Write-Host "Starting jar in foreground... (Ctrl-C to stop)"
  try {
    & 'java' '-jar' $jarPath
  } catch {
    Write-Error "Failed to start java: $_"
    exit 3
  }
}
