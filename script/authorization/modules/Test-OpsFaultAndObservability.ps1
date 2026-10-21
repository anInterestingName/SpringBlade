#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

依据真实断言记录结果，未执行验收步骤不得标为通过。
#>
function Test-TC021-ObservabilityAndHealth {
    param([TestReporter]$Reporter, [string]$Issuer)
    Invoke-TestCase $Reporter 'TC-021' '健康、指标与告警' {
        $response = Invoke-TestHttp "$Issuer/actuator/health"
        if ($response.StatusCode -in @(401, 403)) { throw 'BLOCK: 健康端点拒绝未认证请求，未取得 UP 证据；需运维认证/专用探针' }
        Assert-TestStatus $response @(200) 'Actuator health'
        if ((Read-TestJson $response).status -ne 'UP') { throw 'Actuator health 不是 UP' }
        @{ Status = 'BLOCK'; Details = '健康端点实际 UP；认证分类指标、依赖延迟、刷新重用告警与故障观测尚未执行' }
    }
}
function Test-TC016-TC022-FaultRecoveryAndRollbackCompat {
    param([TestReporter]$Reporter, [string]$WorkspaceRoot)
    Invoke-TestCase $Reporter 'TC-016' 'MySQL/Redis 故障恢复' {
        @{ Status = 'BLOCK'; Details = '未对共享测试基础设施执行停机/故障注入；独立实例、快照和恢复夹具待准备，配置存在不能证明恢复通过' }
    }
    Invoke-TestCase $Reporter 'TC-022' '双版本灰度和回滚' {
        @{ Status = 'BLOCK'; Details = '未切换用户正在使用的应用版本；可回滚部署夹具和独立入口待准备，配置存在不能证明演练通过' }
    }
}
