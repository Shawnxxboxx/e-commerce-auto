package com.auto.ecommerce.ecommerceauto.mambang.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 马帮OpenAPI签名工具类
 * <p>
 * 签名算法：
 * 1. 获取HTTP Body所有入参的字符串JSON格式
 * 2. 将JSON字符串与开发者密钥拼接，使用HMAC-SHA256哈希算法计算签名
 * 3. 对HMAC输出的二进制字符串进行十六进制编码
 * 4. 将签名值放入Authorization请求头
 */
public class MambangSignUtil {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    /**
     * 生成马帮API签名
     *
     * @param bodyJson  请求体的JSON字符串
     * @param appSecret 开发者密钥
     * @return 十六进制编码的HMAC-SHA256签名字符串
     */
    public static String generateSignature(String bodyJson, String appSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    appSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(bodyJson.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HMAC-SHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Invalid key for HMAC-SHA256", e);
        }
    }

    /**
     * 将字节数组转换为小写十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
