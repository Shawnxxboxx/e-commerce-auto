package com.auto.ecommerce.ecommerceauto;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.auto.ecommerce.ecommerceauto.**.mapper")
@SpringBootApplication
public class ECommerceAutoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceAutoApplication.class, args);
    }

}
