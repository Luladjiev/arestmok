package com.luladjiev.arestmok;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class ProxyFilter implements GatewayFilter, Ordered {
  private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory;

  public ProxyFilter(
      ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory) {
    this.modifyResponseBodyGatewayFilterFactory = modifyResponseBodyGatewayFilterFactory;
  }

  @Override
  public int getOrder() {
    return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    var request = exchange.getRequest();
    var path = request.getPath().toString();
    var query = request.getQueryParams();
    var requestHeaders = request.getHeaders();
    var id = request.getId();
    var method = request.getMethod();

    log.info(String.format("[%s] New request: [%s] %s", id, method, path));
    log.info(String.format("[%s] Query: %s", id, query));
    log.info(String.format("[%s] Request Headers: %s", id, requestHeaders));

    // Custom logic to return different response if request was already recorded
    if (path.contains("/civilizations")) {
      var bytes = "{\"message\": \"Hello World\"}".getBytes(StandardCharsets.UTF_8);
      var response = exchange.getResponse();
      var buffer = response.bufferFactory().wrap(bytes);

      response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

      return response.writeWith(Flux.just(buffer));
    }

    // Mutate ServerHttpRequest to capture the request body
    var mutatedRequest =
        new ServerHttpRequestDecorator(exchange.getRequest()) {
          @Override
          @NonNull
          public Flux<DataBuffer> getBody() {
            var baos = new ByteArrayOutputStream();

            return super.getBody()
                .doOnNext(
                    dataBuffer -> {
                      try {
                        Channels.newChannel(baos)
                            .write(dataBuffer.asByteBuffer().asReadOnlyBuffer());

                        var body = baos.toString(StandardCharsets.UTF_8);

                        log.info(String.format("[%s] Request Body: %s", id, body));
                      } catch (IOException e) {
                        log.error("Error writing to stream", e);
                      } finally {
                        try {
                          baos.close();
                        } catch (IOException e) {
                          log.error("Error closing stream", e);
                        }
                      }
                    });
          }
        };

    var responseBody = new AtomicReference<>("");

    // Create a new GatewayFilter to capture the response body
    var delegate =
        modifyResponseBodyGatewayFilterFactory.apply(
            new ModifyResponseBodyGatewayFilterFactory.Config()
                .setRewriteFunction(
                    String.class,
                    String.class,
                    (serverWebExchange, body) -> {
                      var originalBody = body == null ? "" : body;

                      responseBody.set(originalBody);

                      return Mono.just(originalBody);
                    }));

    var mutatedExchange = exchange.mutate().request(mutatedRequest).build();

    return delegate
        .filter(mutatedExchange, chain)
        .then(Mono.just(mutatedExchange))
        .map(
            serverWebExchange -> {
              var response = serverWebExchange.getResponse();
              var responseHeaders = response.getHeaders();
              var statusCode = response.getRawStatusCode();

              log.info(String.format("[%s] Status Code: %s", id, statusCode));
              log.info(
                  String.format("[%s] Response Headers: %s", id, responseHeaders));
              log.info(String.format("[%s] Response Body: \n%s", id, responseBody));

              return serverWebExchange;
            })
        .then();
  }
}
