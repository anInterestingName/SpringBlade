#requires -Version 7.2
<#
Copyright 2026 SpringBlade contributors.
Licensed under the Apache License, Version 2.0.
https://www.apache.org/licenses/LICENSE-2.0

由用户创建外部持久化JWK文件，不输出私钥，不覆盖已有文件。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$OutputPath,
    [string]$KeyId = [Guid]::NewGuid().ToString(),
    [ValidateRange(2048, 4096)] [int]$KeySize = 3072
)

$ErrorActionPreference = 'Stop'
$signingKeyPath = [IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $signingKeyPath) { throw '目标文件已存在，请复用已有密钥或使用新路径。' }
if ([string]::IsNullOrWhiteSpace($KeyId)) { throw '密钥编号不能为空。' }

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$signingDirectory = [IO.Path]::GetDirectoryName($signingKeyPath)
if (-not (Test-Path -LiteralPath $signingDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $signingDirectory | Out-Null
}
$rsa = [Security.Cryptography.RSA]::Create($KeySize)
try {
    $parameters = $rsa.ExportParameters($true)
    $key = [ordered]@{ kty = 'RSA'; use = 'sig'; alg = 'RS256'; kid = $KeyId }
    foreach ($entry in @{ n = 'Modulus'; e = 'Exponent'; d = 'D'; p = 'P'; q = 'Q'; dp = 'DP'; dq = 'DQ'; qi = 'InverseQ' }.GetEnumerator()) {
        $key[$entry.Key] = ConvertTo-Base64Url $parameters.($entry.Value)
    }
    $json = @{ keys = @($key) } | ConvertTo-Json -Depth 4
    $file = [IO.File]::Open($signingKeyPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try {
        if ($IsWindows) {
            $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
            $access = [Security.AccessControl.FileSecurity]::new()
            $access.SetAccessRuleProtection($true, $false)
            $access.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'Allow'))
            Set-Acl -LiteralPath $signingKeyPath -AclObject $access
        } else {
            [IO.File]::SetUnixFileMode($signingKeyPath, [IO.UnixFileMode]::UserRead -bor [IO.UnixFileMode]::UserWrite)
        }
        $bytes = [Text.Encoding]::UTF8.GetBytes($json)
        $file.Write($bytes)
    } finally { $file.Dispose() }
    Write-Host "已创建签名密钥文件；活动密钥编号：$KeyId"
} finally { $rsa.Dispose() }
