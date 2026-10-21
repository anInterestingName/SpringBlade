#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

真实协议回归；未完成的文档验收步骤记录为阻塞。
#>
function Test-TC011-BffSessionAndNoStore {
    param([TestReporter]$Reporter, [string]$Issuer)
    Invoke-TestCase $Reporter 'TC-011' 'BFF 会话隔离与 no-store' {
        $sessions = @(Open-TestBffSession $Reporter; Open-TestBffSession $Reporter)
        foreach ($session in $sessions) { $Reporter.Context.BffSessions.Add($session) }
        $cookies = @($sessions | ForEach-Object { $_.Cookies.GetCookies([Uri]$Reporter.Context.GatewayUrl)['BFF_SESSION'] })
        if ($cookies.Count -ne 2 -or -not $cookies[0].Value -or $cookies[0].Value -ceq $cookies[1].Value) { throw '两个 BFF 会话未使用独立 Cookie' }
        foreach ($cookie in $cookies) { if (-not $cookie.HttpOnly) { throw 'BFF Cookie 缺少 HttpOnly' } }
        foreach ($session in $sessions) {
            foreach ($path in @('/bff/me', '/bff/csrf', '/bff/messages')) {
                $response = Invoke-TestHttp ($Reporter.Context.GatewayUrl + $path) -WebSession $session
                Assert-TestStatus $response @(200) "BFF $path"
                if (($response.Headers['Cache-Control'] -join ',') -notmatch 'no-store') { throw "BFF $path 缺少 no-store" }
                if ($response.Content -match 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.') { throw "BFF $path 泄露 Token" }
            }
        }
        Close-TestBffSession $Reporter $sessions[0]
        Assert-TestStatus (Invoke-TestHttp "$($Reporter.Context.GatewayUrl)/bff/me" -WebSession $sessions[0]) @(401) '已退出会话'
        Assert-TestStatus (Invoke-TestHttp "$($Reporter.Context.GatewayUrl)/bff/me" -WebSession $sessions[1]) @(200) '另一会话'
        @{ Status = 'BLOCK'; Details = '同用户两个独立 BFF Cookie、HttpOnly、三类响应 no-store、无 Token、退出不影响另一会话均实测通过；不同用户夹具待准备' }
    }
}
function Test-TC014-GatewayRoutesAndInternalEndpoints {
    param([TestReporter]$Reporter, [string]$GatewayUrl)
    Invoke-TestCase $Reporter 'TC-014' '网关旧路由和内部隔离' {
        $session = Open-TestBffSession $Reporter
        $Reporter.Context.BffSessions.Add($session)
        foreach ($path in @('/blade-system/feign/client/user-authentication/user?userId=1', '/blade-system/feign/client/data-scope/by-code?code=test')) {
            Assert-TestStatus (Invoke-TestHttp ($GatewayUrl + $path) -WebSession $session) @(403) '登录后外部内部接口'
        }
        Assert-TestStatus (Invoke-TestHttp "$GatewayUrl/non-existent-route-9999" -WebSession $session) @(404) '未知路由'
        Assert-TestStatus (Invoke-TestHttp "$GatewayUrl/blade-system/user/info" -WebSession $session) @(200) 'system 旧路由'
        @{ Status = 'BLOCK'; Details = '真实网关登录后内部接口 403、未知路径 404、system 旧路由已验证；其他资源服务的路由及全站回归待执行，未检查这些服务的启动状态' }
    }
}
function Test-TC023-TC025-TokenRelayAndBladeUserCompat {
    param([TestReporter]$Reporter, [string]$GatewayUrl, [string]$ResourceUrl)
    Invoke-TestCase $Reporter 'TC-023' 'TokenRelay 与伪造身份头' {
        $session = Open-TestBffSession $Reporter
        $Reporter.Context.BffSessions.Add($session)
        $response = Invoke-TestHttp "$GatewayUrl/bff/identity" -WebSession $session -Headers @{ 'X-User-Id' = '999999'; 'X-Tenant-Id' = '999999'; 'X-User-Roles' = 'administrator' }
        Assert-TestStatus $response @(200) 'TokenRelay 身份'
        $identity = (Read-TestJson $response).data
        $claims = Read-TestJwtClaims (Get-TestUserTokens $Reporter).access_token
        if ($identity.subject -cne $claims.sub -or $identity.tenantId -cne $claims.tenant_id) { throw '伪造身份头覆盖了已验证身份' }
        Assert-TestStatus (Invoke-TestHttp "$ResourceUrl/authorization/messages" -Headers @{ 'X-User-Id' = '1'; 'X-Tenant-Id' = '000000' }) @(401) '直连伪造身份'
        @{ Status = 'BLOCK'; Details = '真实 BFF TokenRelay 与伪造头不覆盖身份、直连无 Token=401 已验证；全部资源服务的独立校验矩阵待执行' }
    }
    Invoke-TestCase $Reporter 'TC-025' 'JWT 与现有 BladeUser 权限字段' {
        $tokens = Get-TestUserTokens $Reporter
        $claims = Read-TestJwtClaims $tokens.access_token
        if (-not $claims.user_id -or -not $claims.tenant_id -or -not $claims.role_id -or -not $claims.dept_id) { throw '签发 JWT 缺少 user_id/tenant_id/role_id/dept_id，无法保持现有权限字段' }
        $response = Invoke-TestHttp "$($Reporter.Context.SystemUrl)/user/info" -Headers @{ Authorization = "Bearer $($tokens.access_token)" }
        Assert-TestStatus $response @(200) 'BladeUser Controller'
        $identity = (Read-TestJson $response).data
        if ([string]$identity.id -cne [string]$claims.user_id -or $identity.tenantId -cne $claims.tenant_id) { throw 'BladeUser 与签发身份不一致' }
        $response = Invoke-TestHttp "$($Reporter.Context.SystemUrl)/menu/routes" -Headers @{ Authorization = "Bearer $($tokens.access_token)" }
        Assert-TestStatus $response @(200) '角色菜单'
        if (@((Read-TestJson $response).data).Count -eq 0) { throw '角色菜单为空' }
        @{ Status = 'BLOCK'; Details = 'JWT 基础权限字段、BladeUser Controller 和角色菜单已验证；数据权限算法与跨租户回归待执行' }
    }
}
