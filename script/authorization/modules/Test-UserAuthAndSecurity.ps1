#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

依据真实断言记录结果，未执行验收步骤不得标为通过。
#>
function Test-TC006-UserAuthAndTenantConstraints {
    param([TestReporter]$Reporter, [string]$Issuer, [string]$ValidTenantId, [string]$ValidAccount, [string]$ValidPassword)
    Invoke-TestCase $Reporter 'TC-006' '真实密码与租户拒绝' {
        if (-not $ValidPassword) { throw 'BLOCK: 缺少测试用户密码' }
        foreach ($negative in @('password', 'tenant')) {
            $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
            $Reporter.Context.BffSessions.Add($session)
            $response = Invoke-TestHttp "$Issuer/ui/login-context" -WebSession $session
            Assert-TestStatus $response @(200) '负向登录 CSRF'
            $csrf = (Read-TestJson $response).data.csrf
            $form = @{ username = $ValidAccount; password = $ValidPassword; tenantId = $ValidTenantId }
            $form[$csrf.parameterName] = $csrf.token
            if ($negative -eq 'password') { $form.password = 'Incorrect-' + $Reporter.Prefix }
            else { $form.tenantId = '999999' }
            $response = Invoke-TestHttp "$Issuer/login" -Method POST -Body $form -WebSession $session
            Assert-TestStatus $response @(302) "负向登录 $negative"
            if ((Get-TestLocation $response) -notmatch 'error=credentials') { throw "负向登录 $negative 未到达凭据校验（不能把 CSRF 拒绝算作密码/租户通过）" }
            $response = Invoke-TestHttp "$Issuer/ui/login-context" -WebSession $session
            Assert-TestStatus $response @(200) '拒绝后的认证状态'
            if ((Read-TestJson $response).data.authenticated -ne $false) { throw '负向登录建立了认证会话' }
        }
        $Reporter.Context.AuthSession = New-TestLoginSession $Reporter
        @{ Status = 'BLOCK'; Details = '携带有效 CSRF 的错误密码、越租户登录确实到达凭据校验并拒绝；正常登录成功；旧摘要迁移/停用用户/租户夹具待准备' }
    }
}
function Test-TC007-RateLimitAndLockout {
    param([TestReporter]$Reporter, [string]$Issuer, [string]$TenantId)
    Invoke-TestCase $Reporter 'TC-007' '限流、TTL 和可信来源' {
        @{ Status = 'BLOCK'; Details = '未提供独立测试来源与限流计数清理夹具；不对共享本地 IP 连续失败以免锁定其他用户；不再无条件 PASS' }
    }
}
