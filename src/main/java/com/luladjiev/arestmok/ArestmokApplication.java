package com.luladjiev.arestmok;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class ArestmokApplication {
  @Value("${arestmok.url}")
  private String url;

  private final ProxyFilter proxyFilter;

  public ArestmokApplication(ProxyFilter proxyFilter) {
    this.proxyFilter = proxyFilter;
  }

  public static void main(String[] args) {
    SpringApplication.run(ArestmokApplication.class, args);
  }

  @Bean
  public RouteLocator gateway(RouteLocatorBuilder builder) {
    log.info(String.format("Proxying %s", url));

    return builder
        .routes()
        .route(
            "httpbin",
            predicateSpec ->
                predicateSpec
                    .path("/proxy/**")
                    .filters(
                        gatewayFilterSpec ->
                            gatewayFilterSpec
                                .rewritePath("/proxy2/(?<path>.*)", "/${path}")
                                .filter(proxyFilter))
                    .uri(url))
        .build();
  }
}
