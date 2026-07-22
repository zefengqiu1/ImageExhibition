package com.worker1.worker1.config;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Component
public class RestTemplateConfig {
    private final static Random random = new Random();
    @Bean
    public RestTemplate restTemplate() {
//        String filePath = "/Users/zefengqiu/Documents/webcrawler/worker11/src/main/resources/http_proxy.txt";
//        List<String> IPS = FileReader.readFile(filePath);
//        String ip = IPS.get(random.nextInt(IPS.size()));
//        String proxyHost = ip.split(":")[0];
//        String proxyPort = ip.split(":")[1];

        // 创建 HttpClient 5.x 实例，设置代理
//        HttpHost proxy = new HttpHost(proxyHost, proxyPort);

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
//        connectionManager.setMaxTotal(200);  // 最大连接数
//        connectionManager.setDefaultMaxPerRoute(20);  // 每个路由的最大连接数

//        PoolingHttpClientConnectionManager poolingConnManager = new PoolingHttpClientConnectionManager();
//        CloseableHttpClient httpClient = HttpClients.custom()
//                .setDefaultRequestConfig(RequestConfig.custom()
//                        .setSocketTimeout(5000)  // 5秒钟读取超时
//                        .setConnectTimeout(5000)  // 5秒钟连接超时
//                        .build())
//                .setConnectionManager(poolingConnManager)
//                .build();

        // 创建连接保持策略
//        ConnectionKeepAliveStrategy keepAliveStrategy =

        // 通过 HttpClientBuilder 创建 HttpClient
        CloseableHttpClient httpClient = HttpClients.custom()
//                .setProxy(proxy)  // 设置代理
                .setConnectionManager(connectionManager)
                .setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
                    @Override
                    public TimeValue getKeepAliveDuration(HttpResponse httpResponse, HttpContext httpContext) {
                        // Customize the keep-alive logic here. For example:
                        // Check for a "Keep-Alive" header from the server
                        String keepAliveHeader = httpResponse.getFirstHeader("Keep-Alive") != null
                                ? httpResponse.getFirstHeader("Keep-Alive").getValue()
                                : null;

                        if (keepAliveHeader != null) {
                            // Extract and return the keep-alive duration
//                            return Long.parseLong(keepAliveHeader);
                            return TimeValue.of(5, TimeUnit.SECONDS);
                        }

                        // Default: keep the connection alive for 5 seconds
                        return TimeValue.of(5, TimeUnit.SECONDS);
                    }
                })    // 设置连接保持策略
                .build();

        // 将 HttpClient 设置到 RestTemplate 中
//        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 设置连接超时（毫秒）
        factory.setReadTimeout(10000);    // 设置读取超时（毫秒）

        RestTemplate restTemplate = new RestTemplate(factory);
        List<HttpMessageConverter<?>> messageConverters = restTemplate.getMessageConverters();

        // Add ByteArrayHttpMessageConverter to handle binary responses
        messageConverters.add(new ByteArrayHttpMessageConverter());

        return restTemplate;
    }
}
