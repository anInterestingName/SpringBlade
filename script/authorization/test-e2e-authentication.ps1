#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

端到端模拟测试：从用户登录、OAuth2/OIDC 授权码与客户端凭据，到资源服务（blade-demo）接口验签与权限校验。
测试由用户或自动化执行，严格遵守安全底线，不向控制台输出密码、完整 Token 或密钥明文。
#>
[CmdletBinding()]
param(
    [string]$Issuer = '',
    [string]$ResourceUrl = 'http://127.0.0.1:8200',
    [string]$TenantId = '000000',
    [string]$Account = 'admin',
    [string]$Password = 'admin',
    [string]$ClientId = 'gateway-bff',
    [string]$ClientSecret = '',
    [string]$ServiceClientId = 'demo-service',
    [string]$ServiceClientSecret = '',
    [string]$RedirectUri = 'http://127.0.0.1:8200/authorization/callback',
    [switch]$SkipUserLogin
)

$ErrorActionPreference = 'Stop'

# ==========================================
# 1. 配置加载与环境变量解析
# ==========================================
$localConfig = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.springblade/authorization-local.properties'
$localSecrets = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.springblade/authorization-secrets.properties'

if ([string]::IsNullOrWhiteSpace($Issuer)) {
    if (Test-Path -LiteralPath $localConfig) {
        $line = Get-Content -LiteralPath $localConfig | Where-Object { $_ -match '^AUTHORIZATION_ISSUER=' } | Select-Object -First 1
        if ($line) { $Issuer = $line.Split('=', 2)[1] }
    }
    if ([string]::IsNullOrWhiteSpace($Issuer)) { $Issuer = 'http://127.0.0.1:8100' }
}

if ([string]::IsNullOrWhiteSpace($ClientSecret)) {
    $ClientSecret = [Environment]::GetEnvironmentVariable('AUTHORIZATION_CLIENT_SECRET')
    if ([string]::IsNullOrWhiteSpace($ClientSecret) -and (Test-Path -LiteralPath $localSecrets)) {
        $line = Get-Content -LiteralPath $localSecrets | Where-Object { $_ -match '^AUTHORIZATION_CLIENT_SECRET=' } | Select-Object -First 1
        if ($line) { $ClientSecret = $line.Split('=', 2)[1] }
    }
}

if ([string]::IsNullOrWhiteSpace($ServiceClientSecret)) {
    $ServiceClientSecret = [Environment]::GetEnvironmentVariable('AUTHORIZATION_SERVICE_CLIENT_SECRET')
    if ([string]::IsNullOrWhiteSpace($ServiceClientSecret) -and (Test-Path -LiteralPath $localSecrets)) {
        $line = Get-Content -LiteralPath $localSecrets | Where-Object { $_ -match '^AUTHORIZATION_SERVICE_CLIENT_SECRET=' } | Select-Object -First 1
        if ($line) { $ServiceClientSecret = $line.Split('=', 2)[1] }
    }
}

# 若未提供用户密码且未跳过用户登录流程，提示输入
if (-not $SkipUserLogin -and [string]::IsNullOrWhiteSpace($Password)) {
    $Password = [Environment]::GetEnvironmentVariable('BLADE_TEST_PASSWORD')
    if ([string]::IsNullOrWhiteSpace($Password)) {
        $secPwd = Read-Host "请输入测试用户 [$Account] 的密码（输入将隐藏）" -AsSecureString
        $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secPwd)
        $Password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($BSTR)
    }
}

# ==========================================
# 2. 辅助函数定义
# ==========================================
$testResults = [System.Collections.Generic.List[PSCustomObject]]::new()

