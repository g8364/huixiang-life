package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import com.hmdp.utils.RedisData;
import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;

    //使用构造函数对stringRedisTemplate进行注入
    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }



//    方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key,Object value,Long time,TimeUnit unit){

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time, unit);

    }

//    方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

//    方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key=keyPrefix+id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(jsonStr)){
            R result = JSONUtil.toBean(jsonStr, type);
            return result;
        }
        if("".equals(jsonStr)){
            return null;
        }

        //4.不存在，那就根据id查询数据库
        R r = dbFallback.apply(id);

        //5.如果数据库中不存在，那就返回错误
        if(r==null){
            //缓存穿透：将null值也要写入redis,并设置有效期为2分钟
            stringRedisTemplate.opsForValue().set(key,"",time,unit);
            return null;
        }


//        String toJsonStr = JSONUtil.toJsonStr(r);//先将shop对象转换成对应的JSON字符串
//        stringRedisTemplate.opsForValue().set(key,toJsonStr,time,unit);
        this.set(key,r,time,unit);

        return r;

    }

    /**
     * Redis cache-aside with a distributed mutex. It prevents cache penetration
     * with short-lived null values and prevents cross-node cache breakdown.
     */
    public <R, ID> R queryWithMutex(String keyPrefix, String lockPrefix, ID id,
                                    Class<R> type, Function<ID, R> dbFallback,
                                    long ttl, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if ("".equals(json)) {
            return null;
        }

        String lockKey = lockPrefix + id;
        boolean locked = false;
        try {
            for (int attempt = 0; attempt < 20; attempt++) {
                locked = tryLock(lockKey);
                if (locked) {
                    break;
                }
                Thread.sleep(50L);
                json = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(json)) {
                    return JSONUtil.toBean(json, type);
                }
                if ("".equals(json)) {
                    return null;
                }
            }
            if (!locked) {
                throw new IllegalStateException("缓存重建繁忙，请稍后重试");
            }

            // Double check after acquiring the lock.
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            if ("".equals(json)) {
                return null;
            }

            R value = dbFallback.apply(id);
            if (value == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            set(key, value, ttl, unit);
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("缓存查询被中断", e);
        } finally {
            if (locked) {
                unlock(lockKey);
            }
        }
    }
    /**
     * 方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
     * @param id
     * @return
     */
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix,ID id,Class<R> type,String LocKeyPrefix,Long time,TimeUnit unit,Function<ID,R> selectData){

        //1.从redis数据库中查询
        String key=keyPrefix+id;
        String Json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否在缓存中存在
        if(StrUtil.isBlank(Json)){
            //3.不存在，直接返回null
            return null;
        }

        //4.存在，判断是否逻辑过期
        //4.1 将shopJson反序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
//      RedisData redisData = JSONUtil.toBean(shopJson, new TypeReference<RedisData<Shop>>() {}, false); 也可以这样直接获得
        //4.2 获取expireTime字段值
        LocalDateTime expireTime = redisData.getExpireTime();
        Object data = redisData.getData();
        R r = JSONUtil.toBean((JSONObject) data, type);//将data转换成Shop对象

        //5.如果未过期，直接返回商铺信息--如果expireTime在当前时间之后，就代表没有过期
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        //6.如果过期了，就需要缓存重建
        //6.1获取互斥锁
        String lockKey=LocKeyPrefix+id;
        boolean isLock = tryLock(lockKey);
        //6.2判断获取互斥锁成功还是失败
        if(isLock){
            //6.3获取互斥锁成功，首先应该先再次检测redis缓存有没有过期，做doubleCheck
            String Json2 = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(Json2)){
                RedisData redisData2 = JSONUtil.toBean(Json2, RedisData.class);
                LocalDateTime expireTime2 = redisData2.getExpireTime();
                Object data2 = redisData2.getData();
                R r1 = JSONUtil.toBean((JSONObject) data2, type);
                if(expireTime2.isAfter(LocalDateTime.now())){
                    return r1;
                }
            }

            //6.4新建独立线程进行缓存重建，也就是重新设置逻辑过期时间再写入redis，并且还是返回redis中旧的店铺信息
            //使用线程池去做
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R apply = selectData.apply(id);
                    this.setWithLogicalExpire(key,apply,time,unit);

                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }

            });
        }


        //6.4获取互斥锁成功/失败的话，都要返回redis中旧的店铺信息
        return r;
    }



    private boolean tryLock(String key){
//        setIfAbsent就对应redis中的setnx命令
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    //释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
