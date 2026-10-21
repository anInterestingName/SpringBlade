#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

真实协议回归；未完成的文档验收步骤记录为阻塞。
#>
function Test-TC009-TC010-TokensAndRevocation {
    param([TestReporter]$Reporter, [string]$Issuer, [string]$ClientId, [string]$ClientSecret)
    Invoke-TestCase $Reporter 'TC-009' 'Refresh Token 轮换与令牌族重用' {
        $initial = Request-TestUserTokens $Reporter (New-TestAuthorizationCode $Reporter)
        if (-not $initial.refresh_token) { throw '授权码响应缺少 Refresh Token' }
        $headers = Get-TestClientHeaders $ClientId $ClientSecret
        $body = @{ grant_type = 'refresh_token'; refresh_token = $initial.refresh_token }
        $response = Invoke-TestHttp "$Issuer/oauth2/token" -Method POST -Headers $headers -Body $body
        Assert-TestStatus $response @(200) '首次刷新'
        $next = Read-TestJson $response
        Save-TestTokens $Reporter $next $ClientId $ClientSecret
        if (-not $next.refresh_token -or $next.refresh_token -ceq $initial.refresh_token) { throw 'Refresh Token 未轮换' }
        $response = Invoke-TestHttp "$Issuer/oauth2/token" -Method POST -Headers $headers -Body $body
        Assert-TestStatus $response @(400) '旧 Refresh Token 重用'
        if ((Read-TestJson $response).error -ne 'invalid_grant') { throw '旧 Refresh Token 重用错误语义不符' }
        $response = Invoke-TestHttp "$Issuer/oauth2/token" -Method POST -Headers $headers -Body @{ grant_type = 'refresh_token'; refresh_token = $next.refresh_token }
        if ($response.StatusCode -eq 200) {
            Save-TestTokens $Reporter (Read-TestJson $response) $ClientId $ClientSecret
            throw '旧 Refresh Token 重用后，后继 Refresh Token 仍能签发：令牌族未撤销'
        }
        Assert-TestStatus $response @(400) '重用后后继令牌'
        @{ Status = 'BLOCK'; Details = '真实刷新轮换、旧值 invalid_grant、重用后后继失效已验证；停用用户/租户夹具待准备' }
    }
    Invoke-TestCase $Reporter 'TC-010' '撤销与内省' {
        $tokens = Request-TestUserTokens $Reporter (New-TestAuthorizationCode $Reporter)
        $headers = Get-TestClientHeaders $ClientId $ClientSecret
        $response = Invoke-TestHttp "$Issuer/oauth2/introspect" -Method POST -Headers $headers -Body @{ token = $tokens.access_token }
        Assert-TestStatus $response @(200) '撤销前内省'
        if ((Read-TestJson $response).active -ne $true) { throw '撤销前 Access Token 不是 active' }
        foreach ($entry in @(@{ Token = $tokens.access_token; Hint = 'access_token' }, @{ Token = $tokens.refresh_token; Hint = 'refresh_token' })) {
            $response = Invoke-TestHttp "$Issuer/oauth2/revoke" -Method POST -Headers $headers -Body @{ token = $entry.Token; token_type_hint = $entry.Hint }
            Assert-TestStatus $response @(200) '令牌撤销'
            $response = Invoke-TestHttp "$Issuer/oauth2/introspect" -Method POST -Headers $headers -Body @{ token = $entry.Token }
            Assert-TestStatus $response @(200) '撤销后内省'
            if ((Read-TestJson $response).active -ne $false) { throw '撤销后内省仍 active' }
        }
        $response = Invoke-TestHttp "$Issuer/oauth2/token" -Method POST -Headers $headers -Body @{ grant_type = 'refresh_token'; refresh_token = $tokens.refresh_token }
        Assert-TestStatus $response @(400) '撤销后刷新'
        $resource = Invoke-TestHttp "$($Reporter.Context.ResourceUrl)/authorization/messages" -Headers @{ Authorization = "Bearer $($tokens.access_token)" }
        Assert-TestStatus $resource @(200, 401) '撤销后的资源边界'
        @{ Status = 'PASS'; Details = "Access/Refresh 实际撤销、内省 active=false、刷新拒绝；资源 HTTP $($resource.StatusCode)（离线 JWT 可持续到 exp）" }
    }
}
function Test-TC020-ConcurrencyTokenExchange {
    param([TestReporter]$Reporter, [string]$Issuer, [string]$ClientId, [string]$ClientSecret, [string]$RedirectUri)
    Invoke-TestCase $Reporter 'TC-020' '真实授权码和刷新并发' {
        $headers = Get-TestClientHeaders $ClientId $ClientSecret
        $code = New-TestAuthorizationCode $Reporter
        $body = @{ grant_type = 'authorization_code'; code = $code.Code; code_verifier = $code.Verifier; redirect_uri = $code.RedirectUri }
        $null = Invoke-ConcurrentTokenRequests $Reporter $headers $body
        # 授权码重放可使该授权的令牌失效；刷新并发使用另一条全新授权，避免前置已被撤销。
        $first = Request-TestUserTokens $Reporter (New-TestAuthorizationCode $Reporter)
        $body = @{ grant_type = 'refresh_token'; refresh_token = $first.refresh_token }
        $null = Invoke-ConcurrentTokenRequests $Reporter $headers $body
        @{ Status = 'BLOCK'; Details = '同一真实 code 和 Refresh Token 各 4 个并发请求，均严格 1 个 200、3 个 invalid_grant；容量/P95/延迟故障注入待执行' }
    }
}
function Invoke-ConcurrentTokenRequests {
    param([TestReporter]$Reporter, [hashtable]$Headers, [hashtable]$Body)
    $http = [Net.Http.HttpClient]::new()
    $http.Timeout = [TimeSpan]::FromSeconds(15)
    $http.DefaultRequestHeaders.Add('Authorization', $Headers.Authorization)
    $pending = [Collections.Generic.List[object]]::new()
    $successful = [Collections.Generic.List[object]]::new()
    try {
        $encoded = ($Body.GetEnumerator() | ForEach-Object { '{0}={1}' -f [Uri]::EscapeDataString($_.Key), [Uri]::EscapeDataString($_.Value) }) -join '&'
        # PostAsync 全部先发出，随后统一等待；不使用同步 for 循环模拟并发。
        foreach ($number in 1..4) {
            $content = [Net.Http.StringContent]::new($encoded, [Text.Encoding]::UTF8, 'application/x-www-form-urlencoded')
            $pending.Add(@{ Task = $http.PostAsync("$($Reporter.Context.Issuer)/oauth2/token", $content); Content = $content })
        }
        $rejected = 0
        foreach ($request in $pending) {
            $response = $request.Task.GetAwaiter().GetResult()
            $payload = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
            if ([int]$response.StatusCode -eq 200) {
                Save-TestTokens $Reporter $payload $Reporter.Context.ClientId $Reporter.Context.ClientSecret
                $successful.Add($payload)
            } elseif ([int]$response.StatusCode -eq 400 -and $payload.error -eq 'invalid_grant') { $rejected++ }
            else { throw "并发兑换返回非预期 HTTP $([int]$response.StatusCode)" }
        }
        if ($successful.Count -ne 1 -or $rejected -ne 3) { throw "并发一次性语义失败：成功 $($successful.Count)，invalid_grant $rejected" }
        return $successful[0]
    } finally {
        foreach ($request in $pending) { $request.Content.Dispose() }
        $http.Dispose()
    }
}
