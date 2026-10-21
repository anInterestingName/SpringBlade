#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

REQ-2026-010 回归套件：执行真实协议检查，未覆盖的验收步骤必须记为阻塞。
凭据从仓库外配置或进程参数读取，不写入证据。
#>
[CmdletBinding()]
param(
    [string]$Issuer = '',
    [string]$GatewayUrl = 'http://127.0.0.1:80',
    [string]$ResourceUrl = 'http://127.0.0.1:8200',
    [string]$SystemUrl = 'http://127.0.0.1:8106',
    [string]$TenantId = '000000',
    [string]$Account = 'admin',
    [string]$Password = '',
    [string]$ClientId = 'gateway-bff',
    [string]$ClientSecret = '',
    [string]$ServiceClientId = 'demo-service',
    [string]$ServiceClientSecret = '',
    [string]$RedirectUri = 'http://127.0.0.1:8200/authorization/callback',
    [string]$UserScopes = 'openid profile demo.read service.read',
    [string]$ConfigurationDirectory = '',
    [string]$EvidencePath = '',
    [switch]$SkipUserLogin,
    [switch]$RunMavenBuild
)
$ErrorActionPreference = 'Stop'
$ScriptDir = $PSScriptRoot
$WorkspaceRoot = (Resolve-Path (Join-Path $ScriptDir '../..')).Path
. (Join-Path $ScriptDir 'Common-TestUtils.ps1')
foreach ($module in @('BuildGateway', 'EndpointsAndCrypto', 'ClientManagement', 'AuthCodeAndPkce',
    'UserAuthAndSecurity', 'TokensAndRevocation', 'BffSessionAndGateway', 'DownstreamAndFeign',
    'DatabaseAndDataAudit', 'OpsFaultAndObservability')) {
    . (Join-Path $ScriptDir "modules/Test-$module.ps1")
}
if (-not $ConfigurationDirectory) { $ConfigurationDirectory = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.springblade' }
$configuration = @{}
foreach ($file in @('authorization-local.properties', 'authorization-secrets.properties', 'bff-local.properties')) {
    $path = Join-Path $ConfigurationDirectory $file
    if (Test-Path -LiteralPath $path) {
        foreach ($line in Get-Content -LiteralPath $path) {
            if ($line -match '^\s*([A-Za-z0-9_.-]+)=(.*)$') { $configuration[$Matches[1]] = $Matches[2] }
        }
    }
}
if (-not $Issuer) { $Issuer = $configuration.AUTHORIZATION_ISSUER }
if (-not $Issuer) { $Issuer = 'http://127.0.0.1:8100' }
if (-not $ClientSecret) { $ClientSecret = $env:AUTHORIZATION_CLIENT_SECRET }
if (-not $ClientSecret) { $ClientSecret = $configuration.AUTHORIZATION_CLIENT_SECRET }
if (-not $ServiceClientSecret) { $ServiceClientSecret = $env:AUTHORIZATION_SERVICE_CLIENT_SECRET }
if (-not $ServiceClientSecret) { $ServiceClientSecret = $configuration.AUTHORIZATION_SERVICE_CLIENT_SECRET }
if (-not $Password) { $Password = $env:AUTHORIZATION_TEST_PASSWORD }
$reporter = [TestReporter]::new()
$reporter.Context = @{
    Issuer = $Issuer.TrimEnd('/'); GatewayUrl = $GatewayUrl.TrimEnd('/'); ResourceUrl = $ResourceUrl.TrimEnd('/');
    SystemUrl = $SystemUrl.TrimEnd('/'); TenantId = $TenantId; Account = $Account; Password = $Password;
    ClientId = $ClientId; ClientSecret = $ClientSecret; ServiceClientId = $ServiceClientId;
    ServiceClientSecret = $ServiceClientSecret; RedirectUri = $RedirectUri; SkipUserLogin = $SkipUserLogin.IsPresent;
    WorkspaceRoot = $WorkspaceRoot; Configuration = $configuration; UserScopes = $UserScopes; BffSessions = [Collections.Generic.List[object]]::new()
}
Write-Host 'REQ-2026-010：逐项记录已执行断言和未完成的验收步骤'
Write-Host "Issuer=$Issuer；Gateway=$GatewayUrl；Resource=$ResourceUrl；System=$SystemUrl"
try {
    Test-TC001-BuildGateway -Reporter $reporter -WorkspaceRoot $WorkspaceRoot -RunMavenBuild:$RunMavenBuild
    Test-TC002-DiscoveryAndJwks -Reporter $reporter -Issuer $Issuer
    Test-TC017-ExternalJwkLoading -Reporter $reporter -Issuer $Issuer
    Test-TC008-ClientCredentialsAndScopes -Reporter $reporter -Issuer $Issuer -ResourceUrl $ResourceUrl -ServiceClientId $ServiceClientId -ServiceClientSecret $ServiceClientSecret
    Test-TC006-UserAuthAndTenantConstraints -Reporter $reporter -Issuer $Issuer -ValidTenantId $TenantId -ValidAccount $Account -ValidPassword $Password
    Test-TC004-TC005-TC012-AuthCodeAndPkce -Reporter $reporter -Issuer $Issuer -ResourceUrl $ResourceUrl
    Test-TC003-ClientRegistrationConstraints -Reporter $reporter -Issuer $Issuer
    Test-TC009-TC010-TokensAndRevocation -Reporter $reporter -Issuer $Issuer -ClientId $ClientId -ClientSecret $ClientSecret
    Test-TC020-ConcurrencyTokenExchange -Reporter $reporter -Issuer $Issuer -ClientId $ClientId -ClientSecret $ClientSecret -RedirectUri $RedirectUri
    Test-TC011-BffSessionAndNoStore -Reporter $reporter -Issuer $Issuer
    Test-TC014-GatewayRoutesAndInternalEndpoints -Reporter $reporter -GatewayUrl $GatewayUrl
    Test-TC023-TC025-TokenRelayAndBladeUserCompat -Reporter $reporter -GatewayUrl $GatewayUrl -ResourceUrl $ResourceUrl
    Test-TC013-ResourceJwtVerification -Reporter $reporter -ResourceUrl $ResourceUrl
    Test-TC015-TC024-FeignCredentialsAndPropagation -Reporter $reporter -WorkspaceRoot $WorkspaceRoot
    Test-TC018-DataAuditAndLogSanitization -Reporter $reporter -WorkspaceRoot $WorkspaceRoot
    Test-TC019-SqlUpgradeAndIdempotency -Reporter $reporter -WorkspaceRoot $WorkspaceRoot
    Test-TC021-ObservabilityAndHealth -Reporter $reporter -Issuer $Issuer
    Test-TC016-TC022-FaultRecoveryAndRollbackCompat -Reporter $reporter -WorkspaceRoot $WorkspaceRoot
    Test-TC007-RateLimitAndLockout -Reporter $reporter -Issuer $Issuer -TenantId $TenantId
} finally {
    foreach ($session in $reporter.Context.BffSessions) {
        try { Close-TestBffSession $reporter $session } catch { $reporter.Context.LogoutCleanupFailures++ }
    }
    try { Remove-OwnedTestSessions $reporter } catch { $reporter.Context.SessionCleanupFailed = $true }
    Clean-RegisteredTokens -Reporter $reporter -Issuer $Issuer
    foreach ($number in 1..25) {
        $id = 'TC-{0:000}' -f $number
        if (@($reporter.Results | Where-Object Id -eq $id).Count -eq 0) {
            $reporter.Log($id, '用例未完成', 'BLOCK', '前置步骤异常，未取得执行结果')
        }
    }
    $reporter.PrintSummary()
    if (-not $EvidencePath) { $EvidencePath = Join-Path $WorkspaceRoot ('doc/test/evidence/REQ-2026-010/' + [DateTime]::UtcNow.ToString('yyyy-MM-dd-HHmmss') + '-suite-results.json') }
    $record = [ordered]@{
        ExecutedAtUtc = [DateTime]::UtcNow.ToString('o'); ScriptVersion = '0.9'; DataPrefix = $reporter.Prefix;
        PowerShell = $PSVersionTable.PSVersion.ToString(); Results = @($reporter.Results | Sort-Object Id);
        Cleanup = $reporter.Context.Cleanup; SessionCleanup = $reporter.Context.SessionCleanup;
        LogoutCleanupFailures = $reporter.Context.LogoutCleanupFailures; SessionCleanupFailed = [bool]$reporter.Context.SessionCleanupFailed
    }
    New-Item -ItemType Directory -Path (Split-Path $EvidencePath -Parent) -Force | Out-Null
    $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $EvidencePath -Encoding utf8
    Write-Host "结构化证据：$EvidencePath"
}
if (@($reporter.Results | Where-Object Status -eq FAIL).Count -gt 0 -or $reporter.Context.Cleanup.Failed -gt 0 -or $reporter.Context.SessionCleanupFailed) { exit 1 }
if (@($reporter.Results | Where-Object { $_.Status -in @('BLOCK', 'SKIP') }).Count -gt 0) { exit 2 }
exit 0
