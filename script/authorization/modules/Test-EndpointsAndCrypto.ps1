#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

依据真实断言记录结果，未执行验收步骤不得标为通过。
#>
function Test-TC002-DiscoveryAndJwks {
    param([TestReporter]$Reporter, [string]$Issuer)
    Invoke-TestCase $Reporter 'TC-002' 'Discovery 与 JWKS 公钥边界' {
        $response = Invoke-TestHttp "$Issuer/.well-known/openid-configuration"
        Assert-TestStatus $response @(200) 'OIDC discovery'
        $metadata = Read-TestJson $response
        if ($metadata.issuer -cne $Issuer -or -not $metadata.authorization_endpoint -or -not $metadata.token_endpoint -or -not $metadata.jwks_uri) { throw '发现元数据不匹配' }
        if ($metadata.code_challenge_methods_supported -notcontains 'S256') { throw '发现端点未声明 S256' }
        $response = Invoke-TestHttp $metadata.jwks_uri
        Assert-TestStatus $response @(200) 'JWKS'
        $keys = @((Read-TestJson $response).keys)
        if ($keys.Count -eq 0) { throw 'JWKS 为空' }
        foreach ($key in $keys) {
            if (-not $key.kid -or $key.kty -ne 'RSA' -or -not $key.n -or -not $key.e) { throw 'JWKS 公钥结构无效' }
            foreach ($field in @('d', 'p', 'q', 'dp', 'dq', 'qi')) { if ($key.PSObject.Properties.Name -contains $field) { throw 'JWKS 含私钥字段' } }
        }
        if (($keys.kid | Sort-Object -Unique).Count -ne $keys.Count) { throw 'JWKS kid 重复' }
        $Reporter.Context.Jwks = $keys
        @{ Status = 'PASS'; Details = "Discovery HTTP 200、issuer/端点/S256 匹配；JWKS $($keys.Count) 个 RSA 公钥，无私钥字段" }
    }
}
function Test-TC017-ExternalJwkLoading {
    param([TestReporter]$Reporter, [string]$Issuer)
    Invoke-TestCase $Reporter 'TC-017' '外部 JWK 文件与错误启动配置' {
        $location = $Reporter.Context.Configuration.AUTHORIZATION_JWK_SET_LOCATION
        if (-not $location) { throw 'BLOCK: 未提供实际外部 JWK 文件位置；不能用 JWKS 可用替代错误配置启动验证' }
        if ($location.StartsWith('file:')) { $location = ([Uri]$location).LocalPath }
        if (-not (Test-Path -LiteralPath $location)) { throw '外部 JWK 文件不存在' }
        $material = Get-Content -LiteralPath $location -Raw | ConvertFrom-Json
        $localKids = @($material.keys | ForEach-Object kid)
        if (-not $Reporter.Context.Jwks -or @($Reporter.Context.Jwks | Where-Object { $_.kid -in $localKids }).Count -eq 0) { throw '外部文件与实际 JWKS 无匹配 kid' }
        @{ Status = 'BLOCK'; Details = '外部 JWK 文件与实际 JWKS kid 已匹配；错误权限/kid/算法/长度的隔离启动与替换重启尚未执行' }
    }
}
