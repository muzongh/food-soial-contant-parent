package com.mzh.seckill.redisLockDemo.demo01;


import cn.hutool.core.lang.UUID;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisLockTest {

    private int count =0 ;
    private String lockKey="lock";

    private void call(Jedis jedis){

        //加锁
        boolean locked = RedisLock.tryLock(jedis, lockKey, UUID.randomUUID().toString(), 60);

        try {

            if (locked){
                for (int i = 0; i < 500; i++) {
                     count++;
                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            RedisLock.unlock(jedis,lockKey);
        }

    }


    public static void main(String[] args) {

        RedisLockTest redisLockTest = new RedisLockTest();
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMinIdle(1);
        jedisPoolConfig.setMaxTotal(5);
        JedisPool jedisPool = new JedisPool(jedisPoolConfig, "192.168.131.1",
                6379, 1000, "123456");
        Thread thread1 = new Thread(() -> {
            redisLockTest.call(jedisPool.getResource());
        });
        Thread thread2 = new Thread(() -> {
            redisLockTest.call(jedisPool.getResource());
        });
        thread1.start();
        thread2.start();
        thread1.run();
        thread2.run();
        System.out.println(redisLockTest.count);
    }


}
