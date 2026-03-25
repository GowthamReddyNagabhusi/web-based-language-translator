param(
    [string]$RepoPath = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$Branch = "main",
    [string]$Remote = "origin",
    [switch]$SkipPull
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Step {
    param(
        [string]$Message,
        [scriptblock]$Action
    )

    Write-Host "==> $Message"
    & $Action
}

if (-not (Test-Path $RepoPath)) {
    throw "Repository path not found: $RepoPath"
}

Push-Location $RepoPath
try {
    Invoke-Step -Message "Validating git repository" -Action {
        git rev-parse --is-inside-work-tree | Out-Null
    }

    Invoke-Step -Message "Checking out branch $Branch" -Action {
        git checkout $Branch | Out-Null
    }

    if (-not $SkipPull) {
        Invoke-Step -Message "Pulling latest changes" -Action {
            git pull --rebase $Remote $Branch
        }
    }

    Invoke-Step -Message "Running validation (mvn clean verify -DskipTests=false -B)" -Action {
        mvn clean verify -DskipTests=false -B
    }

    $statusLines = @(git status --porcelain)
    if ($statusLines.Count -eq 0) {
        Write-Host "No local changes detected. Nothing to commit."
        exit 0
    }

    $utcStamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd HH:mm:ss 'UTC'")
    $message = "chore: automated validated update ($utcStamp)"

    Invoke-Step -Message "Staging all changes" -Action {
        git add -A
    }

    Invoke-Step -Message "Creating commit" -Action {
        git commit -m $message
    }

    Invoke-Step -Message "Pushing to $Remote/$Branch" -Action {
        git push $Remote $Branch
    }

    Write-Host "Automation completed successfully."
}
finally {
    Pop-Location
}
