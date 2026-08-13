package com.hmdp.service.impl;

import ch.qos.logback.core.net.server.Client;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.hmdp.dto.Result;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.CacheInvalidationPublisher;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import net.bytebuddy.matcher.CollectionOneToOneMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private Cache<Long, Shop> shopLocalCache;

    @Autowired
    private CacheInvalidationPublisher cacheInvalidationPublisher;


    @Override
    public Result queryById(Long id) {
        Shop shop = shopLocalCache.getIfPresent(id);
        if (shop == null) {
            CacheClient.LogicalCacheResult<Shop> result = cacheClient.queryWithLogicalExpireResult(
                    CACHE_SHOP_KEY, LOCK_SHOP_KEY, id, Shop.class, this::getById,
                    CACHE_SHOP_TTL, TimeUnit.MINUTES, () -> shopLocalCache.invalidate(id));
            shop = result.getValue();
            // Do not put a logically expired value into Caffeine. Requests may use
            // the stale Redis value temporarily, but the next request will observe
            // the asynchronously rebuilt value instead of caching stale data locally.
            if (shop != null && !result.isStale()) {
                shopLocalCache.put(id, shop);
            }
        }
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //7.返回结果
        return Result.ok(shop);
    }


//    //线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    /**
     * 这一段代码是逻辑过期解决缓存击穿的代码 该代码已被写到工具类CacheClient中
     * @param id
     * @return
     */
