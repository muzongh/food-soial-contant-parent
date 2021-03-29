package com.mzh.restaurants;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@MapperScan("com.mzh.restaurants.mapper")
@EnableDiscoveryClient
@SpringBootApplication
public class RestaurantsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantsApplication.class, args);
    }

}
