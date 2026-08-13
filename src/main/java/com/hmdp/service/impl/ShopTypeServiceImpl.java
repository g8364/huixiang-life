package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryAll() {
        //1.首先去redis缓存中查找--将shopList转换成String储存了--String万能
        String shopJson = stringRedisTemplate.opsForValue().get("shop:list");
//        //1.首先去redis缓存中查找--将shopList转换成List储存
//        List<String> shopJson = stringRedisTemplate.opsForList().range("shop:list", 0, -1);

//        //2.如果找的到，那就返回商铺类型
        if(StrUtil.isNotBlank(shopJson)){
            List<ShopType> shopTypeList = JSONUtil.toList(shopJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
//        //2.如果找的到，那就返回商铺类型--将shopList转换成List储存
//        if(CollectionUtil.isNotEmpty(shopJson)){
//            //将所有的jsonList转化为对象list,并排序
//            List<ShopType> shopTypeList = shopJson.stream()
//                    .map(str->JSONUtil.toBean(str, ShopType.class))
//                    .sorted(Comparator.comparingInt(ShopType::getSort))
//                    .collect(Collectors.toList());
//            return Result.ok(shopTypeList);
//        }


        //3.如果找不到，那就去数据库中查找
        List<ShopType> shopTypeList = query().orderByDesc("sort").list();
        //4.数据库中没有，便报错
        if(shopTypeList.isEmpty()){
            return Result.fail("无商铺信息");
        }
        //5.数据库中有，将商铺类型写到缓存中
        String toJsonStr = JSONUtil.toJsonStr(shopTypeList);
        stringRedisTemplate.opsForValue().set("shop:list",toJsonStr);


//        //5.数据库中有，将商铺类型写到缓存中--将shopList转换成List储存
//        shopJson= shopTypeList.stream().sorted(Comparator.comparingInt(ShopType::getSort))
//                .map(shopType -> JSONUtil.toJsonStr(shopType))
//                .collect(Collectors.toList());
//        stringRedisTemplate.opsForList().rightPushAll("shop:list", shopJson);
//

        //6.并返回结果
        return Result.ok(shopTypeList);
    }
}
