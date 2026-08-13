param(
    [int]$Count = 500,
    [string]$MysqlExe = "E:\MySQL\mysql-8.0.18-winx64\bin\mysql.exe",
    [string]$RedisCli = "D:\redis\redis-cli.exe"
)

$ErrorActionPreference = "Stop"
$phonePrefix = "19990"
$firstSuffix = 1

$rows = for ($i = $firstSuffix; $i -lt ($firstSuffix + $Count); $i++) {
    $phone = $phonePrefix + $i.ToString("D6")
    "('$phone','','load_test_$($i.ToString('D4'))','')"
}
$insertSql = "INSERT INTO tb_user(phone,password,nick_name,icon) VALUES " + ($rows -join ",") +
    " ON DUPLICATE KEY UPDATE nick_name=VALUES(nick_name);"
& $MysqlExe -N -B -uroot -proot hmdp -e $insertSql
if ($LASTEXITCODE -ne 0) { throw "MySQL insert failed" }

$usersSql = "SELECT id,phone,nick_name,icon FROM tb_user WHERE phone BETWEEN '19990000001' AND '19990000500' ORDER BY phone LIMIT $Count;"
$userLines = & $MysqlExe -N -B -uroot -proot hmdp -e $usersSql
if ($LASTEXITCODE -ne 0 -or $userLines.Count -ne $Count) {
    throw "Expected $Count users, found $($userLines.Count)"
}

$output = Join-Path $PSScriptRoot "seckill-users-500.csv"
$csvRows = [System.Collections.Generic.List[object]]::new()
$index = 0
foreach ($line in $userLines) {
    $parts = $line -split "`t", 4
    $id = $parts[0]
    $phone = $parts[1]
    $nickName = $parts[2]
    $token = [Guid]::NewGuid().ToString("N")
    $key = "login:token:$token"
    $hsetIdResult = & $RedisCli HSET $key id $id
    $hsetNameResult = & $RedisCli HSET $key nickName $nickName
    if ($LASTEXITCODE -ne 0 -or $hsetIdResult -match "^ERR" -or $hsetNameResult -match "^ERR") {
        throw "Redis HSET failed for user $id`: $hsetIdResult $hsetNameResult"
    }
    $expireResult = & $RedisCli EXPIRE $key 2160000
    if ($LASTEXITCODE -ne 0 -or $expireResult -ne "1") { throw "Redis EXPIRE failed for user $id`: $expireResult" }
    $index++
    $csvRows.Add([pscustomobject]@{
        token = $token
        clientIp = "10.20.$([math]::Floor(($index - 1) / 250)).$((($index - 1) % 250) + 1)"
        userId = $id
        phone = $phone
    })
}

$csvRows | Export-Csv -Path $output -NoTypeInformation -Encoding UTF8
Write-Host "Generated $Count users and tokens: $output"
