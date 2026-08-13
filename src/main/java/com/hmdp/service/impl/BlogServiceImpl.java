package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
//import com.sun.org.apache.regexp.internal.RE;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.websocket.server.PathParam;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IFollowService followService;

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发布笔记并且将这个笔记推送给它的粉丝
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户  这里改成UserDTO
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSave = save(blog);
        if(!isSave) {
            return Result.fail("笔记保存失败");
        }

        /**
         * 将用户发布的笔记推送给他的粉丝
         */
        //首先，先知道该用户的粉丝是谁，查询笔记作者的所有粉丝
//        List<Follow> follows = followService.query().eq("follow_user_id", blog.getUserId()).list();  //到底是谁的？user.getId()
//        答案：blog.getUserId()和user.getId()实际上指代的是同一个人，都是指的是当前登录的用户，所以用哪个都行
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : follows) {
            //获取粉丝id
            Long userId = follow.getUserId();
            //推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
//        List<Long> followIds = follows.stream().map(follow -> follow.getUserId()).collect(Collectors.toList());
//        //推送给粉丝
//
//
//
//        //查询作者的所有粉丝
//        List<Follow> followList = followService.query().eq("follow_user_id", blog.getUserId()).list();
//        //推送id给所有粉丝
//        for (Follow follow :followList) {
//            //获取粉丝id
//            Long userId = follow.getUserId();
//            String key = FEED_KEY+ userId;
//            //添加blogid到粉丝收件箱，zset
//            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
//        }

        // 返回id
        return Result.ok(blog.getId());
    }


    /**
     * 查看探店笔记
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        Long userId = UserHolder.getUser().getId();
        //首先 根据Id获取笔记
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
        //其次，显示笔记，将笔记中应该包含的User信息页显示出来
        //因为在blog里面只包含了userId，所以要使用这个userId将用户信息进行查询
        queryBlogUser(blog);
        //查询blog当前是否被点赞过
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //1.获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        //1.1如果用户未登录，就无需查询是否点赞
        if(userDTO==null){
            return;
        }
        Long id = blog.getId();
        Long userDTOId = userDTO.getId();
        //2.判断当前登录用户是否已经点过赞
        //！！！！！！！！！！因为使用的是stringRedisTemplate，所以这里一定记得使用userDTOId.toString()，不然会报错
        String key="blog:liked:"+id;
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userDTOId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, userDTOId.toString());
        if(score==null){
            blog.setIsLike(false);
        }
        else{
            blog.setIsLike(true);
        }
        //        blog.setIsLike(BooleanUtil.isTrue(isMember));
        /**
         * 上面这段代码这么写是不正确的：
         * 因为无论BooleanUtil.isFalse(isMember）这个语句正确与否，这个字段值setIsLike都会被设置为true
         */
//        if(BooleanUtil.isFalse(isMember)){
//            blog.setIsLike(false);
//        }
//            blog.setIsLike(true);

    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
//        records.forEach(blog ->{
//            queryBlogUser(blog);
//        });
        return Result.ok(records);
    }

    /**
     * 实现给笔记点赞的功能
     * 首先，一人一赞，首次点赞是点赞，再次点赞是取消
     * 其次，点赞之后传给前端已经点赞过的标志，然后高亮显示
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 修改点赞数量
//        update
        //1.获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        Long userDTOId = userDTO.getId();
        //2.判断当前登录用户是否已经点过赞
        //！！！！！！！！！！因为使用的是stringRedisTemplate，所以这里一定记得使用userDTOId.toString()，不然会报错
        String key="blog:liked:"+id;
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userDTOId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, userDTOId.toString());
        //3.如果未点赞，可以点赞
        if(score==null){
            //3.1数据库点赞数+1;
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2保存用户到Redis的Set集合 zadd key value score
            if(isSuccess){
//                stringRedisTemplate.opsForSet().add(key,userDTOId.toString());
                stringRedisTemplate.opsForZSet().add(key,userDTOId.toString(),System.currentTimeMillis());
            }
        }else{
            //4.如果已经点赞了，就取消点赞，并且改变isLike属性
            //4.1数据库点赞数-1;
            boolean isUpdate = update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2删除用户到Redis的ZSet集合
            if(isUpdate) {
                stringRedisTemplate.opsForZSet().remove(key, userDTOId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询top5的点赞用户 zrange key 0 4
        String key="blog:liked:"+id;
        Set<String> idRange5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(idRange5==null||idRange5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //2.解析其中的用户id
        List<Long> top5 = idRange5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.根据id去查询用户SELECT id,phone,password,nick_name,icon,create_time,update_time FROM tb_user WHERE id IN ( 5 , 1 ) order by field(id,5,1)
//        List<User> users = userService.listByIds(top5);
        String join = StrUtil.join(",", top5);
        List<User> users = userService.query()
                .in("id",top5)
                .last("order by field(id,"+join+")").list();
        //4.返回
        List<UserDTO> userDTOS = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogByUserId(Long userId, Integer current) {
        Page<Blog> page = query().eq("user_id", userId).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 基于滚动分页实现查询关注推送
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户
        UserDTO user = UserHolder.getUser();
        if(user==null){
            return Result.ok(emptyScrollResult());
        }
        Long userId = user.getId();

        //2.查询收件箱  ZREVRANGEBYSCORE key max min withscores limit offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key,0,max,offset,2);
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok(emptyScrollResult());
        }

        //3.解析数据:blogId，score(时间戳)、offset
        long minTime=0; //这里面为什么不去做比较求出来哪一个的时间戳是最小的？因为在ZSet里面，reverseRangeByScoreWithScores是降序排序的，只要当循环一结束，走到最后一个循环的时候，minTime正好就是最小值了，无需比较，只需要覆盖即可，这种思想很重要
        List<Long> ids=new ArrayList<>(typedTuples.size());
        int os=0;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String value = typedTuple.getValue();
            Long blogId = Long.valueOf(value);//获取存进去的blogId
            ids.add(blogId);

            long time = typedTuple.getScore().longValue();//获取存到里面的时间戳
            if(time==minTime){
                os++; //如果在最后遇到了与最小值相等的数，就++;
            }else{
                //如果time和minTime不相等，那就说明这个minTime还不是最小的Time,也就是重新再进行比较，同时设置os=1;
                minTime=time;
                os=1;
            }

        }

        //4.根据blogId查询blog
//        List<Blog> blogs = listByIds(ids); //这种是用in来进行查询的，返回到前端是无序的，所以要使用自定义order by filed来查询
        String join = StrUtil.join(",", ids);
        List<Blog> blogs = query()
                .in("id", ids)
                .last("order by field(id," + join + ")").list();

        //5.同时还要查询笔记对应的用户信息和点赞信息
        for (Blog blog : blogs) {
            //5.1.其次，显示笔记，将笔记中应该包含的User信息页显示出来
            //因为在blog里面只包含了userId，所以要使用这个userId将用户信息进行查询
            queryBlogUser(blog);
            //5.2.查询blog当前是否被点赞过
            isBlogLiked(blog);
        }

        //6.封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);

        return Result.ok(scrollResult);
    }

    private ScrollResult emptyScrollResult() {
        ScrollResult result = new ScrollResult();
        result.setList(Collections.emptyList());
        result.setMinTime(0L);
        result.setOffset(0);
        return result;
    }
}
