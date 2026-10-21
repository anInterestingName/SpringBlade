#requires -Version 7.2
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

由用户执行授权码 + PKCE 验证，浏览器中完成登录和同意，不打印密码或令牌。
#>
[CmdletBinding()]
param(
    [string]$Issuer = '',
    [string]$ResourceUrl = 'http://127.0.0.1:8200',
    [string]$RedirectUri = 'http://127.0.0.1:8200/authorization/callback',
    [string]$ClientId = 'gateway-bff',
    [switch]$PublicClient
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Issuer)) {
    $authorizationLocalConfig = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.springblade/authorization-local.properties'
    if (Test-Path -LiteralPath $authorizationLocalConfig) {
        $authorizationIssuerLine = Get-Content -LiteralPath $authorizationLocalConfig | Where-Object { $_ -match '^AUTHORIZATION_ISSUER=' } | Select-Object -First 1
        if ($authorizationIssuerLine) { $Issuer = $authorizationIssuerLine.Split('=', 2)[1] }
    }
    if ([string]::IsNullOrWhiteSpace($Issuer)) { $Issuer = 'http://127.0.0.1:8100' }
}
$clientSecret = [Environment]::GetEnvironmentVariable('AUTHORIZATION_CLIENT_SECRET')
if ([string]::IsNullOrWhiteSpace($clientSecret)) {
    $authorizationLocalSecrets = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.springblade/authorization-secrets.properties'
    if (Test-Path -LiteralPath $authorizationLocalSecrets) {
        $authorizationSecretLine = Get-Content -LiteralPath $authorizationLocalSecrets | Where-Object { $_ -match '^AUTHORIZATION_CLIENT_SECRET=' } | Select-Object -First 1
        if ($authorizationSecretLine) { $clientSecret = $authorizationSecretLine.Split('=', 2)[1] }
    }
}
if ($PublicClient -and -not $PSBoundParameters.ContainsKey('ClientId')) { $ClientId = 'demo-browser' }
if (-not $PublicClient -and [string]::IsNullOrWhiteSpace($clientSecret)) {
    throw '请先设置与授权服务一致的 AUTHORIZATION_CLIENT_SECRET，或使用 -PublicClient 验证公共客户端。'
}

