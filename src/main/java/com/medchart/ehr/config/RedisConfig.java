package com.medchart.ehr.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Profile("!test")
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JdkSerializationRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Patient caches — moderate TTL since patient data changes occasionally
        cacheConfigs.put("patients", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put("patientsByMrn", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put("patientSearch", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // Provider caches — longer TTL since provider data is relatively static
        cacheConfigs.put("providers", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("providersByNpi", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("providersByDepartment", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("providersBySpecialty", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("allProviders", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("activeProviders", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("departments", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("specialties", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Encounter caches — shorter TTL since encounter data changes frequently
        cacheConfigs.put("encounters", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("encountersByPatient", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("encountersByProvider", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("providerSchedule", defaultConfig.entryTtl(Duration.ofMinutes(2)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
