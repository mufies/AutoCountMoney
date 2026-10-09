$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'Install GitHub CLI first: winget install --id GitHub.cli --exact. Then open a new terminal.'
}
& gh auth status
if ($LASTEXITCODE -ne 0) {
    & gh auth login --hostname github.com --git-protocol https --web
    if ($LASTEXITCODE -ne 0) { throw 'GitHub login did not complete.' }
}
& gh auth setup-git
if ($LASTEXITCODE -ne 0) { throw 'Could not configure GitHub Git authentication.' }

if (-not (Test-Path -LiteralPath (Join-Path $projectRoot '.git'))) {
    & git init -b main
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize Git.' }
}
$remotes = @(& git remote)
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect Git remotes.' }
if ($remotes -contains 'origin') {
    $origin = & git remote get-url origin
    throw "An origin remote already exists ($origin). Review it before publishing."
}

& git add -- .gitignore README.md PLAN.md app/src app/build.gradle.kts build.gradle.kts settings.gradle.kts gradle.properties gradle gradlew gradlew.bat docs
if ($LASTEXITCODE -ne 0) { throw 'Could not stage project files.' }
& git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Resolve the staged whitespace errors before publishing.' }
& git diff --cached --quiet
if ($LASTEXITCODE -eq 1) {
    & git commit -m 'Add Android expense tracker with wallet and bank notification parsers'
    if ($LASTEXITCODE -ne 0) { throw 'Could not commit project files. Check git user.name and user.email.' }
} elseif ($LASTEXITCODE -ne 0) {
    throw 'Could not inspect staged changes.'
}
& gh repo create AutoCountMoney --public --source $projectRoot --remote origin --push --description 'Android expense tracker for wallet and bank notifications, built with Kotlin, Compose and Room'
if ($LASTEXITCODE -ne 0) { throw 'Repository creation/push did not complete. Inspect the GitHub CLI error above.' }
& gh repo view --json url --jq .url