function ConvertTo-AuthorizationBase64Url {
    param([byte[]]$Bytes)
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Invoke-AuthorizationRequest {
    param([string]$Uri, [string]$Method = 'GET', [hashtable]$Headers = @{}, [hashtable]$Body)
    $request = @{
        Uri = $Uri; Method = $Method; Headers = $Headers
        SkipHttpErrorCheck = $true; TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $request.Body = $Body
        $request.ContentType = 'application/x-www-form-urlencoded'
    }
    try { Invoke-WebRequest @request }
    catch { throw '验证请求无法完成，请检查授权服务与 demo 验证实例。' }
}

function Assert-AuthorizationStatus {
    param($Response, [int]$Expected, [string]$Name)
    if ([int]$Response.StatusCode -ne $Expected) {
        throw "$Name 失败：期望 HTTP $Expected，实际 HTTP $($Response.StatusCode)。"
    }
    Write-Host "通过：$Name（HTTP $Expected）"
}

$verifier = ConvertTo-AuthorizationBase64Url ([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$challenge = ConvertTo-AuthorizationBase64Url ([Security.Cryptography.SHA256]::HashData([Text.Encoding]::ASCII.GetBytes($verifier)))
$state = ConvertTo-AuthorizationBase64Url ([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$parameters = [ordered]@{
    response_type = 'code'; client_id = $ClientId; redirect_uri = $RedirectUri
    scope = 'openid profile demo.read'; state = $state
    code_challenge = $challenge; code_challenge_method = 'S256'
}
$query = ($parameters.GetEnumerator() | ForEach-Object {
    '{0}={1}' -f [Uri]::EscapeDataString($_.Key), [Uri]::EscapeDataString($_.Value)
}) -join '&'
Write-Host '在浏览器打开以下地址，使用系统中已启用的账号、密码及所属租户登录并同意授权：'
Write-Host "$Issuer/oauth2/authorize?$query"
$callback = Read-Host '授权完成后粘贴浏览器回调完整地址（输入隐藏）' -MaskInput
try { $callbackUri = [Uri]$callback; $expectedUri = [Uri]$RedirectUri }
catch { throw '回调地址格式错误。' }
if (-not $callbackUri.IsAbsoluteUri -or $callbackUri.GetLeftPart([UriPartial]::Path) -cne $expectedUri.GetLeftPart([UriPartial]::Path)) {
    throw '回调地址与注册地址不一致。'
}
$callbackQuery = [System.Web.HttpUtility]::ParseQueryString($callbackUri.Query)
if ($callbackQuery['state'] -cne $state) { throw 'state 不匹配，停止换取令牌。' }
if ($callbackQuery['error'] -or [string]::IsNullOrWhiteSpace($callbackQuery['code'])) { throw '授权失败或回调中缺少 code。' }

$clientHeaders = @{}
if (-not $PublicClient) {
    $credentials = '{0}:{1}' -f ([Uri]::EscapeDataString($ClientId)), ([Uri]::EscapeDataString($clientSecret))
    $clientHeaders.Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($credentials))
}
$body = @{
    grant_type = 'authorization_code'; code = $callbackQuery['code']
    redirect_uri = $RedirectUri; code_verifier = $verifier
}
if ($PublicClient) { $body.client_id = $ClientId }
$response = Invoke-AuthorizationRequest "$Issuer/oauth2/token" -Method POST -Headers $clientHeaders -Body $body
Assert-AuthorizationStatus $response 200 '授权码与 PKCE 换取令牌'
$tokens = $response.Content | ConvertFrom-Json
if (-not $tokens.access_token -or -not $tokens.id_token) { throw 'OIDC 响应缺少 access_token 或 id_token。' }
Write-Host '通过：OIDC 返回 id_token（本脚本不执行 ID Token 客户端验签）'
$bearer = @{ Authorization = "Bearer $($tokens.access_token)" }
Assert-AuthorizationStatus (Invoke-AuthorizationRequest "$ResourceUrl/authorization/messages" -Headers $bearer) 200 '用户令牌访问 demo'
Assert-AuthorizationStatus (Invoke-AuthorizationRequest "$Issuer/userinfo" -Headers $bearer) 200 'OIDC UserInfo'

if (-not $PublicClient) {
    if (-not $tokens.refresh_token) { throw '机密客户端授权码响应缺少 refresh_token。' }
    $refreshBody = @{ grant_type = 'refresh_token'; refresh_token = $tokens.refresh_token }
    $refreshed = Invoke-AuthorizationRequest "$Issuer/oauth2/token" -Method POST -Headers $clientHeaders -Body $refreshBody
    Assert-AuthorizationStatus $refreshed 200 '刷新令牌'
    $newTokens = $refreshed.Content | ConvertFrom-Json
    if (-not $newTokens.refresh_token -or $newTokens.refresh_token -ceq $tokens.refresh_token) { throw '刷新令牌未轮换。' }
    Assert-AuthorizationStatus (Invoke-AuthorizationRequest "$ResourceUrl/authorization/messages" -Headers @{
        Authorization = "Bearer $($newTokens.access_token)"
    }) 200 '刷新后令牌访问 demo'
    $reused = Invoke-AuthorizationRequest "$Issuer/oauth2/token" -Method POST -Headers $clientHeaders -Body $refreshBody
    Assert-AuthorizationStatus $reused 400 '旧刷新令牌不可重用'
    if (($reused.Content | ConvertFrom-Json).error -ne 'invalid_grant') { throw '旧刷新令牌错误语义不符合预期。' }
}
Write-Host '授权码流程验证完成。授权和会话已持久化，请仅撤销本次验证授权或等待过期，不清理共享数据。'
$tokens = $null
$newTokens = $null
$verifier = $null
$callback = $null
