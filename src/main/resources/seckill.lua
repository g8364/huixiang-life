--
-- Created by IntelliJ IDEA.
-- User: HP
-- Date: 2025-12-21
-- Time: 15:29
-- To change this template use File | Settings | File Templates.
--基于Lua脚本，判断秒杀库存、一人一单，决定用户是否抢购成功

-- 1. 参数列表
-- 1.1 优惠券的id
local voucherId=ARGV[1]

-- 1.2用户的id
local userId=ARGV[2]

--2.数据key
--2.1 库存key
local stockKey='seckill:stock:'..voucherId
--2.2 订单key
local orderKey='seckill:order:'..voucherId

--3.脚本业务
--3.1判断库存是不是充足
--redis.call('GET',stockKey),因为存的时候是字符串类型，所以取得时候也是字符串类型，这时候就需要把它转换成数字
local stock = tonumber(redis.call("GET", stockKey))
if(stock == nil or stock <= 0) then
    --3.2 库存不足，则返回1
    return 1
end

--3.2 判断用户是否下单 SISMEMBER orderKey uesId
if(redis.call('SISMEMBER',orderKey,userId)==1) then
--    3.3存在则表示重复下单
    return 2
end

--3.4.扣减库存
redis.call('incrby',stockKey,-1)
--3.5.保存用户
redis.call('sadd',orderKey,userId)

return 0