function Log-Test {
    param(
        [string]$Id,
        [string]$Description,
        [bool]$Success,
        [string]$Details = ''
    )
    $status = if ($Success) { 'PASS' } else { 'FAIL' }
    $testResults.Add([PSCustomObject]@{
        Id          = $Id
        Description = $Description
        Status      = $status
        Details     = $Details
    })
    $color = if ($Success) { 'Green' } else { 'Red' }
    Write-Host "[$status] $Id : $Description" -ForegroundColor $color
    if (-not $Success -and -not [string]::IsNullOrWhiteSpace($Details)) {
        Write-Host "       原因: $Details" -ForegroundColor Yellow
    }
}

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Invoke-Http {
    param(
        [string]$Uri,
        [string]$Method = 'GET',
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [string]$ContentType = 'application/x-www-form-urlencoded',
        [Microsoft.PowerShell.Commands.WebRequestSession]$WebSession = $null,
        [int]$TimeoutSec = 15,
        [int]$MaximumRedirection = -1
    )
    $params = @{
        Uri                = $Uri
        Method             = $Method
        Headers            = $Headers
        SkipHttpErrorCheck = $true
        TimeoutSec         = $TimeoutSec
    }
    if ($MaximumRedirection -ge 0) {
        $params.MaximumRedirection = $MaximumRedirection
    }
    if ($WebSession) {
        $params.WebSession = $WebSession
    }
    if ($null -ne $Body) {
        if ($Body -is [hashtable]) {
            $params.Body = $Body
            $params.ContentType = $ContentType
        } elseif ($Body -is [string]) {
            $params.Body = $Body
            $params.ContentType = $ContentType
        }
    }
    try {
        $resp = Invoke-WebRequest @params -ErrorAction SilentlyContinue
        if ($resp -is [array]) {
            return $resp[0]
        }
        return $resp
    } catch {
        if ($_.Exception.Response) {
            return $_.Exception.Response
        }
        return [PSCustomObject]@{
            StatusCode = 0
            Content    = $_.Exception.Message
        }
    }
}

# ==========================================
# 3. 开始执行端到端测试
# ==========================================
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " SpringBlade OAuth2/OIDC 认证与资源访问自动化测试 " -ForegroundColor Cyan
Write-Host " 授权中心: $Issuer" -ForegroundColor Gray
Write-Host " 资源服务: $ResourceUrl" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Cyan

$tokensToRevoke = [System.Collections.Generic.List[string]]::new()

