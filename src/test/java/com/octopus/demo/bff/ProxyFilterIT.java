package com.octopus.demo.bff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyFilterIT {

    private static final MockWebServer UPSTREAM = new MockWebServer();

    static {
        try {
            UPSTREAM.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("bff.upstreams.system", () -> UPSTREAM.url("/").toString().replaceAll("/$", ""));
        registry.add("bff.routes.unreachable[0]", () -> "/api/unreachable");
        registry.add("bff.upstreams.unreachable", () -> "http://localhost:1");
    }

    @AfterAll
    static void shutdown() throws IOException {
        UPSTREAM.shutdown();
    }

    @Test
    void forwardsMethodPathQueryHeadersAndBodyAndRelaysResponse() throws Exception {
        String responseBody = "{\"code\":200,\"data\":{\"token\":\"token-123\"}}";
        UPSTREAM.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        String requestBody = "{\"username\":\"admin\",\"password\":\"123456\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/login?from=it"))
                .header("Authorization", "Bearer token-abc")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(responseBody, response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));

        RecordedRequest recorded = UPSTREAM.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("POST", recorded.getMethod());
        assertEquals("/api/auth/login?from=it", recorded.getPath());
        assertEquals("Bearer token-abc", recorded.getHeader("Authorization"));
        assertTrue(recorded.getHeader("Content-Type").startsWith("application/json"));
        assertEquals("application/json", recorded.getHeader("Accept"));
        assertEquals(requestBody, recorded.getBody().readUtf8());
    }

    @Test
    void returns502WhenUpstreamUnavailable() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/unreachable"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(502, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(502, body.get("code").asInt());
    }

    @Test
    void returns404ForUnconfiguredApiPrefix() throws Exception {
        int before = UPSTREAM.getRequestCount();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/unknown"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertEquals(404, objectMapper.readTree(response.body()).get("code").asInt());
        assertEquals(before, UPSTREAM.getRequestCount());
    }

    @Test
    void healthIsServedLocally() throws Exception {
        int before = UPSTREAM.getRequestCount();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(200, objectMapper.readTree(response.body()).get("code").asInt());
        assertEquals(before, UPSTREAM.getRequestCount());
    }

    @Test
    void corsPreflightIsAllowed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/login"))
                .header("Origin", "http://localhost:13000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:13000",
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
