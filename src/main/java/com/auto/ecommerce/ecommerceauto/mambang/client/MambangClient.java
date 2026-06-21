package com.auto.ecommerce.ecommerceauto.mambang.client;

import com.auto.ecommerce.ecommerceauto.mambang.config.MambangProperties;
import com.auto.ecommerce.ecommerceauto.mambang.model.MambangRequest;
import com.auto.ecommerce.ecommerceauto.mambang.model.MambangResponse;
import com.auto.ecommerce.ecommerceauto.mambang.util.MambangSignUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 马帮OpenAPI客户端
 * <p>
 * 封装了马帮API的签名、请求发送和响应解析
 */
@Component
public class MambangClient {

    private final MambangProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MambangClient(MambangProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 通用API调用方法
     *
     * @param apiName  马帮API名称，例如 "get-order-list"
     * @param data     API请求参数
     * @return 马帮API响应
     */
    public MambangResponse call(String apiName, Map<String, Object> data) {
        // 构建请求参数
        MambangRequest request = buildRequest(apiName, data);

        // 将请求对象序列化为JSON字符串
        String bodyJson = serializeRequest(request);

        // 生成签名
        String signature = MambangSignUtil.generateSignature(bodyJson, properties.getAppSecret());

        // 发送HTTP请求
        return doPost(bodyJson, signature);
    }

    /**
     * 构建请求对象
     */
    private MambangRequest buildRequest(String apiName, Map<String, Object> data) {
        MambangRequest request = new MambangRequest();
        request.setApi(apiName);
        request.setAppkey(properties.getAppKey());
        request.setData(data);
        request.setTimestamp(String.valueOf(System.currentTimeMillis() / 1000));
        request.setVersion(properties.getVersion());
        return request;
    }

    /**
     * 将请求对象序列化为JSON字符串
     */
    private String serializeRequest(MambangRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Mambang request", e);
        }
    }

    /**
     * 发送HTTP POST请求
     */
    private MambangResponse doPost(String bodyJson, String signature) {
        try {
            String responseBody = restClient.post()
                    .header("Content-Type", "application/json")
                    .header("Authorization", signature)
                    .body(bodyJson)
                    .retrieve()
                    .body(String.class);

            return objectMapper.readValue(responseBody, MambangResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Mambang API", e);
        }
    }
}
