package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class LoginInterceptor implements HandlerInterceptor{
//    private StringRedisTemplate stringRedisTemplate;
//
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    //在进入Controller之前要进行登录校验
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断是否需要做拦截(ThreadLocal中是否有用户）
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO==null){
            response.setStatus(401);
            return false;
        }


//        //1. 获取session
////        HttpSession session = request.getSession();
//        //2. 获取session中的用户
////        Object user = session.getAttribute("user");
//
//        //1. 获取请求头中的token
//        String token = request.getHeader("authorization");
//        if(StrUtil.isBlank(token)){
//            //判断获取到的token是不是为空，如果为空，就拦截
//            response.setStatus(401);
//            return false;
//        }
//        //2. 根据token获取Redis中的用户
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//
//        //3.判断用户是否存在
//        if(userMap.isEmpty()){
//            //4. 不存在，拦截，返回401状态码
//            response.setStatus(401);
//            return false;
//        }
//
//        //5.将查询到的Map对象转换成相应的UserDTO对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//
////        //4. 判断用户是否存在
////        if(user==null){
////            //5. 不存在，拦截，返回401状态码
////            response.setStatus(401);
////            return false;
////        }
//
//        //6. 存在，保存用户信息到ThreadLocal
//        UserHolder.saveUser(userDTO);
//
//        //7.刷新token有效期
//        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8. 放行
        return true;

    }

//    //在用户执行完之后进行销毁操作，避免内存泄露
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        //移除用户
//        UserHolder.removeUser();
//
//    }
}
