$ErrorActionPreference = 'stop'
./tools/tests/dev-release-startup.tests.ps1
if (Test-Path -LiteralPath variable:\LASTEXITCODE) { exit $LASTEXITCODE }
