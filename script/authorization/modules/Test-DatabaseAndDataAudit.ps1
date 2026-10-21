#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

依据真实断言记录结果，未执行验收步骤不得标为通过。
#>
function Test-TC018-DataAuditAndLogSanitization {
    param([TestReporter]$Reporter, [string]$WorkspaceRoot)
    Invoke-TestCase $Reporter 'TC-018' '落盘密文与敏感日志' {
        $codec = Get-Content -LiteralPath (Join-Path $WorkspaceRoot 'blade-authorization-server/src/main/java/org/springblade/authorization/store/AuthorizationCodec.java') -Raw
        if ($codec -notmatch 'AES/GCM/NoPadding' -or $codec -notmatch 'return "v1:"') { throw '授权状态编解码没有声明 AES-GCM 信封' }
        @{ Status = 'BLOCK'; Details = '实际 AuthorizationCodec 的 AES-GCM/v1 静态检查通过；MySQL/Redis 本轮数据与运行日志尚未取证，不以源码关键字替代落盘检查；不涉及脱敏功能建设' }
    }
}
function Test-TC019-SqlUpgradeAndIdempotency {
    param([TestReporter]$Reporter, [string]$WorkspaceRoot)
    Invoke-TestCase $Reporter 'TC-019' 'MySQL 全量、升级、幂等与回滚' {
        foreach ($path in @('doc/sql/blade/blade.mysql.all.create.sql', 'doc/sql/blade/blade.mysql.upgrade.5.0.1.oauth2-persistence.sql')) {
            $sql = Get-Content -LiteralPath (Join-Path $WorkspaceRoot $path) -Raw
            if ($sql -match 'gateway-bff[^\r\n]*\x27\$2[aby]\$') { throw "BFF 种子使用无编码器前缀 BCrypt，加载器要求 {bcrypt}：$path" }
        }
        @{ Status = 'BLOCK'; Details = '两份 SQL 客户端摘要格式静态检查通过；独立空库/存量快照、真实重复执行与回滚未执行' }
    }
}
