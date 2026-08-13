package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

//组合优先于继承
//这个RedisData相当于一个装饰者，里面的data就是被装饰者
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
//Resource是Java的注解，根据名称注入。autowired是spring的注解根据类型注入。