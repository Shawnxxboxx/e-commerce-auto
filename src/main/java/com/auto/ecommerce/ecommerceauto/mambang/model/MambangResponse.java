package com.auto.ecommerce.ecommerceauto.mambang.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 马帮API响应对象
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MambangResponse {

    /**
     * 请求成功（code为空表示成功）
     */
    @JsonProperty("code")
    private String code;

    /**
     * 请求失败返回的错误信息
     */
    @JsonProperty("message")
    private String message;

    /**
     * API返回的具体数据（不同接口data结构不同）
     */
    @JsonProperty("data")
    private Object data;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    /**
     * 判断请求是否成功
     */
    public boolean isSuccess() {
        return code == null || code.isEmpty();
    }
}
