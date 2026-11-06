$ErrorActionPreference = 'stop'
./tools/tests/dev-release.tests.ps1
if (Test-Path -LiteralPath variable:\LASTEXITCODE) { exit $LASTEXITCODE }
