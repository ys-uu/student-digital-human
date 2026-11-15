# ============================================================
# 反爬虫 / 接口防刷验收脚本
#
# 用法（PowerShell 7+）：
#   pwsh -ExecutionPolicy Bypass -File scripts/anti-crawler-check.ps1
#   pwsh -ExecutionPolicy Bypass -File scripts/anti-crawler-check.ps1 `
#        -BaseUrl http://localhost:8080 -NginxUrl http://localhost:8090
#
# 说明：
#   - 默认直连后端 8080，通过 X-Real-IP 为每个用例模拟不同客户端 IP；
#   - 脚本会写入 Redis 风控 key，结束后自动清理（-KeepState 可保留现场）；
#   - 若后端未启动、Nginx 未启动或 Docker CLI 不可用，对应用例会标记 SKIP/FAIL；
#   - 当前 Java 后端尚未容器化，Nginx 访问 backend:8080 返回 502 时，
#     “Nginx 正常转发”会标记为 SKIP；容器化部署后可加 -RequireNginxBackend 严格校验。
# ============================================================
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$NginxUrl = "http://localhost:8090",
    [string]$RedisContainer = "aitutor-redis",
    [switch]$SkipNginx,
    [switch]$RequireNginxBackend,
    [switch]$KeepState
)

if ($PSVersionTable.PSVersion.Major -lt 7) {
    Write-Host "需要 PowerShell 7+（Invoke-WebRequest -SkipHttpErrorCheck）。" -ForegroundColor Red
    exit 1
}

$ErrorActionPreference = "Stop"
$BrowserUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
$script:Pass = 0
$script:Fail = 0
$script:Skip = 0

function Write-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = "")
    if ($Ok) {
        $script:Pass++
        Write-Host "[PASS] $Name" -ForegroundColor Green
    } else {
        $script:Fail++
        Write-Host "[FAIL] $Name  $Detail" -ForegroundColor Red
    }
}

function Write-Skip {
    param([string]$Name, [string]$Detail = "")
    $script:Skip++
    Write-Host "[SKIP] $Name  $Detail" -ForegroundColor Yellow
}

function Invoke-Probe {
    param(
        [string]$Url,
        [string]$Method = "GET",
        [string]$Ip = "",
        [string]$UserAgent = $BrowserUA,
        [string]$Body = "",
        [int]$TimeoutSec = 10
    )

    $headers = @{}
    if ($Ip) { $headers["X-Real-IP"] = $Ip }

    $params = @{
        Uri                = $Url
        Method             = $Method
        UserAgent          = $UserAgent
        SkipHttpErrorCheck = $true
        TimeoutSec         = $TimeoutSec
        Headers            = $headers
        ErrorAction        = "Stop"
    }
    if ($Body) {
        $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes($Body)
        $params["ContentType"] = "application/json; charset=utf-8"
    }

    $response = Invoke-WebRequest @params
    return [pscustomobject]@{
        Status  = [int]$response.StatusCode
        Body    = $response.Content
        Headers = $response.Headers
    }
}

function Get-HeaderValue {
    param($Response, [string]$Name)
    $headers = $Response.Headers
    if ($null -eq $headers) { return $null }

    # Invoke-WebRequest 返回的是 Dictionary[string, IEnumerable[string]]
    if ($headers -is [System.Collections.IDictionary]) {
        foreach ($key in $headers.Keys) {
            if ($key -ieq $Name) {
                $value = $headers[$key]
                if ($value -is [string]) { return $value }
                return (@($value) -join ", ")
            }
        }
        return $null
    }

    try {
        $values = $headers.GetValues($Name)
        if ($values) { return ($values -join ", ") }
    } catch {
        # header 不存在
    }
    return $null
}

function Get-BodyCode {
    param([string]$Body)
    try {
        return [int](($Body | ConvertFrom-Json).code)
    } catch {
        return $null
    }
}

function New-LoginBody {
    param([string]$Username)
    return (@{
            username  = $Username
            password  = "WrongPass1!"
            loginType = 1
            deviceId  = "anti-crawler-probe"
        } | ConvertTo-Json -Compress)
}

