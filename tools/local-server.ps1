<#
.SYNOPSIS
    Starts, stops, and checks the local backend for the API suites.

.DESCRIPTION
    The Gradle bootRun task forks a JVM that a host process can kill. This
    script starts the packaged jar as a detached process, so the server keeps
    running while the Bruno and Playwright suites run.

    The script also writes the build id to build/build-id.txt. The server
    reports that id in the X-Build-Id header, and the reset task compares the
    two. A stale server then fails the suite instead of testing old code.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/local-server.ps1 start
    powershell -ExecutionPolicy Bypass -File tools/local-server.ps1 stop
    powershell -ExecutionPolicy Bypass -File tools/local-server.ps1 status
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'status')]
    [string]$Action = 'status',

    [string]$Profile = 'test',
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$module = Join-Path $root 'service-scheduler-server'
$jar = Join-Path $module 'build/libs/service-scheduler-server-1.0.0-SNAPSHOT.jar'
$marker = Join-Path $module 'build/build-id.txt'
$log = Join-Path $module 'build/local-server.log'

function Get-ServerPid {
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($connection) {
        return $connection.OwningProcess
    }
    return $null
}

switch ($Action) {
    'start' {
        $existing = Get-ServerPid
        if ($existing) {
            Write-Output "The port $Port already answers with the pid $existing. Stop it first."
            exit 1
        }
        if (-not (Test-Path $jar)) {
            Write-Output "The jar is missing. Run: ./gradlew :service-scheduler-server:bootJar"
            exit 1
        }
        $buildId = [guid]::NewGuid().ToString()
        New-Item -ItemType Directory -Force -Path (Split-Path $marker) | Out-Null
        Set-Content -Path $marker -Value $buildId -NoNewline
        $arguments = @(
            '-jar', $jar,
            "--spring.profiles.active=$Profile",
            "--app.build.id=$buildId",
            "--server.port=$Port"
        )
        # The command detaches the server. A Start-Process with redirection
        # keeps the script alive until the server ends.
        $command = 'cd /d "' + $module + '" && start /b java ' + ($arguments -join ' ') + ' > "' + $log + '" 2>&1'
        cmd /c $command | Out-Null

        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep -Seconds 1
            try {
                $response = Invoke-WebRequest -Uri "http://localhost:$Port/actuator/health" -UseBasicParsing -TimeoutSec 3
                if ($response.StatusCode -eq 200) {
                    Write-Output "The server is up on port $Port with the build $buildId"
                    exit 0
                }
            } catch {
                continue
            }
        }
        Write-Output "The server did not become ready. Read $log"
        exit 1
    }
    'stop' {
        $pidToStop = Get-ServerPid
        if ($pidToStop) {
            Stop-Process -Id $pidToStop -Force -ErrorAction SilentlyContinue
            Write-Output "The server on port $Port is stopped"
        } else {
            Write-Output "No server listens on port $Port"
        }
        exit 0
    }
    'status' {
        $pidToStop = Get-ServerPid
        if ($pidToStop) {
            Write-Output "The server on port $Port runs with the pid $pidToStop"
        } else {
            Write-Output "No server listens on port $Port"
        }
        exit 0
    }
}
