package com.medchart.ehr.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.embedded.TomcatWebServerFactoryCustomizer;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots embedded Tomcat with the server.* settings from application.yml and checks what
 * request.getRemoteAddr() (the value AuditAspect stores) returns for a spoofed X-Forwarded-For.
 */
class ForwardedHeadersConfigTest {

    private WebServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void spoofedXForwardedForIsIgnoredByDefault() throws Exception {
        start(Map.of());

        assertThat(remoteAddrSeenByApp("1.2.3.4")).isEqualTo("127.0.0.1");
    }

    @Test
    void xForwardedForIsHonouredOnlyFromConfiguredTrustedProxy() throws Exception {
        start(Map.of("TRUSTED_PROXIES", "127\\.0\\.0\\.1"));

        assertThat(remoteAddrSeenByApp("1.2.3.4")).isEqualTo("1.2.3.4");
    }

    @Test
    void untrustedProxyRangeDoesNotUnlockHeader() throws Exception {
        start(Map.of("TRUSTED_PROXIES", "10\\.0\\.0\\.\\d+"));

        assertThat(remoteAddrSeenByApp("1.2.3.4")).isEqualTo("127.0.0.1");
    }

    private void start(Map<String, Object> env) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", env));
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        ServerProperties serverProperties = Binder.get(environment)
                .bind("server", ServerProperties.class)
                .orElseGet(ServerProperties::new);

        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        new TomcatWebServerFactoryCustomizer(environment, serverProperties).customize(factory);
        factory.addInitializers(ctx -> ctx.addServlet("remote", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.getWriter().write(req.getRemoteAddr());
            }
        }).addMapping("/remote"));
        server = factory.getWebServer();
        server.start();
    }

    private String remoteAddrSeenByApp(String xForwardedFor) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + server.getPort() + "/remote").openConnection();
        conn.setRequestProperty("X-Forwarded-For", xForwardedFor);
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
