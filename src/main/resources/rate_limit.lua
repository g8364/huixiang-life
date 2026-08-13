local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]

-- 先检查所有维度，任一窗口达到阈值都拒绝，且不记录本次请求。
for _, key in ipairs(KEYS) do
    redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
    if redis.call('ZCARD', key) >= limit then
        return 0
    end
end

-- 所有维度均放行后，再同时记录请求。
for _, key in ipairs(KEYS) do
    redis.call('ZADD', key, now, member)
    redis.call('PEXPIRE', key, window)
end
return 1
