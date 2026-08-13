package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class RedisIdWorker {

    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAP=1640995200L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BTTS=32;

    /**
     * 这里使用Resource和构造函数注入都是可以的
     * 你待会要使用这个类,用它的构造函数生成对象的时候,会传递个redisTemplate,而传递进来的是bean容器中存在的,所以这里不需要注解注入,待会使用的时候通过你使用的类中这个对象
     * 这里讲的应该是序列号会一直增而不会随着时间戳变化而刷新回1的意思吧，所有的业务生成id次数超过也是可能的
     */
    @Resource
    private StringRedisTemplate stringRedisTemplatel;
//    public RedisIdWorker(StringRedisTemplate stringRedisTemplate){
//        this.stringRedisTemplatel=stringRedisTemplate;
//    }

    public long nextId(String keyPrefix){

        //1.生成时间戳
        //1.1 首先定义一个初始时间，然后根据当前时间减去初始时间的差值作为时间戳
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timeStap = nowSecond - BEGIN_TIMESTAP;


        //2.生成序列号
        //2.1获取当前日期，精确到天
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));//分层级
        //2.2自增长
        //当这个key不存在的时候，inc操作会自动创建这个key并自动加1
        long count = stringRedisTemplatel.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3.拼接并返回
        //以数字的形式去做拼接，数字的高位是时间戳，低位是序列号，而且不是以字符串的形式做拼接--位运算
        return timeStap << COUNT_BTTS | count;
    }

    public static void main(String[] args) {
        LocalDateTime begin = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = begin.toEpochSecond(ZoneOffset.UTC);//UTC时区
        System.out.println(second);

    }


}
