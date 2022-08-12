package com.shun.gateway.filter;

import com.shun.gateway.config.MyFilterConfiguration;
import com.shun.gateway.util.CheckTokenUtil;
import com.shun.gateway.util.CircleBloomFilter;
import com.shun.gateway.util.TokenParse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by chenwenshun on 2022/6/14
 */
@Component
@RequiredArgsConstructor
public class CheckTokenFilter implements GlobalFilter, Ordered {

    public static final String AUTHHEADER = "authorization";

    static final String BODY_401 = " {\n" +
            "  \"code\": 401,\n" +
            "  \"message\": \"Unauthorized\"\n" +
            "}";

    static final String BODY_403 = " {\n" +
            "  \"code\": 403,\n" +
            "  \"message\": \"token expired\"\n" +
            "}";

    private final CheckTokenUtil checkTokenUtil;
    private final TokenParse tokenParse;
    private final CircleBloomFilter passedCircleBloomFilter;
    private final CircleBloomFilter stopedCircleBloomFilter;

    private final MyFilterConfiguration myFilterConfiguration;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        if (request.getMethod() == HttpMethod.OPTIONS){
            return chain.filter(exchange);
        }
        //请求路径白名单 判断
        if (checkWhitePath(request.getPath().value())){
            return chain.filter(exchange);
        }

        String token = request.getHeaders().getFirst(AUTHHEADER);
        if(token == null){
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return getVoidMono(response, request, BODY_401);
        }

        if (stopedCircleBloomFilter.exists(token)){
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return getVoidMono(response, request, BODY_401);
        }
        Claims claims = null;
        if (passedCircleBloomFilter.exists(token)){
            claims = tokenParse.parseToken(token);
           setHeaders(claims, request.mutate());
        }else {
            try {
                claims = checkTokenUtil.check(token);
                passedCircleBloomFilter.put(token);
                setHeaders(claims, request.mutate());
            } catch (ExpiredJwtException e) {
                stopedCircleBloomFilter.put(token);
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return getVoidMono(response, request, BODY_403);
            } catch (Exception e){
                stopedCircleBloomFilter.put(token);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return getVoidMono(response, request, BODY_401);
            }
        }
        return chain.filter(exchange);
    }

    private Mono<Void> getVoidMono(ServerHttpResponse serverHttpResponse, ServerHttpRequest httpRequest, String body) {
        HttpHeaders headers = serverHttpResponse.getHeaders();
        headers.add("Content-Type", "application/json;charset=UTF-8");
        
        DataBuffer dataBuffer = serverHttpResponse.bufferFactory().wrap(body.getBytes());
        return serverHttpResponse.writeWith(Flux.just(dataBuffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private AntPathMatcher pathMatcher = new AntPathMatcher();
    private boolean checkWhitePath(String reqPath){

        for (String white : myFilterConfiguration.getWhiteList()) {
            if (pathMatcher.match(white, reqPath)) {
                return true;
            }
        }

        return false;
    }

    private void setHeaders(Claims claims, ServerHttpRequest.Builder builder){
        builder.header(CheckTokenUtil.USER_ID_KEY, claims.get(CheckTokenUtil.USER_ID_KEY).toString());
        try {
            builder.header("uname", URLEncoder.encode(claims.getSubject(), "utf-8"));
        } catch (UnsupportedEncodingException e) {
            builder.header("uname", claims.getSubject());
        }
        builder.header(Claims.AUDIENCE, claims.getAudience());
    }

    public static void main(String[] args) {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        System.out.println(pathMatcher.match("/*", "/testing"));
        System.out.println(pathMatcher.match("/*/**", "/testing/testing"));
        System.out.println(pathMatcher.match("/**", "/testing/testing/aa"));
    }
}
