package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
//import jdk.internal.icu.lang.UCharacterDirection;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
//import org.graalvm.compiler.lir.LIRInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.ReactiveStringCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private UserMapper userMapper;

    //注入Redis操作的API
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IBlogService blogService;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 首先校验手机号是否符合规范
        if(RegexUtils.isPhoneInvalid(phone)){
            //isPhoneInvalid(phone)这个方法校验的：手机号是不是不符合格式
            //如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        //2. 如果符合，生成验证码
        //使用随机数生成来生成6位数的验证码
        String code = RandomUtil.randomNumbers(6);

        //3.保存验证码到session
//        session.setAttribute("code",code);

        //3.保存验证码到Redis,以手机号为key 并对验证码设置有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //4.发送验证码
        //验证码的发送需要使用第三方平台，可以之后使用阿里的接口发送
        log.debug("发送验证码成功，验证码：{}", code);
        //5. 返回成功
        return Result.ok();
    }

    //上面的发送验证码请求和这里的登录请求是两个独立的请求，每次请求都应该进行校验
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号和验证码
        String phone = loginForm.getPhone();
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        //2. 不一致，则报错
        if(phoneInvalid){
            return Result.fail("手机号格式错误，请重新输入");
        }

        //使用session时候对验证码进行验证
//        Object cashcode = session.getAttribute("code");
//        String code = loginForm.getCode();
//        if(cashcode==null || !cashcode.toString().equals(code)){
//            return Result.fail("验证码错误，请重新获取");
//        }

        //改成使用redis的时候对验证码进行验证
        String cashcode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cashcode==null || !cashcode.equals(code)){
            return Result.fail("验证码错误，请重新获取");
        }

        //3. 一致，则根据手机号查询用户--使用Mybatis-Plus
        //select * from tb_user where phone=?  query()=select * from tb_user
        User user = query().eq("phone", phone).one();

        //4. 判断用户是否存在在数据库里
        if(user==null){
            //5. 如果不存在，那么创建用户并保存到数据库中（使用mybatis-plus）
            user=createUserWithPhone(phone);
        }

//        //6. 如果存在，保存用户信息到session中
////        session.setAttribute("user",user);
//        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));

//        6. 如果存在，保存用户信息到Redis中
        //6.1.首先生成一个token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //6.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        问题就是StringRedisTemplate是定义一个String类型的key和value，但是再map转换成user的时候无法将string转换成long，所以需要这种方式或者自定义一个map
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));


//        (fieldName,fieldValue)——>fieldValue.toString()
        //6.3.存储用户信息到Redis,并且设置有效期，参考session有效期为30分钟,session中是只要你30分钟内没有访问我，我就会将你删掉，在30分钟内你访问我了，那么有效期继续变为新的30分钟
        //但是此次的redis不是，此处的redis是：只要你登录之后过了三十分钟，我就一定会将你删除，所以还是应该将其进行改进：改进成session那样的
        //意思就是说，只要请求就会触发拦截器，我们只需要在拦截器添加设置reids中存储的有效期即可
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //6.4.设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,CACHE_SHOP_TTL,TimeUnit.MINUTES);


        //7.并且将token返回给客户端
        return Result.ok(token);
    }

    @Override
    public Result queryUserById(Long userId) {
        User user = getById(userId);
        if(user==null){
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 实现签到接口，将当前用户当天签到信息保存到Redis中
     * @return
     */
    @Override
    public Result sign() {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.得到key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key="sign:"+userId+keySuffix;

        //4.获取今天是本月的第几天
        int dayOfMonth=now.getDayOfMonth();
        //5.写入Redis,setbit key offset value
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1, true);
        return Result.ok();
    }
    /**
     * 实现签到统计功能,统计当前用户截至当前时间在本月的连续签到天数
     */
    @Override
    public Result signCount() {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.得到key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key="sign:"+userId+keySuffix;

        //4.获取今天是本月的第几天
        int dayOfMonth=now.getDayOfMonth();

        //5.获取数据--统计当前用户截至当前时间在本月的连续签到记录，返回的是一个十进制数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().
                get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0));
        //这里的结果为什么是集合，是因为：bitField这个命令同时可以做get set等命令，可以得到多个结果
        if(result==null||result.isEmpty()){
            return Result.ok(0);
        }
        //6.将十进制数字转化成二进制数组返回，循环遍历
        Long num = result.get(0);//得到这个十进制数字
        if(num==0||num==null){
            return Result.ok(0);
        }
        int count=0;
        while(true){
            //6.1.让这个数字与1做与运算，得到数字的最后一个bit位
            //6.2 判断这个bit位是否为0
            if ((num&1)==0) {
                //如果为0，说明未签到，结束
                break;
            }else
            {
                //如果为1，说明已签到，计数器+1
                count++;

            }
            //将数字右移一位，抛弃最后一个bit位
//            num>>>=1; 这句代码和下面那个代码是一样的
            num = num >> 1;
        }

        return Result.ok(count);
    }


    private User createUserWithPhone(String phone) {
        //创建用户
        User user=new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //保存用户--mybatis-plus提供的操作
        save(user);
        return user;
    }
}
