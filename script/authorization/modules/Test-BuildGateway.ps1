#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

依据真实断言记录结果，未执行验收步骤不得标为通过。
#>
function Test-TC001-BuildGateway {
    param([TestReporter]$Reporter, [string]$WorkspaceRoot, [switch]$RunMavenBuild)
    Invoke-TestCase $Reporter 'TC-001' '构建与敏感配置门禁' {
        foreach ($file in @('blade-gateway/src/main/resources/application.yml', 'blade-authorization-server/src/main/resources/application.yml')) {
            $content = Get-Content -LiteralPath (Join-Path $WorkspaceRoot $file) -Raw
            if ($content -match '(?im)^\s*(?:client-secret|password):\s*\$\{[A-Z0-9_]+:[^}\s]+\}') { throw "认证配置含固定凭据默认值：$file" }
        }
        if ($RunMavenBuild) {
            $buildOutput = & mvn -o compile -DskipTests -pl blade-authorization-server,blade-service/blade-system,blade-gateway,blade-service/blade-demo -am 2>&1
            if ($LASTEXITCODE -ne 0) { throw '目标模块编译失败（构建日志需独立归档）' }
            @{ Status = 'BLOCK'; Details = '目标模块离线编译及认证配置检查通过；clean package 尚未执行' }
        } else { @{ Status = 'BLOCK'; Details = '认证配置静态检查通过；未指定 RunMavenBuild，编译/打包不记为通过' } }
    }
}
