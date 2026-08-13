# 黑马点评：高并发改造版

基于黑马点评项目进行高并发与工程化改造，围绕秒杀异步下单、二级缓存、多维滑动窗口限流、支付状态流转和超时关单构建完整链路。

## 技术栈

- Java 8、Spring Boot 2.3.12、MyBatis-Plus
- MySQL、Redis、Redisson、Caffeine
- Kafka、Lua、Spring AOP、Spring Task
- Maven、Docker Compose、JMeter

## 核心功能

### 1. Redis + Lua + Kafka 异步秒杀

秒杀请求先在 Redis Lua 脚本中原子完成库存校验、库存预扣和一人一单校验；校验成功后生成全局订单号并投递 Kafka，由消费者异步完成 MySQL 库存扣减和订单创建，从而缩短同步链路并削减数据库瞬时压力。

可靠性措施：

- Kafka 生产者启用 `acks=all`、幂等发送和重试。
- 消费失败自动重试，最终进入死信 Topic。
- 消费端事务内执行 MySQL 条件扣减，防止数据库超卖。
- `(user_id, voucher_id)` 联合唯一索引兜底一人一单。
- 重复消息幂等处理；发送失败或死信时补偿 Redis 秒杀资格与库存。

主要代码：

- `src/main/resources/seckill.lua`
- `VoucherOrderServiceImpl#setKillVoucher`
- `VoucherOrderConsumer`
- `VoucherOrderTransactionalService`
- `VoucherOrderDltConsumer`

### 2. Caffeine + Redis 二级缓存

商户详情采用以下读取链路：

```text
请求 → Caffeine 本地缓存 → Redis 分布式缓存 → MySQL
```

- Caffeine 承接 JVM 内热点请求，减少 Redis 网络访问。
- Redis 空值缓存防止缓存穿透。
- Redis 互斥锁重建热点数据，缓解缓存击穿。
- Cache-Aside 更新：先更新数据库，再删除缓存。
- 商户更新后通过 Kafka 广播缓存失效消息，使多实例本地缓存最终一致。

主要代码：

- `LocalCacheConfig`
- `CacheClient#queryWithMutex`
- `ShopServiceImpl#queryById`
- `CacheInvalidationPublisher`
- `CacheInvalidationConsumer`

### 3. Redis 滑动窗口多维限流

使用自定义 `@RateLimit` 注解、Spring AOP 和 Redis ZSet Lua 实现原子滑动窗口限流，支持 `GLOBAL`、`USER`、`IP`、`METHOD` 四种维度。

当前业务规则：

| 接口 | 维度 | 滑动窗口阈值 |
|---|---|---:|
| 秒杀下单 `POST /voucher-order/seckill/{id}` | 用户 + IP | 5 次/秒 |
| 商户详情 `GET /shop/{id}` | 全局 + IP | 200 次/秒 |
| 商户列表 `GET /shop/of/type` | 全局 + IP | 100 次/秒 |
| 热门博客 `GET /blog/hot` | 全局 + IP | 100 次/秒 |
| 发送验证码 `POST /user/code` | IP | 3 次/60秒 |

主要代码：

- `RateLimit`
- `RateLimitAspect`
- `RateLimitDimension`
- `src/main/resources/rate_limit.lua`

### 4. 支付与超时关单

- 提供订单列表、订单详情和模拟支付接口。
- 支付通过 `订单ID + 用户ID + 待支付状态 + 支付期限` 条件更新完成状态机 CAS，避免重复支付以及支付与关单并发覆盖。
- Spring Task 周期扫描超时未支付订单，使用条件更新将订单关闭。
- 关单事务内恢复 MySQL 库存，事务提交后恢复 Redis 库存，失败任务进入重试队列。
- 默认支付期限为 15 分钟，扫描间隔为 30 秒，均支持环境变量覆盖。

> 当前支付用于验证订单状态流转与并发安全，不包含支付宝、微信支付等真实渠道签名及回调。

主要代码：

- `VoucherOrderServiceImpl#payOrder`
- `VoucherOrderCloseTask`
- `VoucherOrderCloseService`
- `RedisStockCompensationService`

