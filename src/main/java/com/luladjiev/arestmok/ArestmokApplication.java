package com.luladjiev.arestmok;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class ArestmokApplication {
  private final ProxyFilter proxyFilter;

  public ArestmokApplication(ProxyFilter proxyFilter) {
    this.proxyFilter = proxyFilter;
  }

  public static void main(String[] args) {
    SpringApplication.run(ArestmokApplication.class, args);
  }

  @Bean
  public RouteLocator gateway(RouteLocatorBuilder builder) {
    return builder
        .routes()
        .route(
            "age-of-empires",
            predicateSpec ->
                predicateSpec
                    .path("/proxy/**")
                    .filters(
                        gatewayFilterSpec ->
                            gatewayFilterSpec
                                .rewritePath("/proxy/(?<path>.*)", "/${path}")
                                .filter(proxyFilter))
                    .uri("https://age-of-empires-2-api.herokuapp.com"))
        .route(
            "httpbin",
            predicateSpec ->
                predicateSpec
                    .path("/proxy2/**")
                    .filters(
                        gatewayFilterSpec ->
                            gatewayFilterSpec
                                .rewritePath("/proxy2/(?<path>.*)", "/${path}")
                                .filter(proxyFilter))
                    .uri("https://httpbin.org"))
        .build();
  }
}
