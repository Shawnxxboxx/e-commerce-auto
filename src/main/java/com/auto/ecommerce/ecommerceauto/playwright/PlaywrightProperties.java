package com.auto.ecommerce.ecommerceauto.playwright;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Playwright CDP 连接配置。
 * <p>
 * 通过 connectOverCDP 连接到已打开的 Chrome 浏览器（需以 --remote-debugging-port=9222 启动），
 * 从而复用现有的 Cookie/登录态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Component
@ConfigurationProperties(prefix = "playwright.cdp")
public class PlaywrightProperties {

    /** Chrome DevTools Protocol 主机地址 */
    private String host = "localhost";

    /** Chrome DevTools Protocol 端口 */
    private int port = 9222;

    /** 元素超时时间（毫秒） */
    private int timeoutMs = 30_000;

    /** 获取完整的 CDP WebSocket URL */
    public String getCdpUrl() {
        return "http://" + host + ":" + port;
    }
}
