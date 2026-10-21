#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

由用户对已启动的独立验证服务执行；只创建持久化授权记录，不输出密码或令牌。
#>
[CmdletBinding()]
param(
    [string]$Issuer = '',
    [string]$ResourceUrl = 'http://127.0.0.1:8200',
    [string]$ClientId = 'demo-service'
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
$clientSecret = [Environment]::GetEnvironmentVariable('AUTHORIZATION_SERVICE_CLIENT_SECRET')
if ([string]::IsNullOrWhiteSpace($clientSecret)) {
    $authorizationLocalSecrets = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.springblade/authorization-secrets.properties'
    if (Test-Path -LiteralPath $authorizationLocalSecrets) {
        $authorizationSecretLine = Get-Content -LiteralPath $authorizationLocalSecrets | Where-Object { $_ -match '^AUTHORIZATION_SERVICE_CLIENT_SECRET=' } | Select-Object -First 1
        if ($authorizationSecretLine) { $clientSecret = $authorizationSecretLine.Split('=', 2)[1] }
    }
}
if ([string]::IsNullOrWhiteSpace($clientSecret)) {
    throw '请先设置 AUTHORIZATION_SERVICE_CLIENT_SECRET，且与授权服务一致。'
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
    catch { throw '验证请求无法完成，请检查两个服务是否已启动以及端口配置。' }
}

function Assert-AuthorizationStatus {
    param($Response, [int]$Expected, [string]$Name)
    if ([int]$Response.StatusCode -ne $Expected) {
        throw "$Name 失败：期望 HTTP $Expected，实际 HTTP $($Response.StatusCode)。"
    }
    Write-Host "通过：$Name（HTTP $Expected）"
}

function Assert-AuthorizationCondition {
    param([bool]$Condition, [string]$Name)
    if (-not $Condition) { throw "$Name 失败。" }
    Write-Host "通过：$Name"
}

$clientCredentials = '{0}:{1}' -f ([Uri]::EscapeDataString($ClientId)), ([Uri]::EscapeDataString($clientSecret))
$basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($clientCredentials))
$clientHeaders = @{ Authorization = "Basic $basic" }
$issuedToken = $null
$revoked = $false
try {
    $discovery = Invoke-AuthorizationRequest "$Issuer/.well-known/openid-configuration"
    Assert-AuthorizationStatus $discovery 200 'OIDC 发现端点'
    $metadata = $discovery.Content | ConvertFrom-Json
    Assert-AuthorizationCondition ($metadata.issuer -ceq $Issuer) 'issuer 一致'
    $jwks = Invoke-AuthorizationRequest "$Issuer/oauth2/jwks"
    Assert-AuthorizationStatus $jwks 200 'JWK 公钥端点'
    $keys = ($jwks.Content | ConvertFrom-Json).keys
    Assert-AuthorizationCondition ($keys.Count -gt 0 -and $null -eq $keys[0].d) 'JWK 不包含 RSA 私钥'

    $public = Invoke-AuthorizationRequest "$ResourceUrl/authorization/public"
    Assert-AuthorizationStatus $public 200 'demo 公开入口'
    Assert-AuthorizationCondition (($public.Content | ConvertFrom-Json).code -eq 200) '公开入口 R 响应'
    Assert-AuthorizationStatus (Invoke-AuthorizationRequest "$ResourceUrl/authorization/messages") 401 '缺少令牌被拒绝'
    Assert-AuthorizationStatus (Invoke-AuthorizationRequest "$ResourceUrl/authorization/messages" -Headers @{
        Authorization = 'Bearer invalid-token'
    }) 401 '无效令牌被拒绝'

    $invalidScope = Invoke-AuthorizationRequest "$Issuer/oauth2/token" -Method POST -Headers $clientHeaders -Body @{
        grant_type = 'client_credentials'; scope = 'demo.write'
    }
    Assert-AuthorizationStatus $invalidScope 400 '越权申请 scope 被拒绝'
    Assert-AuthorizationCondition (($invalidScope.Content | ConvertFrom-Json).error -eq 'invalid_scope') 'invalid_scope 错误语义'

    $response = Invoke-AuthorizationRequest "$Issuer/oauth2/token" -Method POST -Headers $clientHeaders -Body @{
        grant_type = 'client_credentials'; scope = 'demo.read'
    }
    Assert-AuthorizationStatus $response 200 '客户端凭证换取令牌'
    $tokenResponse = $response.Content | ConvertFrom-Json
    $issuedToken = $tokenResponse.access_token
    Assert-AuthorizationCondition (-not [string]::IsNullOrWhiteSpace($issuedToken) -and $tokenResponse.token_type -ieq 'Bearer') '标准 OAuth2 令牌响应'
    $bearer = @{ Authorization = "Bearer $issuedToken" }
    $messages = Invoke-AuthorizationRequest "$ResourceUrl/authorization/messages" -Headers $bearer
    Assert-AuthorizationStatus $messages 200 'demo.read 访问资源'
    $resourceResponse = $messages.Content | ConvertFrom-Json
    Assert-AuthorizationCondition ($resourceResponse.code -eq 200 -and $resourceResponse.data.subject -ceq $ClientId) '资源端 R 响应与客户端身份'
    Assert-AuthorizationStatus (Invoke-AuthorizationRequest "$ResourceUrl/authorization/restricted" -Headers $bearer) 403 '缺少 demo.write 被拒绝'

    $introspection = Invoke-AuthorizationRequest "$Issuer/oauth2/introspect" -Method POST -Headers $clientHeaders -Body @{
        token = $issuedToken
    }
    Assert-AuthorizationStatus $introspection 200 '令牌内省'
    Assert-AuthorizationCondition (($introspection.Content | ConvertFrom-Json).active -eq $true) '令牌为有效授权记录'
    $revocation = Invoke-AuthorizationRequest "$Issuer/oauth2/revoke" -Method POST -Headers $clientHeaders -Body @{
        token = $issuedToken; token_type_hint = 'access_token'
    }
    Assert-AuthorizationStatus $revocation 200 '令牌撤销'
    $revoked = $true
    $afterRevocation = Invoke-AuthorizationRequest "$Issuer/oauth2/introspect" -Method POST -Headers $clientHeaders -Body @{
        token = $issuedToken
    }
    Assert-AuthorizationStatus $afterRevocation 200 '撤销后令牌内省'
    Assert-AuthorizationCondition (($afterRevocation.Content | ConvertFrom-Json).active -eq $false) '撤销后授权记录失效'
    Write-Host '客户端凭证流程验证完成。JWT 资源端采用离线验签，撤销不代表已签发 JWT 立即失效。'
}
finally {
    if ($issuedToken -and -not $revoked) {
        try {
            $null = Invoke-AuthorizationRequest "$Issuer/oauth2/revoke" -Method POST -Headers $clientHeaders -Body @{
                token = $issuedToken; token_type_hint = 'access_token'
            }
        }
        catch { Write-Warning '持久化授权记录清理未完成，停止独立授权服务即可清空。' }
    }
    $issuedToken = $null
    $clientCredentials = $null
    $basic = $null
}