try {
    # ----------------------------------------------------
    # 阶段 0：服务连通性与 OIDC 发现端点检查
    # ----------------------------------------------------
    Write-Host "`n[阶段 0] 服务连通性与 OIDC 基础配置检查..." -ForegroundColor Magenta

    $discoveryResp = Invoke-Http "$Issuer/.well-known/openid-configuration"
    if ($discoveryResp.StatusCode -eq 200) {
        $meta = $discoveryResp.Content | ConvertFrom-Json
        $matched = ($meta.issuer -ceq $Issuer)
        Log-Test 'TC-001' '授权中心 OIDC 发现端点连通且 Issuer 一致' $matched "期望 $Issuer，实际 $($meta.issuer)"
    } else {
        Log-Test 'TC-001' '授权中心 OIDC 发现端点连通' $false "HTTP $($discoveryResp.StatusCode): 授权中心可能未启动"
    }

    $jwksResp = Invoke-Http "$Issuer/oauth2/jwks"
    if ($jwksResp.StatusCode -eq 200) {
        $keys = ($jwksResp.Content | ConvertFrom-Json).keys
        $hasKey = ($keys.Count -gt 0)
        $noPrivate = ($hasKey -and $null -eq $keys[0].d)
        Log-Test 'TC-002' 'JWK 公钥端点暴露且不泄露 RSA 私钥' ($hasKey -and $noPrivate) "发现公钥数量: $($keys.Count)"
    } else {
        Log-Test 'TC-002' 'JWK 公钥端点正常' $false "HTTP $($jwksResp.StatusCode)"
    }

    $resourcePublicResp = Invoke-Http "$ResourceUrl/authorization/public"
    if ($resourcePublicResp.StatusCode -eq 200) {
        $pubJson = $resourcePublicResp.Content | ConvertFrom-Json
        $ok = ($pubJson.code -eq 200)
        Log-Test 'TC-003' '资源服务 (blade-demo) 公开接口响应正常' $ok "业务状态码: $($pubJson.code)"
    } else {
        Log-Test 'TC-003' '资源服务 (blade-demo) 连通性' $false "HTTP $($resourcePublicResp.StatusCode): 资源服务可能未启动"
    }

    # ----------------------------------------------------
    # 阶段 1：未授权与无效 Token 的负向测试
    # ----------------------------------------------------
    Write-Host "`n[阶段 1] 资源端负向保护拦截检查..." -ForegroundColor Magenta

    $unauthResp = Invoke-Http "$ResourceUrl/authorization/messages"
    $is401 = ($unauthResp.StatusCode -eq 401)
    Log-Test 'TC-010' '未携带 Token 访问受保护资源被拦截返回 401' $is401 "实际返回 HTTP $($unauthResp.StatusCode)"

    $fakeTokenResp = Invoke-Http "$ResourceUrl/authorization/messages" -Headers @{ Authorization = 'Bearer invalid-mock-token-xyz' }
    $isFake401 = ($fakeTokenResp.StatusCode -eq 401)
    Log-Test 'TC-011' '伪造无效 Token 访问受保护资源被拦截返回 401' $isFake401 "实际返回 HTTP $($fakeTokenResp.StatusCode)"

    # ----------------------------------------------------
    # 阶段 2：服务间客户端凭据模式验证 (Client Credentials)
    # ----------------------------------------------------
    Write-Host "`n[阶段 2] 客户端凭据模式 (M2M) 验证..." -ForegroundColor Magenta
    if (-not [string]::IsNullOrWhiteSpace($ServiceClientSecret)) {
        $serviceCredentials = '{0}:{1}' -f ([Uri]::EscapeDataString($ServiceClientId)), ([Uri]::EscapeDataString($ServiceClientSecret))
        $basicHeader = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($serviceCredentials))
        $serviceHeaders = @{ Authorization = $basicHeader }

        # 越权 Scope 申请拦截
        $badScopeResp = Invoke-Http "$Issuer/oauth2/token" -Method POST -Headers $serviceHeaders -Body @{
            grant_type = 'client_credentials'
            scope      = 'demo.write'
        }
        $scopeBlocked = ($badScopeResp.StatusCode -eq 400)
        Log-Test 'TC-020' '服务客户端越权申请未分配 scope 被拒绝 (400)' $scopeBlocked "返回 HTTP $($badScopeResp.StatusCode)"

        # 正常获取客户端 Token
        $m2mResp = Invoke-Http "$Issuer/oauth2/token" -Method POST -Headers $serviceHeaders -Body @{
            grant_type = 'client_credentials'
            scope      = 'demo.read'
        }
        if ($m2mResp.StatusCode -eq 200) {
            $m2mTokenJson = $m2mResp.Content | ConvertFrom-Json
            $m2mToken = $m2mTokenJson.access_token
            $tokensToRevoke.Add($m2mToken)
            Log-Test 'TC-021' '客户端凭据获取 Access Token 成功' (-not [string]::IsNullOrWhiteSpace($m2mToken)) "Token 类型: $($m2mTokenJson.token_type)"

            # 访问资源
            $m2mResResp = Invoke-Http "$ResourceUrl/authorization/messages" -Headers @{ Authorization = "Bearer $m2mToken" }
            if ($m2mResResp.StatusCode -eq 200) {
                $m2mResJson = $m2mResResp.Content | ConvertFrom-Json
                $subMatches = ($m2mResJson.data.subject -ceq $ServiceClientId)
                Log-Test 'TC-022' '持客户端凭据 Token 成功访问资源接口并校验主体' $subMatches "Subject: $($m2mResJson.data.subject)"
            } else {
                Log-Test 'TC-022' '持客户端凭据 Token 访问资源接口' $false "HTTP $($m2mResResp.StatusCode)"
            }

            # 权限不足接口拦截 (demo.write)
            $restrictedResp = Invoke-Http "$ResourceUrl/authorization/restricted" -Headers @{ Authorization = "Bearer $m2mToken" }
            $is403 = ($restrictedResp.StatusCode -eq 403)
            Log-Test 'TC-023' 'Token 缺少 demo.write 权限访问受限接口被拒绝 (403)' $is403 "实际返回 HTTP $($restrictedResp.StatusCode)"
        } else {
            Log-Test 'TC-021' '客户端凭据获取 Access Token' $false "HTTP $($m2mResp.StatusCode): $($m2mResp.Content)"
        }
    } else {
        Write-Host "       [跳过] 未提供 AUTHORIZATION_SERVICE_CLIENT_SECRET，跳过 M2M 凭据测试。" -ForegroundColor Yellow
    }

    # ----------------------------------------------------
    # 阶段 3：真实用户登录与授权码 + PKCE 流程验证
    # ----------------------------------------------------
    if (-not $SkipUserLogin) {
        Write-Host "`n[阶段 3] 模拟用户登录与授权码 (Auth Code + PKCE) 验证..." -ForegroundColor Magenta

        # 3.1 生成 PKCE 与 State
        $verifierBytes = [byte[]]::new(32)
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($verifierBytes)
        $codeVerifier = ConvertTo-Base64Url $verifierBytes

        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        $challengeBytes = $sha256.ComputeHash([System.Text.Encoding]::ASCII.GetBytes($codeVerifier))
        $codeChallenge = ConvertTo-Base64Url $challengeBytes

        $stateBytes = [byte[]]::new(24)
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($stateBytes)
        $state = ConvertTo-Base64Url $stateBytes

        # 3.2 建立 WebSession 并获取 CSRF Token
        $webSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
        $contextResp = Invoke-Http "$Issuer/ui/login-context" -WebSession $webSession
        $csrfToken = ''
        $csrfParam = '_csrf'
        if ($contextResp.StatusCode -eq 200) {
            $ctxJson = $contextResp.Content | ConvertFrom-Json
            if ($ctxJson.data -and $ctxJson.data.csrf) {
                $csrfToken = $ctxJson.data.csrf.token
                $csrfParam = $ctxJson.data.csrf.parameterName
            }
        }
        $hasCsrf = (-not [string]::IsNullOrWhiteSpace($csrfToken))
        Log-Test 'TC-030' '获取登录上下文及 CSRF 防御令牌' $hasCsrf "CSRF 参数名: $csrfParam"

        if ($hasCsrf) {
            # 3.3 模拟用户账号密码表单登录 POST /login（设置 MaximumRedirection=0 避免跟随前端静态地址）
            $loginForm = @{
                username  = $Account
                account   = $Account
                password  = $Password
                tenantId  = $TenantId
                $csrfParam = $csrfToken
            }
            $loginResp = Invoke-Http "$Issuer/login" -Method POST -Body $loginForm -WebSession $webSession -MaximumRedirection 0
            $loginSuccess = ($loginResp.StatusCode -in @(200, 302) -and ($null -eq $loginResp.Headers['Location'] -or $loginResp.Headers['Location'] -notmatch 'error='))
            Log-Test 'TC-031' '模拟用户提交凭据登录 (/login)' $loginSuccess "HTTP $($loginResp.StatusCode)$(if ($loginResp.Headers['Location']) { ', Location=' + $loginResp.Headers['Location'] })"

            # 3.4 发起授权请求 GET /oauth2/authorize
            $authParams = [ordered]@{
                response_type         = 'code'
                client_id             = $ClientId
                redirect_uri          = $RedirectUri
                scope                 = 'openid profile demo.read'
                state                 = $state
                code_challenge        = $codeChallenge
                code_challenge_method = 'S256'
            }
            $authQuery = ($authParams.GetEnumerator() | ForEach-Object {
                '{0}={1}' -f [Uri]::EscapeDataString($_.Key), [Uri]::EscapeDataString($_.Value)
            }) -join '&'
            $authUrl = "$Issuer/oauth2/authorize?$authQuery"

            $authorizeResp = Invoke-Http $authUrl -WebSession $webSession -MaximumRedirection 0
            $redirectLoc = [string]($authorizeResp.Headers['Location'] | Select-Object -First 1)
            if ($authorizeResp.StatusCode -eq 302 -and -not [string]::IsNullOrWhiteSpace($redirectLoc)) {
                # 检查是否重定向到同意授权页面
                if ($redirectLoc -match '/consent') {
                    Write-Host "       [注意] 客户端需要交互同意授权，尝试模拟同意确认提交..." -ForegroundColor Yellow
                    # 模拟提交同意
                    $consentForm = @{
                        client_id  = $ClientId
                        state      = $state
                        scope      = 'openid profile demo.read'
                        $csrfParam = $csrfToken
                    }
                    $authorizeResp = Invoke-Http "$Issuer/oauth2/authorize" -Method POST -Body $consentForm -WebSession $webSession -MaximumRedirection 0
                    $redirectLoc = [string]($authorizeResp.Headers['Location'] | Select-Object -First 1)
                }

                # 验证回调 URL
                $code = $null
                if ($redirectLoc -match '[\?&]code=([^&]+)') {
                    $code = [System.Web.HttpUtility]::UrlDecode($Matches[1])
                }
                $hasCode = (-not [string]::IsNullOrWhiteSpace($code))
                Log-Test 'TC-032' '获取到授权码 (OAuth2 Authorization Code)' $hasCode "重定向地址已拦截: $redirectLoc"

                if ($hasCode) {
                    # 3.5 使用授权码与 Code Verifier 换取 Token
                    $tokenHeaders = @{}
                    if (-not [string]::IsNullOrWhiteSpace($ClientSecret)) {
                        $authStr = '{0}:{1}' -f ([Uri]::EscapeDataString($ClientId)), ([Uri]::EscapeDataString($ClientSecret))
                        $tokenHeaders.Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($authStr))
                    }
                    $tokenBody = @{
                        grant_type    = 'authorization_code'
                        code          = $code
                        redirect_uri  = $RedirectUri
                        code_verifier = $codeVerifier
                        client_id     = $ClientId
                    }
                    $tokenExchangeResp = Invoke-Http "$Issuer/oauth2/token" -Method POST -Headers $tokenHeaders -Body $tokenBody
                    if ($tokenExchangeResp.StatusCode -eq 200) {
                        $userTokens = $tokenExchangeResp.Content | ConvertFrom-Json
                        $userAccess = $userTokens.access_token
                        $userRefresh = $userTokens.refresh_token
                        $tokensToRevoke.Add($userAccess)

                        Log-Test 'TC-033' '授权码与 PKCE 换取令牌组成功' (-not [string]::IsNullOrWhiteSpace($userAccess)) "包含 OIDC ID Token: $(-not [string]::IsNullOrWhiteSpace($userTokens.id_token))"

                        # 3.6 验证 OIDC UserInfo
                        $userInfoResp = Invoke-Http "$Issuer/userinfo" -Headers @{ Authorization = "Bearer $userAccess" }
                        $hasUserInfo = ($userInfoResp.StatusCode -eq 200)
                        Log-Test 'TC-034' '持用户 Token 访问 OIDC /userinfo 获取用户信息' $hasUserInfo "HTTP $($userInfoResp.StatusCode)"

                        # 3.7 携带用户 Access Token 访问资源接口
                        $userResResp = Invoke-Http "$ResourceUrl/authorization/messages" -Headers @{ Authorization = "Bearer $userAccess" }
                        if ($userResResp.StatusCode -eq 200) {
                            $resObj = $userResResp.Content | ConvertFrom-Json
                            Log-Test 'TC-035' '持用户 Token 成功访问资源服务受保护接口' ($resObj.code -eq 200) "返回信息: $($resObj.data.message)"
                        } else {
                            Log-Test 'TC-035' '持用户 Token 访问资源服务受保护接口' $false "HTTP $($userResResp.StatusCode)"
                        }

                        # 3.8 刷新令牌与防重放安全验证 (Refresh Token)
                        if (-not [string]::IsNullOrWhiteSpace($userRefresh) -and -not [string]::IsNullOrWhiteSpace($ClientSecret)) {
                            Write-Host "`n[阶段 4] 刷新令牌轮换与防重放测试..." -ForegroundColor Magenta

                            $refreshResp = Invoke-Http "$Issuer/oauth2/token" -Method POST -Headers $tokenHeaders -Body @{
                                grant_type    = 'refresh_token'
                                refresh_token = $userRefresh
                            }
                            if ($refreshResp.StatusCode -eq 200) {
                                $newTokens = $refreshResp.Content | ConvertFrom-Json
                                $newTokenRotated = ($newTokens.refresh_token -and $newTokens.refresh_token -cne $userRefresh)
                                Log-Test 'TC-040' 'Refresh Token 成功轮换换取新令牌' $newTokenRotated "新旧 Refresh Token 已隔离"
                                if ($newTokens.access_token) { $tokensToRevoke.Add($newTokens.access_token) }

                                # 负向验证：重用旧 Refresh Token 必须被拒绝
                                $reuseResp = Invoke-Http "$Issuer/oauth2/token" -Method POST -Headers $tokenHeaders -Body @{
                                    grant_type    = 'refresh_token'
                                    refresh_token = $userRefresh
                                }
                                $is400 = ($reuseResp.StatusCode -eq 400)
                                Log-Test 'TC-041' '防重放：重复使用已作废的旧 Refresh Token 被拒绝 (400)' $is400 "实际返回 HTTP $($reuseResp.StatusCode)"
                            } else {
                                Log-Test 'TC-040' 'Refresh Token 换取新令牌' $false "HTTP $($refreshResp.StatusCode)"
                            }
                        }
                    } else {
                        Log-Test 'TC-033' '授权码与 PKCE 换取令牌组' $false "HTTP $($tokenExchangeResp.StatusCode): $($tokenExchangeResp.Content)"
                    }
                }
            } else {
                Log-Test 'TC-032' '获取到授权码 (OAuth2 Code)' $false "未发生重定向或未获取到授权回调地址 (HTTP $($authorizeResp.StatusCode))"
            }
        }
    }
} finally {
    # ----------------------------------------------------
    # 清理阶段：撤销本次测试生成的 Access Token
    # ----------------------------------------------------
    if ($tokensToRevoke.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace($ServiceClientSecret)) {
        Write-Host "`n[清理] 正在撤销本次测试生成的 $($tokensToRevoke.Count) 个 Access Token..." -ForegroundColor Gray
        $clientCredentials = '{0}:{1}' -f ([Uri]::EscapeDataString($ServiceClientId)), ([Uri]::EscapeDataString($ServiceClientSecret))
        $cleanHeaders = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($clientCredentials)) }
        foreach ($tok in $tokensToRevoke) {
            try {
                $null = Invoke-Http "$Issuer/oauth2/revoke" -Method POST -Headers $cleanHeaders -Body @{
                    token           = $tok
                    token_type_hint = 'access_token'
                }
            } catch {}
        }
    }

    # ----------------------------------------------------
    # 输出汇总报告表格
    # ----------------------------------------------------
    Write-Host "`n==========================================================" -ForegroundColor Cyan
    Write-Host "                      测试结果汇总表                      " -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Cyan

    $passCount = ($testResults | Where-Object { $_.Status -eq 'PASS' }).Count
    $failCount = ($testResults | Where-Object { $_.Status -eq 'FAIL' }).Count
    $totalCount = $testResults.Count

    $testResults | Format-Table -Property Id, Status, Description, Details -AutoSize

    Write-Host "----------------------------------------------------------"
    $summaryColor = if ($failCount -eq 0) { 'Green' } else { 'Yellow' }
    Write-Host "总计用例: $totalCount | 通过: $passCount | 失败: $failCount" -ForegroundColor $summaryColor
    Write-Host "==========================================================`n"
}