//    public Shop queryWithLogicalExpire(Long id){
//        //1.从redis数据库中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        //2.判断是否在缓存中存在
//        if(StrUtil.isBlank(shopJson)){
//            //3.不存在，直接返回null
//            return null;
//        }
//
//        //4.存在，判断是否逻辑过期
//        //4.1 将shopJson反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
////      RedisData redisData = JSONUtil.toBean(shopJson, new TypeReference<RedisData<Shop>>() {}, false); 也可以这样直接获得
//        //4.2 获取expireTime字段值
//        LocalDateTime expireTime = redisData.getExpireTime();
//        Object data = redisData.getData();
//        Shop shop = JSONUtil.toBean((JSONObject) data, Shop.class);//将data转换成Shop对象
//
//        //5.如果未过期，直接返回商铺信息--如果expireTime在当前时间之后，就代表没有过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            return shop;
//        }
//        //6.如果过期了，就需要缓存重建
//        //6.1获取互斥锁
//        String lockKey=LOCK_SHOP_KEY+id;
//        boolean isLock = tryLock(lockKey);
//        //6.2判断获取互斥锁成功还是失败
//        if(isLock){
//
//            //6.3获取互斥锁成功，首先应该先再次检测redis缓存有没有过期，做doubleCheck
//            String shopJson2 = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//            if(StrUtil.isNotBlank(shopJson2)){
//                RedisData redisData2 = JSONUtil.toBean(shopJson2, RedisData.class);
//                LocalDateTime expireTime2 = redisData2.getExpireTime();
//                Object data2 = redisData2.getData();
//                Shop shop2 = JSONUtil.toBean((JSONObject) data2, Shop.class);
//                if(expireTime2.isAfter(LocalDateTime.now())){
//                    return shop2;
//                }
//            }
//
//            //6.4新建独立线程进行缓存重建，也就是重新设置逻辑过期时间再写入redis，并且还是返回redis中旧的店铺信息
//            //使用线程池去做
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    saveShop2Redis(id,20L);
//                } catch (Exception exception) {
//                    throw new RuntimeException(exception);
//                } finally {
//                    //释放锁
//                    unlock(lockKey);
//                }
//
//            });
//        }
//
//
//        //6.4获取互斥锁成功/失败的话，都要返回redis中旧的店铺信息
//        return shop;
//    }


    /**
     * 这一段是利用互斥锁解决缓存击穿的代码
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        //1.从redis数据库中查询商铺缓存
//        Object o = stringRedisTemplate.opsForHash().get();
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //2.判断是否在缓存中存在
//        StrUtil.isNotBlank(null) // false
//        StrUtil.isNotBlank("") // false
//        StrUtil.isNotBlank("\t\n") // false
//        StrUtil.isNotBlank("abc") // true
//        isNotBlank在null和""和tn下都是不存在，为空
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回商铺信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);//将Json字符串转换成shop对象返回
            return shop;
        }
        //缓存穿透：判断获取出来的shopJson是不是空字符串，如果是空字符串，也要返回错误信息
//        NULL是缓存中没找到要去数据库里找，而""是找到了但是内容为空直接返回错误信息
//        null不等于""
//        这里使用shopJson==""和shopJson！=null判断出来的结果是不一样的，如果使用shopJson==""进行判断，依旧每次都会去数据库中查，但是使用shopJson！=null就会去缓存的数据库中查
//        而且在这里：如果使用shopJson.equals("")和shopJson==""的话，是不对的，因为：shopJson 为 null，会抛出 NullPointerException。
//          但是这个可以直接使用："".equals(shopJson) 但是使用这个判读却是可以的
//        直接用==""判断不行，因为这个比较的是引用地址，而且shopJson可能为null造成NullPointerException

        if("".equals(shopJson)){
            return null;
        }
//        shopJson ！= null ，其实就是shopJson == ""和 shopJson =="tn"
//        可以这样理解，第一个if已经保证了shopjson不为空了，所以到下面的话一定是shopjson没查到的情况，之前没查到不用判断，直接走数据库查，现在因为为空时存了个"",所以这里的条件,那么下次这个id再传过来的时候，返回值就不是null了，直接返回错误信息
//        用"".equals(shopJson)也可以 不过老师这种更优雅 因为走到这个判断时shopJson肯定为null或者为""

        //4.实现缓存重建
        //4.1 获取互斥锁
        String lockKey="lock:shop:"+id;
        Shop shop = null;
        try {
            boolean flag = tryLock(lockKey);
            //4.2 判断是否获取互斥锁成功
            if(!flag){
                //4.3 如果获取失败，那么就休眠重试
                Thread.sleep(50);
                return queryWithMutex(id);//重试
            }

            //4.4 如果获取成功，首先应该再次检测redis缓存是否存在，做DoubleCheck，如果存在就不需要去根据id查询数据库重建缓存
//        因为获取锁成功有两种情况，一种是该线程确实是第一个，也就是老师讲的这个，另一种是别的线程释放锁后该线程又拿到了锁，所以可能已经查过数据库放进redis中了
            String shopJson2 = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(StrUtil.isNotBlank(shopJson2)){
                //3.存在，直接返回商铺信息
                Shop shop2 = JSONUtil.toBean(shopJson, Shop.class);//将Json字符串转换成shop对象返回
                return shop2;
            }
            //如果redis缓存中没有的话，那就要重新去数据库查找，重建缓存
            shop = getById(id);
            //模拟重建缓存的延时
            Thread.sleep(200);
            //5.如果数据库中不存在，那就返回错误
            if(shop==null){

                //缓存穿透：将null值也要写入redis,并设置有效期为2分钟
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }

            //6.如果数据库中存在，将商铺信息写入redis,并设置有效期
            String toJsonStr = JSONUtil.toJsonStr(shop);//先将shop对象转换成对应的JSON字符串
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,toJsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }finally {
//            finally中的代码一定会执行的，除非虚拟机被关闭或发生异常退出
//            finally无论执行成功还是失败都会执行finally里面的代码继续释放锁
            //7.释放互斥锁
            unlock(lockKey);
        }



        //8.返回结果
        return shop;
    }


//    /**
//     * 这一段是缓存穿透的代码,该代码已被写到工具类CacheClient中
//     * @param id
//     * @return
//     */

