package com.octopus.demo.bff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.octopus.demo.common.bean.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProxyFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";
    private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final BffProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ProxyFilter(BffProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(API_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        Route route = resolve(path);
        if (route == null) {
            writeJson(response, HttpServletResponse.SC_NOT_FOUND, R.fail(404, "not found"));
            return;
        }

        try {
            forward(request, response, route);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            writeJson(response, HttpServletResponse.SC_BAD_GATEWAY, R.fail(502, "upstream unavailable"));
        }
    }

    private Route resolve(String path) {
        String bestPrefix = null;
        String bestGroup = null;
        for (Map.Entry<String, List<String>> entry : properties.getRoutes().entrySet()) {
            List<String> prefixes = entry.getValue();
            if (prefixes == null) {
                continue;
            }
            for (String prefix : prefixes) {
                if (matches(path, prefix) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                    bestPrefix = prefix;
                    bestGroup = entry.getKey();
                }
            }
        }
        if (bestGroup == null) {
            return null;
        }
        String upstream = properties.getUpstreams().get(bestGroup);
        if (upstream == null || upstream.isBlank()) {
            return null;
        }
        return new Route(trimTrailingSlash(upstream));
    }

    private boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private void forward(HttpServletRequest request, HttpServletResponse response, Route route)
            throws IOException, InterruptedException {
        byte[] requestBody = request.getInputStream().readAllBytes();

        String targetUrl = route.baseUrl() + request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            targetUrl = targetUrl + "?" + query;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30));

        HttpRequest.BodyPublisher publisher = METHODS_WITH_BODY.contains(request.getMethod()) && requestBody.length > 0
                ? HttpRequest.BodyPublishers.ofByteArray(requestBody)
                : HttpRequest.BodyPublishers.noBody();
        builder.method(request.getMethod(), publisher);

        copyHeader(request, builder, HttpHeaders.AUTHORIZATION);
        copyHeader(request, builder, HttpHeaders.CONTENT_TYPE);
        copyHeader(request, builder, HttpHeaders.ACCEPT);
        forwardClientAddress(request, builder);

        HttpResponse<byte[]> upstreamResponse = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

        response.setStatus(upstreamResponse.statusCode());
        upstreamResponse.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                .ifPresent(contentType -> response.setHeader(HttpHeaders.CONTENT_TYPE, contentType));
        response.getOutputStream().write(upstreamResponse.body());
    }

    private void copyHeader(HttpServletRequest request, HttpRequest.Builder builder, String name) {
        String value = request.getHeader(name);
        if (value != null) {
            builder.header(name, value);
        }
    }

    /** Preserves the client-provided chain and appends the direct peer address. */
    private void forwardClientAddress(HttpServletRequest request, HttpRequest.Builder builder) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String remoteAddr = request.getRemoteAddr();
        String chain;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            chain = remoteAddr == null || remoteAddr.isBlank() ? forwardedFor : forwardedFor + ", " + remoteAddr;
        } else {
            chain = remoteAddr;
        }
        if (chain != null && !chain.isBlank()) {
            builder.header("X-Forwarded-For", chain);
        }
    }

    private void writeJson(HttpServletResponse response, int status, R<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record Route(String baseUrl) {
    }
}
