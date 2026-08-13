package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX="lock:";
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    public static final DefaultRedisScript<Long> UNLOCK_SCRIRT;
    static {
        UNLOCK_SCRIRT=new DefaultRedisScript<>();
        UNLOCK_SCRIRT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIRT.setResultType(Long.class);
    }

    //构造函数注入
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        //在JVM内部，每创建一个线程，线程的ID就会递增一个数值
        //修改：给线程标识加一个前缀
        //获取线程标识
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        String key=KEY_PREFIX+name;

        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId+"", timeoutSec, TimeUnit.SECONDS);
        
        return Boolean.TRUE.equals(success);
        //因为在这里，Boolean是包装类，如果它是空的话，就有可能出现空指针异常
    }

    @Override
    public void unlock() {
//        //修改--首先判断是不是释放的自己的锁
//        //1.获取线程标识
//        String key=KEY_PREFIX+name;
//        String thread = stringRedisTemplate.opsForValue().get(key);
//        //2.判读标识是否一致
//        String threadId = ID_PREFIX+Thread.currentThread().getId();
//        if (threadId.equals(thread)) {
//            stringRedisTemplate.delete(key);
//        }

        //版本三改动：调用Lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIRT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+Thread.currentThread().getId());
    }
}