//    public Shop queryWithPassThrough(Long id){
//        //1.从redis数据库中查询商铺缓存
////        Object o = stringRedisTemplate.opsForHash().get();
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        //2.判断是否在缓存中存在
////        StrUtil.isNotBlank(null) // false
////        StrUtil.isNotBlank("") // false
////        StrUtil.isNotBlank("\t\n") // false
////        StrUtil.isNotBlank("abc") // true
////        isNotBlank在null和""和tn下都是不存在，为空
//        if(StrUtil.isNotBlank(shopJson)){
//            //3.存在，直接返回商铺信息
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);//将Json字符串转换成shop对象返回
//            return shop;
//        }
//        //缓存穿透：判断获取出来的shopJson是不是空字符串，如果是空字符串，也要返回错误信息
////        NULL是缓存中没找到要去数据库里找，而""是找到了但是内容为空直接返回错误信息
////        null不等于""
////        这里使用shopJson==""和shopJson！=null判断出来的结果是不一样的，如果使用shopJson==""进行判断，依旧每次都会去数据库中查，但是使用shopJson！=null就会去缓存的数据库中查
////        而且在这里：如果使用shopJson.equals("")和shopJson==""的话，是不对的，因为：shopJson 为 null，会抛出 NullPointerException。
////          但是这个可以直接使用："".equals(shopJson) 但是使用这个判读却是可以的
////        直接用==""判断不行，因为这个比较的是引用地址，而且shopJson可能为null造成NullPointerException
//
//        if("".equals(shopJson)){
//            return null;
//        }
////        shopJson ！= null ，其实就是shopJson == ""和 shopJson =="tn"
////        可以这样理解，第一个if已经保证了shopjson不为空了，所以到下面的话一定是shopjson没查到的情况，之前没查到不用判断，直接走数据库查，现在因为为空时存了个"",所以这里的条件,那么下次这个id再传过来的时候，返回值就不是null了，直接返回错误信息
////        用"".equals(shopJson)也可以 不过老师这种更优雅 因为走到这个判断时shopJson肯定为null或者为""
//
//        //4.不存在，那就根据id查询数据库
//        Shop shop = getById(id);
//
//        //5.如果数据库中不存在，那就返回错误
//        if(shop==null){
//            //缓存穿透：将null值也要写入redis,并设置有效期为2分钟
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//
//        //6.如果数据库中存在，将商铺信息写入redis,并设置有效期
//        String toJsonStr = JSONUtil.toJsonStr(shop);//先将shop对象转换成对应的JSON字符串
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,toJsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//
//        //7.返回结果
//        return shop;
//    }

    @Override
    @Transactional  //控制原子性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }

        //1.首先更新数据库
        updateById(shop);

        //2.删除缓存
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                shopLocalCache.invalidate(id);
                try {
                    stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
                } catch (Exception ignored) {
                    // Kafka 消费者会继续尝试删除 Redis 缓存。
                }
                cacheInvalidationPublisher.publishShopInvalidation(id);
            }
        });
        return Result.ok();
    }

    /**
     * 检索附件的商铺
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.是否需要根据坐标进行查询
        if(x==null||y==null){
            // 不需要根据地理经纬度进行查询，只需要根据类型分页查询
        Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
        }

        //2.计算分页参数
        int from=(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end=current*SystemConstants.DEFAULT_PAGE_SIZE;
        //3.查询redis,按照距离排序、分页，结果：shopId,distance
        //圆心，半径，
        //搜索的是5000米以内的
        String key="shop:geo:"+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(new Point(x, y)), //圆心
                new Distance(5000), //半径
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance() //带上距离
                        .limit(end) //分页，但是这个分页返回的永远都是从0到end的，所以要自己手动截取
        );

        //4.解析id
        if(results==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if(content.size()<=from){
            //没有下一页了
            return Result.ok(Collections.emptyList());
        }
        //4.1截取from-end的部分
        List<Long> ids=new ArrayList<>(content.size());
        Map<String,Distance> distanceMap=new HashMap<>(content.size());
        //跳过前from的数据即可，就完成了从from到end
        content.stream().skip(from).forEach(res->{
            //4.2获取店铺id
            String shopIdStr = res.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //4.3获取距离
            Distance distance = res.getDistance();
            distanceMap.put(shopIdStr,distance);
        });

        //5.根据id查询shop
        String join = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(ID," + join + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        return Result.ok(shops);
    }


    /**
     * //加互斥锁 该代码已被写到工具类CacheClient中
     * @param key
     * @return
     */
//    因为这个不是基本类型的boolean，这个Boolean是boolean的包装类
//网络问题或键不存在但 Redis 未响应，setIfAbsent 可能会返回 null
    private boolean tryLock(String key){
//        setIfAbsent就对应redis中的setnx命令
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁 该代码已被写到工具类CacheClient中
     * @param key
     */
    //释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 首先通过该方法将需要的数据放到redis中去
     * @param id
     * @param expireSeconds
     * @throws InterruptedException
     */
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //首先去数据库根据id查询数据
        Shop shop = getById(id);
        //模拟缓存重建延迟
        Thread.sleep(200);
        //然后封装一个要存入redis的逻辑过期对象
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //将该对象写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }
//    如果不是热点key,可以判断redisData里面的expireTime,为null则这个key不是热点key,可以进行相应操作




}
