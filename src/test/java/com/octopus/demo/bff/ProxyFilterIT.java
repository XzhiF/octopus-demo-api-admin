package com.octopus.demo.bff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyFilterIT {

    private static final List<Capture> SYSTEM_REQUESTS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Capture> USER_REQUESTS = Collections.synchronizedList(new ArrayList<>());

    private static final HttpServer SYSTEM_STUB = startStub("system", SYSTEM_REQUESTS);
    private static final HttpServer USER_STUB = startStub("user", USER_REQUESTS);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("bff.upstreams.system", () -> stubUrl(SYSTEM_STUB));
        registry.add("bff.upstreams.user", () -> stubUrl(USER_STUB));
        registry.add("bff.routes.unreachable[0]", () -> "/api/unreachable");
        registry.add("bff.upstreams.unreachable", () -> "http://localhost:1");
    }

    @BeforeEach
    void resetCaptures() {
        SYSTEM_REQUESTS.clear();
        USER_REQUESTS.clear();
    }

    @AfterAll
    static void shutdown() {
        SYSTEM_STUB.stop(0);
        USER_STUB.stop(0);
    }

    @Test
    void authLoginIsProxiedToSystemUpstream() throws Exception {
        String requestBody = "{\"username\":\"admin\",\"password\":\"123456\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/login?from=it"))
                .header("Authorization", "Bearer token-abc")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("system", body.get("upstream").asText());
        assertEquals("POST", body.get("method").asText());
        assertEquals("/api/auth/login?from=it", body.get("path").asText());
        assertEquals("Bearer token-abc", body.get("authorization").asText());
        assertEquals(requestBody, body.get("body").asText());

        Capture captured = awaitCapture(SYSTEM_REQUESTS);
        assertEquals("POST", captured.method());
        assertEquals("/api/auth/login?from=it", captured.path());
        assertTrue(USER_REQUESTS.isEmpty());
    }

    @Test
    void usersPathIsProxiedToSystemUpstream() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/users")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("system", objectMapper.readTree(response.body()).get("upstream").asText());

        Capture captured = awaitCapture(SYSTEM_REQUESTS);
        assertEquals("GET", captured.method());
        assertEquals("/api/users", captured.path());
        assertTrue(USER_REQUESTS.isEmpty());
    }

    @Test
    void memberPathIsProxiedToUserUpstream() throws Exception {
        String requestBody = "{\"username\":\"member_abc\",\"password\":\"Passw0rd!123\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/member/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("user", body.get("upstream").asText());
        assertEquals("/api/member/auth/login", body.get("path").asText());
        assertEquals(requestBody, body.get("body").asText());

        Capture captured = awaitCapture(USER_REQUESTS);
        assertEquals("POST", captured.method());
        assertEquals("/api/member/auth/login", captured.path());
        assertTrue(SYSTEM_REQUESTS.isEmpty());
    }

    @Test
    void relaysUpstreamStatusAndBody() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/member/fail")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(503, response.statusCode());
        assertEquals("user", objectMapper.readTree(response.body()).get("upstream").asText());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }

    @Test
    void memberPrefixDoesNotMatchMembersPrefix() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/members")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertEquals(404, objectMapper.readTree(response.body()).get("code").asInt());
        assertTrue(SYSTEM_REQUESTS.isEmpty());
        assertTrue(USER_REQUESTS.isEmpty());
    }

    @Test
    void returns502WhenUpstreamUnavailable() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/unreachable")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(502, response.statusCode());
        assertEquals(502, objectMapper.readTree(response.body()).get("code").asInt());
    }

    @Test
    void returns404ForUnconfiguredApiPrefix() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/unknown")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertEquals(404, objectMapper.readTree(response.body()).get("code").asInt());
        assertTrue(SYSTEM_REQUESTS.isEmpty());
        assertTrue(USER_REQUESTS.isEmpty());
    }

    @Test
    void healthIsServedLocally() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(200, objectMapper.readTree(response.body()).get("code").asInt());
        assertTrue(SYSTEM_REQUESTS.isEmpty());
        assertTrue(USER_REQUESTS.isEmpty());
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

    private record Capture(String method, String path, String body) {
    }

    private static HttpServer startStub(String name, List<Capture> sink) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> handle(exchange, name, sink));
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void handle(HttpExchange exchange, String name, List<Capture> sink) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().toString();
        sink.add(new Capture(exchange.getRequestMethod(), path, body));

        int status = path.endsWith("/fail") ? 503 : 200;
        byte[] response = ("{\"upstream\":\"" + name + "\",\"method\":\"" + exchange.getRequestMethod()
                + "\",\"path\":\"" + path + "\",\"authorization\":\""
                + String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"))
                + "\",\"body\":\"" + body.replace("\"", "\\\"") + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    private static String stubUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static Capture awaitCapture(List<Capture> captures) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (!captures.isEmpty()) {
                return captures.get(0);
            }
            Thread.sleep(10);
        }
        throw new AssertionError("upstream stub received no request");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
