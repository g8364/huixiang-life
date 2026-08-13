package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest(properties = "hmdp.order.close-task-enabled=false")
class HmDianPingApplicationTests {
    @Resource
    ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void test() throws InterruptedException {

        shopService.saveShop2Redis(1L,10L);
    }

    private ExecutorService es=Executors.newFixedThreadPool(500);
    @Test
    void test2() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task=() ->{
            for (int i = 0; i < 100; i++) {
                long id=redisIdWorker.nextId("order");
                System.out.println("id"+id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time"+(begin - end));
    }

    @Test
    void loadShopData(){
        //查询店铺信息
        List<Shop> shops = shopService.list();
        //把店铺按照typeId分组，id一致的放到一个集合:以typeId为key，以它对应的集合为value,把他们转换成一个map集合返回
        Map<Long,List<Shop>> map=shops.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        //分组完成之后写入Redis
        for (Map.Entry<Long, List<Shop>> longListEntry : map.entrySet()) {
            //获取类型id
            Long typeId = longListEntry.getKey();
            //获取同类型的店铺的集合
            List<Shop> value = longListEntry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>(value.size());
            //写入redis geoadd key 经纬度坐标 member
            String key="shop:geo:"+typeId;
            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY()))
                );
            }
            stringRedisTemplate.opsForGeo().add(key,locations);


        }



    }


    /**
     * 测试UV统计
     */
    @Test
    void testHyperLog() {
        // 准备数据，表用户数据
        String[] users = new String[1000];
        // 数组角标
        int j = 0;
        for (int i = 0; i <= 1000000; i++) {
            j=i%1000;
            // 赋值
            users[j] = "user_" + i;
            // 每1000条发送一次
            if (j==999) {
                stringRedisTemplate.opsForHyperLogLog().add("hll1", users);
            }
        }
        // 统计数量
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println("size = " + size);
    }
    @Test
    void test3(){
//        String s="hjy";
//        String s1=new String("hjy");
////        String intern = s.intern();
//        System.out.println("s.intern()"+s.intern());//hjy
//        System.out.println("s1.intern()"+s1.intern());//hjy
//        System.out.println(s.intern()==s1.intern());//true
//
//
//        String s2="dglfglgfldfvlvflgdia";
//        String s3=new String("dglfglgfldfvlvflgdia");
////        String intern = s.intern();
//        System.out.println("s2.intern()"+s2.intern());//hjy
//        System.out.println("s3.intern()"+s3.intern());//hjy
//        System.out.println(s2.intern()==s3.intern());//true



        String s1 = "HelloWorld";
        String s2 = new String("HelloWorld");
        String s3 = "Hello";
        String s4 = "World";
        String s5 = "Hello" + "World";
        String s6 = s3 + s4;

        System.out.println("s5 = " + s5);
        System.out.println("s6 = " + s6);

        System.out.println(s1 == s2); //false
        System.out.println(s1.intern() == s2.intern()); //true
        System.out.println(s1 == s5); //true
        System.out.println(s1.intern() == s5.intern()); //True
        System.out.println(s1 == s6); //False
        System.out.println(s1.intern() == s6.intern()); //True
        System.out.println(s1 == s6.intern()); //true
        System.out.println(s2 == s2.intern());//false
    }
    @Test
    void test4(){
        Integer i5 = 127;
        Integer i6 = 127;
        System.out.println(i5 == i6);//true

        Integer i5n = new Integer(127);
        Integer i6n = new Integer(127);
        System.out.println(i5n == i6n);//false

        String str1 = new String("1") ;
        System.out.println(str1.intern() == str1); //false
        System.out.println(str1 == "1");  //false

        String str3 = "abcd";
        String str2 = new String("abcd");
        String str4 = "abcd";
        System.out.println(str3==str2);//false
        System.out.println(str3==str4);//true
    }


//    final static String str1 = "cunteng";
//    final static String str2 = "008";
    @Test
    void test5(){
        String str1 = "cunteng";
        String str2 = "008";
        String str3 = "cunteng" + "008";
        String str4 = (str1 + str2);
        System.out.println(str3 == str4);//true
        /** example4 **/
        String str5 = new String("cunteng") ;
        System.out.println(str5.intern() == str5);  //false
    }
    @Test
    void test6(){
//        String str1 = "cunteng";
//        String str2 = "008";
//        String str3 = "cunteng" + "008";
//        System.out.println("str1 = " + str1);
        String str3 = "abcd";
        String str2 = new String("abcd");
        String str4 = "abcd";
        int code3 = System.identityHashCode(str3); //code3 = 436399072
        int code2 = System.identityHashCode(str2); //code2 = 93011633
        int code4 = System.identityHashCode(str4); //code4 = 436399072
        System.out.println("code2 = " + code2);
        System.out.println("code3 = " + code3);
        System.out.println("code4 = " + code4);

        System.out.println("str2==str3 = " + (str2 == str3));//false
        System.out.println("str2==str4 = " + (str2 == str4));//false
        System.out.println("str3==str4 = " + (str3 == str4));//true
        System.out.println(str2 == str3);//false
        System.out.println(str2 == str4);//false
        System.out.println(str3 == str4);//true
        System.out.println(str2.intern() == str3.intern());//true
        System.out.println(str2.intern() == str4.intern());//true
        System.out.println(str3.intern() == str4.intern());//true


        /**
         * 当输出str3.intern()、str2.intern()、str4.intern()时候，三者都是一样的
         * code2 = 1275071684
         * code3 = 1275071684
         * code4 = 1275071684
         *
         */

    }

    @Test
    void test7(){
//        String s1 = new String("abc");
//        String s2 = new String("abc");
//        System.out.println(s1 == s2); // false（两个不同对象）

//        String s1 = new String("字符串１") + new String("字符串２");
//        String s2 = s1.intern();
//        System.out.println(s1 == s2);//true

//        String s1 ="字符串1";
//        String s2 ="字符串2";
//        String s3=s1+s2;
//
//        String s5="字符串1"+"字符串2";
//        String s4="字符串1字符串2";
//        System.out.println(s3 == s4);
//        System.out.println(s3.intern() == s4.intern());
//        System.out.println(s3 == s4.intern());
//        System.out.println(s3.intern() == s4);

        /** example3 **/
        String str1 = new String("cunteng") + new String("008");
        System.out.println(str1.intern() == str1);  //true

        /** example4 **/
        String str2 = new String("cunteng") ;
        System.out.println(str2.intern() == str2);  //false


    }
}
