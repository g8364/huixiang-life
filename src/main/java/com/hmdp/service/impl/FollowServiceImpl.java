package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        if(user==null){
            return Result.ok();
        }
        Long userId = user.getId();
        if(!isFollow){
//            delete from tb_follow where user_id=？ and follow_user_id=?
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if(remove){
                stringRedisTemplate.opsForSet().remove("follows:"+userId,followUserId.toString());
            }
        }else{
            Follow follow = new Follow();
            follow.setCreateTime(LocalDateTime.now());
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean save = save(follow);
            if(save){
                stringRedisTemplate.opsForSet().add("follows:"+userId,followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     *实现共同关注接口
     * @param userId
     * @return
     */
    @Override
    public Result followCommons(Long userId) {
        UserDTO user = UserHolder.getUser();
        Long userID = user.getId();

        Set<String> union = stringRedisTemplate.opsForSet().intersect("follows:"+userId, "follows:"+userID);
        if(union==null||union.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> idList = union.stream().map(Long::valueOf).collect(Collectors.toList());//共同关注的用户列表

        List<User> users = userService.listByIds(idList);
        List<UserDTO> userDTOS = users.stream().map(user1 -> BeanUtil.copyProperties(user1, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }



    @Override
    public Result isFollow(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        if(user==null){
            return Result.ok();
        }
        Long userId = user.getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        int i = count.intValue();
        return Result.ok(i>0);
//        if(i>0){
//            return Result.ok();
//        }
//        return Result.fail("");
    }


}
