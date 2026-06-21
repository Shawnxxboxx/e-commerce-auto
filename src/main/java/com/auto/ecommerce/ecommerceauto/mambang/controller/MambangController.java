package com.auto.ecommerce.ecommerceauto.mambang.controller;

import com.auto.ecommerce.ecommerceauto.mambang.client.MambangClient;
import com.auto.ecommerce.ecommerceauto.mambang.model.MambangResponse;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 马帮OpenAPI演示控制器
 * <p>
 * 提供示例接口，展示如何调用马帮各种API
 */
@RestController
@RequestMapping("/api/mambang")
public class MambangController {

    private final MambangClient mambangClient;

    public MambangController(MambangClient mambangClient) {
        this.mambangClient = mambangClient;
    }

    /**
     * 获取订单列表
     * <p>
     * API名称: get-order-list
     *
     * @param status 订单状态（可选）
     * @param page   页码（可选）
     * @param size   每页条数（可选）
     */
    @GetMapping("/orders")
    public MambangResponse getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size) {

        Map<String, Object> data = new HashMap<>();
        if (status != null) {
            data.put("status", status);
        }
        data.put("page", page);
        data.put("size", size);

        return mambangClient.call("get-order-list", data);
    }

    /**
     * 获取商品列表
     * <p>
     * API名称: get-product-list
     */
    @GetMapping("/products")
    public MambangResponse getProducts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size) {

        Map<String, Object> data = new HashMap<>();
        data.put("page", page != null ? page : 1);
        data.put("size", size);

        return mambangClient.call("get-product-list", data);
    }

    /**
     * 获取物流列表
     * <p>
     * API名称: get-logistics-list
     */
    @GetMapping("/logistics")
    public MambangResponse getLogistics() {
        return mambangClient.call("get-logistics-list", new HashMap<>());
    }

    /**
     * 通用API调用接口（用于调用任意马帮API）
     * <p>
     * 请求体示例:
     * {
     *   "api": "get-order-list",
     *   "data": {
     *     "status": "1",
     *     "page": 1,
     *     "size": 50
     *   }
     * }
     *
     * @param requestBody 包含api和data字段的请求体
     * @return 马帮API响应
     */
    @PostMapping("/call")
    public MambangResponse callApi(@RequestBody Map<String, Object> requestBody) {
        String apiName = (String) requestBody.get("api");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) requestBody.getOrDefault("data", new HashMap<>());

        if (apiName == null || apiName.isEmpty()) {
            throw new IllegalArgumentException("'api' field is required");
        }

        return mambangClient.call(apiName, data);
    }
}
