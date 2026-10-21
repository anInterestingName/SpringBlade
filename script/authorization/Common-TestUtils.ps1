#requires -Version 7.0
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

认证回归公共工具。凭据只保存在进程内；报告不包含响应正文、Token 或密码。
#>
class TestReporter {
    [System.Collections.Generic.List[object]]$Results = [System.Collections.Generic.List[object]]::new()
    [System.Collections.Generic.List[object]]$TokensToRevoke = [System.Collections.Generic.List[object]]::new()
    [hashtable]$Context = @{}
    [string]$Prefix = ('auth-rq10-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss') + '-')
    [void] Log([string]$Id, [string]$Description, [string]$Status, [string]$Details) {
        if ($Status -notin @('PASS', 'FAIL', 'BLOCK', 'SKIP')) { throw '非法测试状态' }
        if (@($this.Results | Where-Object Id -eq $Id).Count -ne 0) { throw "重复用例编号：$Id" }
        $this.Results.Add([pscustomobject]@{ Id = $Id; Description = $Description; Status = $Status; Details = $Details })
        $color = switch ($Status) { PASS { 'Green' }; FAIL { 'Red' }; default { 'Yellow' } }
        Write-Host "[$Status] $Id : $Description；$Details" -ForegroundColor $color
    }
    [void] RegisterToken([string]$Token) {
        $this.RegisterOwnedToken($Token, $this.Context.ClientId, $this.Context.ClientSecret, 'access_token')
    }
    [void] RegisterOwnedToken([string]$Token, [string]$ClientId, [string]$Secret, [string]$Hint) {
        if ([string]::IsNullOrWhiteSpace($Token)) { return }
        if (@($this.TokensToRevoke | Where-Object Token -CEQ $Token).Count -eq 0) {
            $this.TokensToRevoke.Add([pscustomobject]@{ Token = $Token; ClientId = $ClientId; Secret = $Secret; Hint = $Hint })
        }
    }
    [void] PrintSummary() {
        Write-Host ($this.Results | Sort-Object Id | Format-Table Id, Status, Description, Details -Wrap | Out-String -Width 180)
        $pass = @($this.Results | Where-Object Status -eq PASS).Count
        $fail = @($this.Results | Where-Object Status -eq FAIL).Count
        $block = @($this.Results | Where-Object Status -eq BLOCK).Count
        $skip = @($this.Results | Where-Object Status -eq SKIP).Count
        Write-Host "总计用例: $($this.Results.Count) | 通过: $pass | 失败: $fail | 阻塞: $block | 跳过: $skip"
    }
}
function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
function Invoke-TestCase {
    param([TestReporter]$Reporter, [string]$Id, [string]$Description, [scriptblock]$Action)
    try {
        $result = & $Action
        if (-not $result -or -not $result.Status -or -not $result.Details) { throw '用例没有返回可核对的执行结果' }
        $Reporter.Log($Id, $Description, $result.Status, $result.Details)
    } catch {
        $message = $_.Exception.Message
        foreach ($secret in @($Reporter.Context.Password, $Reporter.Context.ClientSecret, $Reporter.Context.ServiceClientSecret)) {
            if ($secret) { $message = $message.Replace($secret, '[REMOVED]') }
        }
        $message = $message -replace 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', '[TOKEN_REMOVED]'
        $status = if ($message.StartsWith('BLOCK:')) { 'BLOCK' } else { 'FAIL' }
        $Reporter.Log($Id, $Description, $status, $message)
    }
}
function New-PkcePair {
    $verifier = ConvertTo-Base64Url ([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    [pscustomobject]@{ Verifier = $verifier; Challenge = ConvertTo-Base64Url ([Security.Cryptography.SHA256]::HashData([Text.Encoding]::ASCII.GetBytes($verifier))) }
}
function Invoke-TestHttp {
    param([string]$Uri, [string]$Method = 'GET', [hashtable]$Headers = @{}, [object]$Body = $null,
        [string]$ContentType = 'application/x-www-form-urlencoded',
        [Microsoft.PowerShell.Commands.WebRequestSession]$WebSession = $null,
        [int]$TimeoutSec = 10, [int]$MaximumRedirection = 0)
    # HttpClient 明确禁用重定向，302 是实际响应，不能因 PowerShell 重定向上限被误判为连接失败。
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $MaximumRedirection -gt 0
    if ($MaximumRedirection -gt 0) { $handler.MaxAutomaticRedirections = $MaximumRedirection }
    if ($WebSession) { $handler.CookieContainer = $WebSession.Cookies }
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method), $Uri)
    try {
        foreach ($header in $Headers.GetEnumerator()) { $null = $request.Headers.TryAddWithoutValidation($header.Key, [string]$header.Value) }
        if ($null -ne $Body) {
            if ($Body -is [hashtable]) {
                $pairs = [Collections.Generic.List[Collections.Generic.KeyValuePair[string,string]]]::new()
                foreach ($entry in $Body.GetEnumerator()) {
                    foreach ($value in @($entry.Value)) { $pairs.Add([Collections.Generic.KeyValuePair[string,string]]::new($entry.Key, [string]$value)) }
                }
                $request.Content = [Net.Http.FormUrlEncodedContent]::new($pairs)
            } else { $request.Content = [Net.Http.StringContent]::new([string]$Body, [Text.Encoding]::UTF8, $ContentType) }
        }
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $responseHeaders = @{}
        foreach ($header in $response.Headers) { $responseHeaders[$header.Key] = @($header.Value) }
        foreach ($header in $response.Content.Headers) { $responseHeaders[$header.Key] = @($header.Value) }
        [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult(); Headers = $responseHeaders }
    } catch {
        [pscustomobject]@{ StatusCode = 0; Content = ''; Headers = @{} }
    } finally { $request.Dispose(); $client.Dispose() }
}
function Assert-TestStatus {
    param($Response, [int[]]$Expected, [string]$Operation)
    if ($Response.StatusCode -eq 0) { throw "BLOCK: $Operation 连接失败" }
    if ($Response.StatusCode -notin $Expected) { throw "$Operation：期望 HTTP $($Expected -join '/')，实际 HTTP $($Response.StatusCode)" }
}
function Read-TestJson {
    param($Response)
    try { $Response.Content | ConvertFrom-Json -ErrorAction Stop } catch { throw '响应不是有效 JSON' }
}
function Get-TestLocation {
    param($Response)
    [string](@($Response.Headers['Location'])[0])
}
function Get-TestClientHeaders {
    param([string]$ClientId, [string]$Secret)
    if ([string]::IsNullOrWhiteSpace($Secret)) { throw 'BLOCK: 缺少测试客户端凭据' }
    $value = '{0}:{1}' -f [Uri]::EscapeDataString($ClientId), [Uri]::EscapeDataString($Secret)
    @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($value)) }
}
function New-TestLoginSession {
    param([TestReporter]$Reporter, [Microsoft.PowerShell.Commands.WebRequestSession]$Session = $null)
    if ($Reporter.Context.SkipUserLogin -or [string]::IsNullOrWhiteSpace($Reporter.Context.Password)) { throw 'BLOCK: 未提供测试用户密码或选择跳过登录' }
    if (-not $Session) { $Session = [Microsoft.PowerShell.Commands.WebRequestSession]::new() }
    $Reporter.Context.BffSessions.Add($Session)
    $response = Invoke-TestHttp "$($Reporter.Context.Issuer)/ui/login-context" -WebSession $Session
    Assert-TestStatus $response @(200) '登录上下文'
    $csrf = (Read-TestJson $response).data.csrf
    if (-not $csrf.token -or -not $csrf.parameterName) { throw '登录上下文缺少 CSRF' }
    $form = @{ username = $Reporter.Context.Account; password = $Reporter.Context.Password; tenantId = $Reporter.Context.TenantId }
    $form[$csrf.parameterName] = $csrf.token
    $response = Invoke-TestHttp "$($Reporter.Context.Issuer)/login" -Method POST -Body $form -WebSession $Session
    Assert-TestStatus $response @(302) '用户登录'
    if ((Get-TestLocation $response) -match '[?&]error=') { throw '测试用户登录被拒绝' }
    $response = Invoke-TestHttp "$($Reporter.Context.Issuer)/ui/login-context" -WebSession $Session
    Assert-TestStatus $response @(200) '登录后上下文'
    if ((Read-TestJson $response).data.authenticated -ne $true) { throw '登录后未建立认证会话' }
    return $Session
}
function Complete-TestAuthorization {
    param([TestReporter]$Reporter, [string]$AuthorizationUri, [Microsoft.PowerShell.Commands.WebRequestSession]$Session)
    $response = Invoke-TestHttp $AuthorizationUri -WebSession $Session -Headers @{ Accept = 'text/html' }
    Assert-TestStatus $response @(302) '授权请求'
    $location = Get-TestLocation $response
    if ($location -match '/auth/login') {
        $Session = New-TestLoginSession $Reporter $Session
        $response = Invoke-TestHttp $AuthorizationUri -WebSession $Session -Headers @{ Accept = 'text/html' }
        Assert-TestStatus $response @(302) '登录后的授权请求'
        $location = Get-TestLocation $response
    }
    if ($location -match '/auth/consent') {
        $query = [Web.HttpUtility]::ParseQueryString(([Uri]$location).Query)
        $consentUri = "$($Reporter.Context.Issuer)/ui/consent?client_id=$([Uri]::EscapeDataString($query['client_id']))&state=$([Uri]::EscapeDataString($query['state']))"
        $consentResponse = Invoke-TestHttp $consentUri -WebSession $Session
        Assert-TestStatus $consentResponse @(200) '同意上下文'
        $consent = (Read-TestJson $consentResponse).data
        $scopes = @($consent.scopes | ForEach-Object code)
        $csrf = $consent.csrf
        if (-not $csrf.token -or $csrf.headerName -ne 'X-AUTH-XSRF-TOKEN') { throw '同意上下文缺少独立 AUTH CSRF' }
        $form = @{ client_id = $query['client_id']; state = $query['state']; scope = $scopes }
        $form[$csrf.parameterName] = $csrf.token
        $response = Invoke-TestHttp "$($Reporter.Context.Issuer)/oauth2/authorize" -Method POST -Body $form -WebSession $Session
        Assert-TestStatus $response @(302) '同意提交'
        $location = Get-TestLocation $response
    }
    if ([string]::IsNullOrWhiteSpace($location)) { throw '授权响应缺少回调' }
    $query = [Web.HttpUtility]::ParseQueryString(([Uri]$location).Query)
    if ($query['error']) { throw ("授权被拒绝：" + $query['error']) }
    if (-not $query['code']) { throw ("授权未返回 code，回跳路径：" + ([Uri]$location).AbsolutePath) }
    return $location
}
function New-TestAuthorizationCode {
    param([TestReporter]$Reporter, [string]$Scopes = '')
    if (-not $Scopes) { $Scopes = $Reporter.Context.UserScopes }
    if (-not $Scopes) { $Scopes = 'openid profile demo.read' }
    if (-not $Reporter.Context.AuthSession) { $Reporter.Context.AuthSession = New-TestLoginSession $Reporter }
    $pkce = New-PkcePair
    $state = ConvertTo-Base64Url ([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $query = @{ response_type = 'code'; client_id = $Reporter.Context.ClientId; redirect_uri = $Reporter.Context.RedirectUri;
        scope = $Scopes; state = $state; code_challenge = $pkce.Challenge; code_challenge_method = 'S256' }
    $encoded = ($query.GetEnumerator() | ForEach-Object { '{0}={1}' -f [Uri]::EscapeDataString($_.Key), [Uri]::EscapeDataString($_.Value) }) -join '&'
    $callback = Complete-TestAuthorization $Reporter "$($Reporter.Context.Issuer)/oauth2/authorize?$encoded" $Reporter.Context.AuthSession
    $callbackUri = [Uri]$callback
    if ($callbackUri.GetLeftPart([UriPartial]::Path) -cne ([Uri]$Reporter.Context.RedirectUri).GetLeftPart([UriPartial]::Path)) { throw '授权回调地址不匹配' }
    $parameters = [Web.HttpUtility]::ParseQueryString($callbackUri.Query)
    if ($parameters['state'] -cne $state) { throw '授权 state 不匹配' }
    [pscustomobject]@{ Code = $parameters['code']; Verifier = $pkce.Verifier; RedirectUri = $Reporter.Context.RedirectUri }
}
function Save-TestTokens {
    param([TestReporter]$Reporter, $Tokens, [string]$ClientId, [string]$Secret)
    if (-not $Tokens.access_token) { throw '令牌响应缺少 access_token' }
    $Reporter.RegisterOwnedToken($Tokens.access_token, $ClientId, $Secret, 'access_token')
    if ($Tokens.refresh_token) { $Reporter.RegisterOwnedToken($Tokens.refresh_token, $ClientId, $Secret, 'refresh_token') }
}
function Request-TestUserTokens {
    param([TestReporter]$Reporter, $Code)
    $headers = Get-TestClientHeaders $Reporter.Context.ClientId $Reporter.Context.ClientSecret
    $response = Invoke-TestHttp "$($Reporter.Context.Issuer)/oauth2/token" -Method POST -Headers $headers -Body @{
        grant_type = 'authorization_code'; code = $Code.Code; code_verifier = $Code.Verifier; redirect_uri = $Code.RedirectUri }
    Assert-TestStatus $response @(200) '授权码兑换'
    $tokens = Read-TestJson $response
    Save-TestTokens $Reporter $tokens $Reporter.Context.ClientId $Reporter.Context.ClientSecret
    return $tokens
}
function Get-TestUserTokens {
    param([TestReporter]$Reporter)
    if (-not $Reporter.Context.UserTokens) { $Reporter.Context.UserTokens = Request-TestUserTokens $Reporter (New-TestAuthorizationCode $Reporter) }
    return $Reporter.Context.UserTokens
}
function Read-TestJwtClaims {
    param([string]$Token)
    # 仅供对照声明；签名和权限必须由资源服务实际验证。
    $parts = $Token.Split('.')
    if ($parts.Count -ne 3) { throw '令牌不是 JWT' }
    $encoded = $parts[1].Replace('-', '+').Replace('_', '/')
    $encoded = $encoded.PadRight($encoded.Length + ((4 - $encoded.Length % 4) % 4), '=')
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encoded)) | ConvertFrom-Json
}
function Open-TestBffSession {
    param([TestReporter]$Reporter)
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $Reporter.Context.BffSessions.Add($session)
    $response = Invoke-TestHttp "$($Reporter.Context.GatewayUrl)/oauth2/authorization/blade" -WebSession $session
    Assert-TestStatus $response @(302) 'BFF 登录入口'
    $authorizationUri = Get-TestLocation $response
    if (([Uri]$authorizationUri).GetLeftPart([UriPartial]::Authority) -cne ([Uri]$Reporter.Context.Issuer).GetLeftPart([UriPartial]::Authority)) { throw 'BFF 授权目标与 issuer 不匹配' }
    $callback = Complete-TestAuthorization $Reporter $authorizationUri $session
    # 前端代理回调在测试中直达同一网关路径，保留 Cookie 和 state。
    $response = Invoke-TestHttp ($Reporter.Context.GatewayUrl + ([Uri]$callback).PathAndQuery) -WebSession $session
    Assert-TestStatus $response @(302) 'BFF 回调'
    if ((Get-TestLocation $response) -match '[?&]error=') { throw 'BFF 回调认证失败' }
    $response = Invoke-TestHttp "$($Reporter.Context.GatewayUrl)/bff/me" -WebSession $session
    Assert-TestStatus $response @(200) 'BFF 身份'
    if ((Read-TestJson $response).success -ne $true) { throw 'BFF 身份业务响应失败' }
    return $session
}
function Close-TestBffSession {
    param([TestReporter]$Reporter, [Microsoft.PowerShell.Commands.WebRequestSession]$Session)
    $response = Invoke-TestHttp "$($Reporter.Context.GatewayUrl)/bff/csrf" -WebSession $Session
    Assert-TestStatus $response @(200) 'BFF 退出 CSRF'
    $csrf = (Read-TestJson $response).data
    $headers = @{}
    $headers[$csrf.headerName] = $csrf.token
    $response = Invoke-TestHttp "$($Reporter.Context.GatewayUrl)/bff/logout" -Method POST -Headers $headers -WebSession $Session
    Assert-TestStatus $response @(200) 'BFF 退出'
}
function Clean-RegisteredTokens {
    param([TestReporter]$Reporter, [string]$Issuer, [string]$ServiceClientId, [string]$ServiceClientSecret)
    $failed = 0
    $fingerprints = [Collections.Generic.List[object]]::new()
    foreach ($owned in $Reporter.TokensToRevoke) {
        $cleared = $false
        foreach ($attempt in 1..3) {
          try {
            $headers = Get-TestClientHeaders $owned.ClientId $owned.Secret
            $response = Invoke-TestHttp "$Issuer/oauth2/revoke" -Method POST -Headers $headers -Body @{ token = $owned.Token; token_type_hint = $owned.Hint }
            Assert-TestStatus $response @(200) '测试令牌撤销'
            $response = Invoke-TestHttp "$Issuer/oauth2/introspect" -Method POST -Headers $headers -Body @{ token = $owned.Token }
            Assert-TestStatus $response @(200) '清理内省'
            if ((Read-TestJson $response).active -ne $false) { throw '清理后令牌仍有效' }
            $cleared = $true
            break
          } catch { }
        }
        if (-not $cleared) { $failed++ }
        $fingerprints.Add(@{ ClientId = $owned.ClientId; Hint = $owned.Hint; Cleared = $cleared;
            Sha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($owned.Token))).ToLowerInvariant() })
    }
    $Reporter.Context.Cleanup = @{ Registered = $Reporter.TokensToRevoke.Count; Failed = $failed; Fingerprints = @($fingerprints) }
    Write-Host "令牌清理：登记 $($Reporter.TokensToRevoke.Count)，失败 $failed；按所属客户端撤销并内省确认"
}

