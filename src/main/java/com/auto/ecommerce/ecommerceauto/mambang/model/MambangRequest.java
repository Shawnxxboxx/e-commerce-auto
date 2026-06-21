package com.auto.ecommerce.ecommerceauto.mambang.model;

import java.util.Map;

/**
 * 马帮API请求对象
 */
public class MambangRequest {

    /**
     * 马帮定义的api名称，例如: get-order-list
     */
    private String api;

    /**
     * 马帮分配给应用的AppKey
     */
    private String appkey;

    /**
     * api提交的请求参数
     */
    private Map<String, Object> data;

    /**
     * 时间戳，10位数字Unix时间戳（GMT+8）
     */
    private String timestamp;

    /**
     * API调用版本号
     */
    private String version;

    public MambangRequest() {
    }

    public MambangRequest(String api, String appkey, Map<String, Object> data, String timestamp, String version) {
        this.api = api;
        this.appkey = appkey;
        this.data = data;
        this.timestamp = timestamp;
        this.version = version;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public String getAppkey() {
        return appkey;
    }

    public void setAppkey(String appkey) {
        this.appkey = appkey;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