## 秒杀链路

```text
客户端
  → AOP 用户/IP滑动窗口限流
  → Redis Lua：库存预扣 + 一人一单
  → RedisIdWorker 生成订单号
  → Kafka 投递订单消息
  → 立即返回订单号

Kafka Consumer
  → MySQL 条件扣减库存
  → 唯一索引校验一人一单
  → 创建待支付订单
  → 失败重试 / 死信补偿
```

## JMeter 压测结果

测试环境为本地 Windows 单机，JMeter GUI、应用、Redis、MySQL 与 Kafka 运行在同一台机器，数据仅代表该环境。

### 500 用户同步秒杀

配置：500 线程、循环 1 次、Synchronizing Timer 同步起跑、库存 300。

| 指标 | 结果 |
|---|---:|
| 请求数 | 500 |
| 下单成功 | 300 |
| 库存不足 | 200 |
| 重复订单 | 0 |
| 平均响应时间 | 2245 ms |
| 最小 / 最大响应时间 | 518 / 3760 ms |
| 平均吞吐量 | 133.0 QPS |
| MySQL / Redis 最终库存 | 0 / 0 |

### 秒杀用户限流

同一用户、同一 IP 在同一时刻发起 20 个请求：精确放行 5 个、限流 15 个；放行请求中 1 个下单成功、4 个被一人一单规则拦截。

### 商户详情全局限流

500 个不同 IP 同步访问商户详情，全局阈值为 200 次/秒：成功触发全局限流，整体平均吞吐量为 188.3 QPS。滑动窗口按请求实际到达 AOP 的时间滚动统计，因此并非固定批次只放行 200 个请求。

JMeter 测试计划位于 `load-test/`：

- `seckill-500-users.jmx`
- `seckill-user-rate-limit.jmx`
- `shop-global-rate-limit.jmx`

真实压测 token 文件 `seckill-users-500.csv` 已加入 `.gitignore`，不会提交到仓库。可先执行 `Generate-LoadTestUsers.ps1` 在本地生成；用户限流脚本中的单用户 token 通过 JMeter 属性 `rateLimitToken` 传入，禁止将有效登录 token 写入版本库。

## 环境要求

- JDK 8
- Maven 3.8+
- MySQL 5.7+
- Redis 6+
- Kafka 2.8+

也可以使用 Docker Compose 启动基础设施：

```bash
docker compose up -d
```

默认端口：MySQL `3306`、Redis `6379`、Kafka `9092`。MySQL 首次初始化会执行 `src/main/resources/db/hmdp.sql`。

## 构建与启动

```bash
mvn clean test
mvn spring-boot:run
```

后端默认监听 `http://127.0.0.1:8081`。

常用环境变量：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8081` | 后端端口 |
| `DB_URL` | `jdbc:mysql://localhost:3306/hmdp...` | MySQL 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | MySQL 账号密码 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 地址 |
| `KAFKA_BOOTSTRAP_SERVERS` | `127.0.0.1:9092` | Kafka 地址 |
| `ORDER_PAYMENT_TIMEOUT_MINUTES` | `15` | 支付超时分钟数 |
| `ORDER_CLOSE_FIXED_DELAY_MS` | `30000` | 关单扫描间隔 |

## 测试

```bash
mvn test
```

当前自动化测试覆盖缓存配置、Kafka 消费异常处理、订单事务、一人一单、支付状态流转、关单和库存补偿等关键逻辑。

## 其他功能

- Redis ZSet 实现关注用户 Feed 流推送与滚动分页。
- Redis GEO 商户地理位置查询。
- Redis Bitmap 用户签到统计。
- 博客点赞、关注关系、商户分类与优惠券管理。

## 注意事项

- 生产环境应使用密钥管理服务或环境变量保存密码，禁止提交真实凭据。
- 正式性能测试建议使用 JMeter 非 GUI 模式，并同时记录 CPU、内存、GC、Redis、Kafka 和数据库指标。
- `docker compose down -v` 会永久删除本地数据卷，执行前请确认数据不再需要。