function Send-TestRedisCommand {
    param([Net.Sockets.NetworkStream]$Stream, [IO.StreamReader]$Reader, [string[]]$Command)
    $crlf = [string][char]13 + [char]10
    $buffer = [IO.MemoryStream]::new()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes('*' + $Command.Count + $crlf)
        $buffer.Write($bytes, 0, $bytes.Length)
        foreach ($argument in $Command) {
            $body = [Text.Encoding]::UTF8.GetBytes($argument)
            $header = [Text.Encoding]::UTF8.GetBytes('$' + $body.Length + $crlf)
            $buffer.Write($header, 0, $header.Length)
            $buffer.Write($body, 0, $body.Length)
            $ending = [Text.Encoding]::UTF8.GetBytes($crlf)
            $buffer.Write($ending, 0, $ending.Length)
        }
        $bytes = $buffer.ToArray()
        $Stream.Write($bytes, 0, $bytes.Length)
        $reply = $Reader.ReadLine()
        if (-not $reply -or $reply.StartsWith('-')) { throw 'Redis 精确清理命令失败' }
        return $reply
    } finally { $buffer.Dispose() }
}
function Remove-OwnedTestSessions {
    param([TestReporter]$Reporter)
    $configuration = $Reporter.Context.Configuration
    $redisHost = $configuration.AUTHORIZATION_REDIS_HOST
    if (-not $redisHost) { $redisHost = '127.0.0.1' }
    if ($redisHost -notin @('127.0.0.1', 'localhost', '::1')) { throw '精确清理只允许本地测试 Redis' }
    $redisPort = if ($configuration.AUTHORIZATION_REDIS_PORT) { [int]$configuration.AUTHORIZATION_REDIS_PORT } else { 6379 }
    $database = if ($configuration.AUTHORIZATION_REDIS_DATABASE) { $configuration.AUTHORIZATION_REDIS_DATABASE } else { '0' }
    $ownedKeys = [Collections.Generic.HashSet[string]]::new()
    $ownedIds = [Collections.Generic.HashSet[string]]::new()
    foreach ($session in $Reporter.Context.BffSessions) {
        foreach ($uri in @($Reporter.Context.Issuer, $Reporter.Context.GatewayUrl)) {
            foreach ($cookie in $session.Cookies.GetCookies([Uri]$uri)) {
                $namespace = switch ($cookie.Name) { BFF_SESSION { 'blade:gateway:session' }; AUTH_SESSION { 'blade:authorization:session' }; default { '' } }
                if (-not $namespace) { continue }
                $value = $cookie.Value
                $guid = [Guid]::Empty
                if (-not [Guid]::TryParse($value, [ref]$guid)) {
                    try { $value = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($value)) } catch { continue }
                }
                if (-not [Guid]::TryParse($value, [ref]$guid)) { continue }
                $id = $guid.ToString()
                $null = $ownedIds.Add($namespace + ':' + $id)
                $null = $ownedKeys.Add($namespace + ':sessions:' + $id)
                $null = $ownedKeys.Add($namespace + ':sessions:expires:' + $id)
            }
        }
    }
    $connection = [Net.Sockets.TcpClient]::new()
    $connection.ReceiveTimeout = 5000
    $connection.SendTimeout = 5000
    try {
        $connection.Connect($redisHost, $redisPort)
        $stream = $connection.GetStream()
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $false, 1024, $true)
        if ($configuration.AUTHORIZATION_REDIS_PASSWORD) {
            $null = Send-TestRedisCommand $stream $reader @('AUTH', $configuration.AUTHORIZATION_REDIS_PASSWORD)
        }
        $null = Send-TestRedisCommand $stream $reader @('SELECT', $database)
        foreach ($key in $ownedKeys) {
            $null = Send-TestRedisCommand $stream $reader @('DEL', $key)
            if ((Send-TestRedisCommand $stream $reader @('EXISTS', $key)) -ne ':0') { throw '本轮会话清理后仍存在' }
        }
        $Reporter.Context.SessionCleanup = @{ OwnedSessions = $ownedIds.Count; CheckedKeys = $ownedKeys.Count; Failed = 0;
            Method = '仅删除本轮内存 Cookie 对应的固定 namespace/UUID 键并确认不存在；不扫描或清空其他会话' }
    } finally { $connection.Dispose() }
}