$runId = [guid]::NewGuid().ToString("N").Substring(0, 8)
$testIps = @(
    "203.0.113.10",
    "203.0.113.11",
    "203.0.113.12",
    "203.0.113.13",
    "203.0.113.14",
    "203.0.113.15"
)
$testAccounts = New-Object System.Collections.Generic.List[string]

Write-Host "== 反爬虫验收：$BaseUrl ==" -ForegroundColor Cyan

# ------------------------------------------------------------
# 1. 后端可达 + 正常浏览器请求不被误伤
# ------------------------------------------------------------
try {
    $health = Invoke-Probe "$BaseUrl/api/v1/health" -Ip "203.0.113.10"
    Write-Check "健康检查可达" ($health.Status -eq 200) "status=$($health.Status)"
} catch {
    Write-Check "健康检查可达" $false $_.Exception.Message
    Write-Host "后端未启动，后续用例无法执行。" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------
# 2. 反抓取响应头 + 限流额度响应头
# ------------------------------------------------------------
$r = Invoke-Probe "$BaseUrl/api/v1/auth/refresh" -Method POST -Ip "203.0.113.11" `
    -Body '{"refreshToken":"anti-crawler-probe"}'
$robots = Get-HeaderValue $r "X-Robots-Tag"
$limitHeader = Get-HeaderValue $r "X-RateLimit-Limit"
Write-Check "接口返回反抓取响应头" `
    ($null -ne $robots -and $robots -like "*noindex*" -and $limitHeader -eq "10") `
    "status=$($r.Status) X-Robots-Tag=$robots X-RateLimit-Limit=$limitHeader"

# ------------------------------------------------------------
# 3. 可疑 User-Agent 拦截
# ------------------------------------------------------------
$r = Invoke-Probe "$BaseUrl/api/v1/auth/me" -Ip "203.0.113.12" -UserAgent "python-requests/2.31.0"
Write-Check "拦截 python-requests UA" `
    ($r.Status -eq 403 -and (Get-BodyCode $r.Body) -eq 1013) `
    "status=$($r.Status) code=$(Get-BodyCode $r.Body)"

# ------------------------------------------------------------
# 4. 登录失败锁定（同一账号连续 6 次错误密码）
# ------------------------------------------------------------
$lockAccount = "lock_$runId"
$testAccounts.Add($lockAccount)
$lockCodes = @()
for ($i = 1; $i -le 6; $i++) {
    $r = Invoke-Probe "$BaseUrl/api/v1/auth/login" -Method POST `
        -Ip "203.0.113.13" -Body (New-LoginBody $lockAccount)
    $lockCodes += (Get-BodyCode $r.Body)
}
Write-Check "连续登录失败触发锁定" ($lockCodes -contains 1006) "codes=$($lockCodes -join ',')"

