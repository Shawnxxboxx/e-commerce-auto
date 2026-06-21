package com.auto.ecommerce.ecommerceauto.mambang.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 马帮OpenAPI配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "mambang.api")
public class MambangProperties {

    /**
     * 马帮API基础地址
     */
    private String baseUrl = "https://gwapi.mabangerp.com/api/v2";

    /**
     * 马帮分配的AppKey
     */
    private String appKey;

    /**
     * 马帮分配的AppSecret（开发者密钥）
     */
    private String appSecret;

    /**
     * API版本号，默认为1
     */
    private String version = "1";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
