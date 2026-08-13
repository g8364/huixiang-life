$ErrorActionPreference = "Stop"
$mysql = "E:\MySQL\mysql-8.0.18-winx64\bin\mysql.exe"
$redis = "D:\redis\redis-cli.exe"
$csvPath = Join-Path $PSScriptRoot "seckill-users-500.csv"

if (-not (Test-Path $csvPath)) { throw "Missing token CSV: $csvPath" }
$data = Import-Csv $csvPath
if ($data.Count -ne 500) { throw "Expected 500 CSV rows, found $($data.Count)" }

$cleanupSql = @"
START TRANSACTION;
DELETE o FROM tb_voucher_order o
JOIN tb_user u ON u.id=o.user_id
WHERE u.phone BETWEEN '19990000001' AND '19990000500';
UPDATE tb_seckill_voucher SET stock=300 WHERE voucher_id=10;
COMMIT;
"@
& $mysql -N -B -uroot -proot hmdp -e $cleanupSql
if ($LASTEXITCODE -ne 0) { throw "MySQL cleanup failed" }

foreach ($row in $data) {
    & $redis SREM "seckill:order:10" $row.userId | Out-Null
}
& $redis SET "seckill:stock:10" 300 | Out-Null

$rateKeys = & $redis --scan --pattern "rate:limit:voucher-order:seckill:*"
foreach ($key in $rateKeys) {
    if ($key) { & $redis DEL $key | Out-Null }
}

Write-Host "Load-test orders and qualifications removed; 500 users/tokens retained; voucher 10 stock restored to 300."
