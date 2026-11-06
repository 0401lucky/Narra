$ErrorActionPreference = 'stop'
./tools/tests/dev-release-entry.tests.ps1
if (Test-Path -LiteralPath variable:\LASTEXITCODE) { exit $LASTEXITCODE }
