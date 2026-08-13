# Apifox 多用户秒杀压测

## 请求

- 方法：`POST`
- 地址：`http://127.0.0.1:8081/voucher-order/seckill/10`
- Body：无
- Header `authorization`：`{{token}}`
- Header `X-Forwarded-For`：`{{clientIp}}`

如果实际有效券 ID 不是 `10`，请替换 URL 最后一段。

## 测试数据

复制 `seckill-users-template.csv`，每行填写一个不同用户的有效 token。每个用户必须使用不同手机号登录，`clientIp` 也应不同。

在 Apifox 的“测试数据”中导入 CSV，并设置为每次循环依次读取一行。

## 第一轮建议配置

- 循环次数：CSV 用户数（例如 20）
- 线程数：与用户数相同（例如 20）
- 间隔停顿：0 ms
- 遇到错误时：继续
- 保存请求/响应详情：失败请求
- Cookie：关闭

## 判断结果

HTTP 200 不等于抢购成功，应检查响应 JSON：

- `success=true`：获得下单资格，`data` 是订单号
- `success=false,errorMsg=库存不足`：正常业务失败
- `success=false,errorMsg=不能重复下单`：token 被重复使用或用户已有订单
- `success=false,errorMsg=秒杀请求过于频繁`：命中用户/IP 限流

压测完成后，还要核对 MySQL 订单数、MySQL 库存、Redis 库存三者是否一致。
