#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

依据真实断言记录结果，未执行验收步骤不得标为通过。
#>
function Test-TC003-ClientRegistrationConstraints {
    param([TestReporter]$Reporter, [string]$Issuer)
    Invoke-TestCase $Reporter 'TC-003' '客户端管理校验入口' {
        $tokens = Get-TestUserTokens $Reporter
        $headers = @{ Authorization = "Bearer $($tokens.access_token)" }
        $response = Invoke-TestHttp "$($Reporter.Context.SystemUrl)/client/list?size=1" -Headers $headers
        Assert-TestStatus $response @(200) '管理员客户端管理入口'
        if ((Read-TestJson $response).success -ne $true) { throw '客户端管理权限或业务响应失败' }
        @{ Status = 'BLOCK'; Details = '真实管理员客户端管理入口已验证；创建/非法配置/重复键/数据库副作用需独立客户端夹具与精确清理，不以禁用的动态注册端点 404 代替' }
    }
}
function Test-TC008-ClientCredentialsAndScopes {
    param([TestReporter]$Reporter, [string]$Issuer, [string]$ResourceUrl, [string]$ServiceClientId, [string]$ServiceClientSecret)
    Invoke-TestCase $Reporter 'TC-008' 'client_credentials 与 scope' {
        $headers = Get-TestClientHeaders $ServiceClientId $ServiceClientSecret
        $response = Invoke-TestHttp "$Issuer/oauth2/token" -Method POST -Headers $headers -Body @{ grant_type = 'client_credentials'; scope = 'invalid.scope.unauthorized' }
        Assert-TestStatus $response @(400) '未登记 scope'
        if ((Read-TestJson $response).error -ne 'invalid_scope') { throw '未登记 scope 没有返回 invalid_scope' }
        $response = Invoke-TestHttp "$Issuer/oauth2/token" -Method POST -Headers $headers -Body @{ grant_type = 'client_credentials'; scope = 'demo.read' }
        Assert-TestStatus $response @(200) '服务令牌'
        $tokens = Read-TestJson $response
        Save-TestTokens $Reporter $tokens $ServiceClientId $ServiceClientSecret
        $Reporter.Context.ServiceTokens = $tokens
        $bearer = @{ Authorization = "Bearer $($tokens.access_token)" }
        Assert-TestStatus (Invoke-TestHttp "$ResourceUrl/authorization/messages" -Headers $bearer) @(200) 'demo.read'
        Assert-TestStatus (Invoke-TestHttp "$ResourceUrl/authorization/restricted" -Headers $bearer) @(403) '缺少 demo.write'
        Assert-TestStatus (Invoke-TestHttp "$($Reporter.Context.SystemUrl)/feign/client/user-authentication/user?userId=1" -Headers $bearer) @(401) '错误资源 audience'
        @{ Status = 'PASS'; Details = '实际服务令牌签发=200、非法 scope=400/invalid_scope、资源读取=200、缺 scope=403、错误 audience=401' }
    }
}
