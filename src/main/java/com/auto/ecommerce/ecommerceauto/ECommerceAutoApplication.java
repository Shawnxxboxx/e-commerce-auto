package com.auto.ecommerce.ecommerceauto;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@MapperScan("com.auto.ecommerce.ecommerceauto.**.mapper")
@EnableAsync
@SpringBootApplication
public class ECommerceAutoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceAutoApplication.class, args);
    }

}
