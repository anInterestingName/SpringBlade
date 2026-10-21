#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

真实协议回归；未完成的文档验收步骤记录为阻塞。
#>
function Test-TC004-TC005-TC012-AuthCodeAndPkce {
    param([TestReporter]$Reporter, [string]$Issuer, [string]$ResourceUrl)
    Invoke-TestCase $Reporter 'TC-004' '授权码与 PKCE 正常流程' {
        $tokens = Get-TestUserTokens $Reporter
        if (-not $tokens.id_token -or -not $tokens.refresh_token) { throw 'OIDC 响应缺少 ID Token 或 Refresh Token' }
        $response = Invoke-TestHttp "$ResourceUrl/authorization/messages" -Headers @{ Authorization = "Bearer $($tokens.access_token)" }
        Assert-TestStatus $response @(200) '用户令牌访问资源'
        $response = Invoke-TestHttp "$Issuer/userinfo" -Headers @{ Authorization = "Bearer $($tokens.access_token)" }
        Assert-TestStatus $response @(200) 'OIDC UserInfo'
        $session = Open-TestBffSession $Reporter
        $Reporter.Context.BffSessions.Add($session)
        $response = Invoke-TestHttp "$($Reporter.Context.GatewayUrl)/bff/me" -WebSession $session
        Assert-TestStatus $response @(200) 'BFF 会话'
        if ($response.Content -match 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.') { throw 'BFF 响应泄露 Token' }
        @{ Status = 'PASS'; Details = '授权码、state、S256 PKCE、用户资源、UserInfo 和 BFF 回调会话实际验证通过' }
    }
    Invoke-TestCase $Reporter 'TC-005' 'PKCE、回调与授权码一次性' {
        $headers = Get-TestClientHeaders $Reporter.Context.ClientId $Reporter.Context.ClientSecret
        foreach ($negative in @('verifier', 'redirect')) {
            $code = New-TestAuthorizationCode $Reporter
            $body = @{ grant_type = 'authorization_code'; code = $code.Code; code_verifier = $code.Verifier; redirect_uri = $code.RedirectUri }
            if ($negative -eq 'verifier') { $body.code_verifier = 'tampered-verifier-with-at-least-43-characters-test' }
            else { $body.redirect_uri = 'http://127.0.0.1:9999/tampered/callback' }
            $response = Invoke-TestHttp "$Issuer/oauth2/token" -Method POST -Headers $headers -Body $body
            Assert-TestStatus $response @(400) "错误 $negative"
            if ((Read-TestJson $response).error -ne 'invalid_grant') { throw "错误 $negative 未返回 invalid_grant" }
        }
        $code = New-TestAuthorizationCode $Reporter
        $null = Request-TestUserTokens $Reporter $code
        $response = Invoke-TestHttp "$Issuer/oauth2/token" -Method POST -Headers $headers -Body @{
            grant_type = 'authorization_code'; code = $code.Code; code_verifier = $code.Verifier; redirect_uri = $code.RedirectUri }
        Assert-TestStatus $response @(400) '授权码重放'
        if ((Read-TestJson $response).error -ne 'invalid_grant') { throw '授权码重放错误语义不符' }
        @{ Status = 'BLOCK'; Details = '错误 verifier、错误回调、code 重放已通过；跨客户端、缺失/超长 verifier 和过期 code 夹具待准备' }
    }
    Invoke-TestCase $Reporter 'TC-012' 'BFF CSRF 与退出' {
        $session = Open-TestBffSession $Reporter
        $Reporter.Context.BffSessions.Add($session)
        $response = Invoke-TestHttp "$($Reporter.Context.GatewayUrl)/bff/logout" -Method POST -WebSession $session
        Assert-TestStatus $response @(403) '无 CSRF 退出'
        Close-TestBffSession $Reporter $session
        $response = Invoke-TestHttp "$($Reporter.Context.GatewayUrl)/bff/me" -WebSession $session
        Assert-TestStatus $response @(401) '退出后身份'
        Close-TestBffSession $Reporter $session
        @{ Status = 'BLOCK'; Details = '真实 BFF 缺失 CSRF=403、退出=200、退出后=401、重复退出已验证；拒绝同意和过期回调恢复待执行' }
    }
}