# ------------------------------------------------------------
# 5. 认证接口限流（同一 IP 15 次登录请求，超过 10 次/分钟）
# ------------------------------------------------------------
$rateStatuses = @()
$rateCodes = @()
$retryAfter = $null
for ($i = 1; $i -le 15; $i++) {
    $account = "probe_${runId}_$i"
    $testAccounts.Add($account)
    $r = Invoke-Probe "$BaseUrl/api/v1/auth/login" -Method POST `
        -Ip "203.0.113.14" -Body (New-LoginBody $account)
    $rateStatuses += $r.Status
    $rateCodes += (Get-BodyCode $r.Body)
    if ($r.Status -eq 429 -and $null -eq $retryAfter) {
        $retryAfter = Get-HeaderValue $r "Retry-After"
    }
}
Write-Check "接口限流返回 429" `
    (($rateStatuses -contains 429) -and ($rateCodes -contains 1006) -and $retryAfter) `
    "statuses=$($rateStatuses -join ',') retry-after=$retryAfter"

# ------------------------------------------------------------
# 6. 蜜罐路径 + 临时封禁
# ------------------------------------------------------------
$r1 = Invoke-Probe "$BaseUrl/trap/secret" -Ip "203.0.113.15"
$r2 = Invoke-Probe "$BaseUrl/api/v1/auth/me" -Ip "203.0.113.15"
Write-Check "命中蜜罐后 IP 被临时封禁" `
    ($r1.Status -eq 403 -and $r2.Status -eq 403) `
    "honeypot=$($r1.Status) next=$($r2.Status)"

# ------------------------------------------------------------
# 7. Nginx 边缘层（可选）
# ------------------------------------------------------------
if (-not $SkipNginx) {
    try {
        $n = Invoke-Probe "$NginxUrl/api/v1/health"
        if ($n.Status -eq 200) {
            Write-Check "Nginx 正常转发" $true
        } elseif ($n.Status -eq 502 -and -not $RequireNginxBackend) {
            Write-Skip "Nginx 正常转发" `
                "status=502：Nginx 可达，但 backend:8080 不在 compose 网络内（Java 后端尚未容器化），属预期；容器化后可用 -RequireNginxBackend 严格校验"
        } else {
            Write-Check "Nginx 正常转发" $false "status=$($n.Status)"
        }

        $n = Invoke-Probe "$NginxUrl/api/v1/health" -UserAgent "python-requests/2.31.0"
        Write-Check "Nginx 拦截可疑 UA" ($n.Status -eq 403) "status=$($n.Status)"

        Write-Host "      并发发起 120 个请求验证 Nginx limit_req/limit_conn ..." -ForegroundColor DarkGray
        $burst = 1..120 | ForEach-Object -Parallel {
            try {
                $resp = Invoke-WebRequest -Uri "$using:NginxUrl/api/v1/health" `
                    -UserAgent $using:BrowserUA -SkipHttpErrorCheck -TimeoutSec 15
                [int]$resp.StatusCode
            } catch {
                0
            }
        } -ThrottleLimit 50
        Write-Check "Nginx 限流返回 429" ($burst -contains 429) `
            "429 次数=$(($burst | Where-Object { $_ -eq 429 }).Count)"
    } catch {
        Write-Skip "Nginx 边缘层" "连接失败：$($_.Exception.Message)"
    }
} else {
    Write-Skip "Nginx 边缘层" "已指定 -SkipNginx"
}

# ------------------------------------------------------------
# 8. 清理测试写入的 Redis key
# ------------------------------------------------------------
if (-not $KeepState) {
    $dockerReady = $false
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        try {
            docker exec $RedisContainer redis-cli PING 2>$null | Out-Null
            $dockerReady = ($LASTEXITCODE -eq 0)
        } catch {
            $dockerReady = $false
        }
    }

    if ($dockerReady) {
        $keys = @()
        foreach ($ip in $testIps) {
            $keys += "ai_tutor:anti:rate:auth:$ip"
            $keys += "ai_tutor:anti:rate:api:$ip"
            $keys += "ai_tutor:anti:ban:$ip"
            $keys += "ai_tutor:anti:violation:$ip"
            $keys += "ai_tutor:auth:login:fail:ip:$ip"
            $keys += "ai_tutor:auth:login:lock:ip:$ip"
        }
        foreach ($account in $testAccounts) {
            $keys += "ai_tutor:auth:login:fail:account:$account"
            $keys += "ai_tutor:auth:login:lock:account:$account"
        }
        foreach ($key in $keys) {
            docker exec $RedisContainer redis-cli DEL $key 2>$null | Out-Null
        }
        Write-Host "已清理测试 Redis key。" -ForegroundColor DarkGray
    } else {
        Write-Host "未检测到可用的 Docker/Redis 容器，跳过自动清理。" -ForegroundColor Yellow
        Write-Host "手工清理：docker exec $RedisContainer redis-cli --scan --pattern 'ai_tutor:anti:*'" -ForegroundColor Yellow
    }
}

# ------------------------------------------------------------
# 9. 汇总
# ------------------------------------------------------------
Write-Host ""
Write-Host "结果：通过 $($script:Pass)  失败 $($script:Fail)  跳过 $($script:Skip)" -ForegroundColor Cyan
if ($script:Fail -gt 0) {
    exit 1
}
exit 0