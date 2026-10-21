#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

依据真实断言记录结果，未执行验收步骤不得标为通过。
#>
function Test-TC013-ResourceJwtVerification {
    param([TestReporter]$Reporter, [string]$ResourceUrl)
    Invoke-TestCase $Reporter 'TC-013' '资源端 JWT 拒绝与正常访问' {
        foreach ($token in @('', 'bad-format-token', 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.tampered-signature')) {
            $headers = @{}
            if ($token) { $headers.Authorization = "Bearer $token" }
            Assert-TestStatus (Invoke-TestHttp "$ResourceUrl/authorization/messages" -Headers $headers) @(401) '无效 JWT'
        }
        $tokens = Get-TestUserTokens $Reporter
        Assert-TestStatus (Invoke-TestHttp "$ResourceUrl/authorization/messages" -Headers @{ Authorization = "Bearer $($tokens.access_token)" }) @(200) '有效 JWT'
        @{ Status = 'BLOCK'; Details = '有效 JWT=200、无 Token/格式错误/签名篡改=401 已验证；独立有效签名的错误 issuer/exp/nbf 夹具待准备，scope/audience 已由 TC-008 实测' }
    }
}
function Test-TC015-TC024-FeignCredentialsAndPropagation {
    param([TestReporter]$Reporter, [string]$WorkspaceRoot)
    Invoke-TestCase $Reporter 'TC-015' '内部契约 scope 与依赖降级' {
        $tokens = Get-TestUserTokens $Reporter
        $response = Invoke-TestHttp "$($Reporter.Context.SystemUrl)/feign/client/user-authentication/user?userId=1" -Headers @{ Authorization = "Bearer $($tokens.access_token)" }
        Assert-TestStatus $response @(403) '用户 Token 缺少认证内部 scope'
        foreach ($path in @('blade-service-api/blade-user-api/src/main/java/org/springblade/system/user/feign/IUserAuthenticationClientFallback.java',
            'blade-service-api/blade-system-api/src/main/java/org/springblade/system/feign/IClientRegistrationClientFallback.java')) {
            $content = Get-Content -LiteralPath (Join-Path $WorkspaceRoot $path) -Raw
            if ($content -match 'return\s+null' -or $content -notmatch 'R.fail\(503') { throw '认证契约 Fallback 未明确失败' }
        }
        @{ Status = 'BLOCK'; Details = '真实内部接口 scope 不足=403，两份实际认证 Fallback 静态 fail-closed 通过；system 停机/超时/错误结构故障注入待执行' }
    }
    Invoke-TestCase $Reporter 'TC-024' 'Feign 用户与服务 Token 传播' {
        $content = Get-Content -LiteralPath (Join-Path $WorkspaceRoot 'blade-common/src/main/java/org/springblade/common/security/feign/ServiceTokenFeignConfiguration.java') -Raw
        if ($content -match 'forEach\(registration::scope\)') { throw 'Feign 多 scope 配置逐项覆盖，只保留最后一个 scope' }
        @{ Status = 'BLOCK'; Details = '公共 Feign 多 scope 注册静态检查通过；真实 HTTP 用户调用和无请求上下文后台 Feign 调用探针待准备' }
    }
}
