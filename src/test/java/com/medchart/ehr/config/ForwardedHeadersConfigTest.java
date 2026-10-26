package com.medchart.ehr.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForwardedHeadersConfigTest {

    @Test
    void forwardedHeadersAreResolvedByTheContainer() throws IOException {
        ServerProperties server = bindServerProperties(Collections.emptyMap());

        assertEquals(ServerProperties.ForwardHeadersStrategy.NATIVE, server.getForwardHeadersStrategy());
    }

    @Test
    void noProxyIsTrustedByDefault() throws IOException {
        ServerProperties server = bindServerProperties(Collections.emptyMap());

        // Tomcat's RemoteIpValve treats an empty internal-proxies value as "trust nothing".
        assertEquals("", server.getTomcat().getRemoteip().getInternalProxies());
    }

    @Test
    void trustedProxiesCanBeOverriddenPerDeployment() throws IOException {
        Pattern trusted = internalProxies(bindServerProperties(
                Collections.singletonMap("TRUSTED_PROXIES", "10\\.0\\.5\\.\\d+")));

        assertTrue(trusted.matcher("10.0.5.17").matches());
        assertFalse(trusted.matcher("127.0.0.1").matches());
        assertFalse(trusted.matcher("10.20.30.40").matches());
        assertFalse(trusted.matcher("0:0:0:0:0:0:0:1").matches());
    }

    private static Pattern internalProxies(ServerProperties server) {
        return Pattern.compile(server.getTomcat().getRemoteip().getInternalProxies());
    }

    private static ServerProperties bindServerProperties(Map<String, Object> env) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", env));
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        yaml.forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment).bind("server", ServerProperties.class).get();
    }
}
