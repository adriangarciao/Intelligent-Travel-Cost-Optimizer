package com.adriangarciao.traveloptimizer.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Base64;

@Configuration
public class SearchKeyGenerator {

    @Bean("tripSearchKeyGenerator")
    public KeyGenerator keyGenerator() {
        return new KeyGenerator() {
            private final ObjectMapper mapper = new ObjectMapper()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            @Override
            public Object generate(Object target, Method method, Object... params) {
                try {
                    String json = mapper.writeValueAsString(params[0]);
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] hash = digest.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
                } catch (JsonProcessingException | java.security.NoSuchAlgorithmException e) {
                    // fallback: use toString if serialization fails
                    return params.length > 0 ? params[0].toString() : "search";
                }
            }
        };
    }
}
