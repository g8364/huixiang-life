package com.hmdp.aspect;

import com.hmdp.annotation.RateLimit;
import com.hmdp.dto.UserDTO;
import com.hmdp.enums.RateLimitDimension;
import com.hmdp.exception.RateLimitException;
import com.hmdp.utils.UserHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Aspect
@Component
public class RateLimitAspect {
    private static final DefaultRedisScript<Long> SCRIPT;
    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("rate_limit.lua"));
        SCRIPT.setResultType(Long.class);
    }
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private HttpServletRequest request;

    @Around("@annotation(rateLimit)")
    public Object limit(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        if (rateLimit.count() <= 0 || rateLimit.windowSeconds() <= 0) {
            throw new IllegalArgumentException("限流次数和窗口必须大于 0");
        }
        long now = System.currentTimeMillis();
        Long allowed = stringRedisTemplate.execute(SCRIPT, buildKeys(point, rateLimit),
                String.valueOf(now), String.valueOf(rateLimit.windowSeconds() * 1000L),
                String.valueOf(rateLimit.count()), now + ":" + UUID.randomUUID());
        if (allowed == null) throw new IllegalStateException("限流服务暂时不可用");
        if (allowed == 0L) throw new RateLimitException(rateLimit.message());
        return point.proceed();
    }

    private List<String> buildKeys(ProceedingJoinPoint point, RateLimit rateLimit) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        String base = StringUtils.hasText(rateLimit.key()) ? rateLimit.key()
                : method.getDeclaringClass().getName() + ":" + method.getName();
        String prefix = "rate:limit:" + base;
        List<String> keys = new ArrayList<>();
        for (RateLimitDimension dimension : rateLimit.dimensions()) {
            switch (dimension) {
                case GLOBAL:
                    keys.add(prefix + ":global");
                    break;
                case METHOD:
                    keys.add(prefix + ":method");
                    break;
                case USER:
                    UserDTO user = UserHolder.getUser();
                    keys.add(prefix + ":user:" + (user == null ? "anonymous" : user.getId()));
                    break;
                case IP:
                    keys.add(prefix + ":ip:" + clientIp());
                    break;
                default:
                    throw new IllegalArgumentException("不支持的限流维度：" + dimension);
            }
        }
        if (keys.isEmpty()) {
            keys.add(prefix + ":method");
        }
        return keys;
    }

    private String clientIp() {
        String value = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(value)) return value.split(",")[0].trim();
        value = request.getHeader("X-Real-IP");
        return StringUtils.hasText(value) ? value.trim() : request.getRemoteAddr();
    }
}
