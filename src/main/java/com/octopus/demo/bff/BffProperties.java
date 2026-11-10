package com.octopus.demo.bff;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "bff")
public class BffProperties {

    private Map<String, List<String>> routes = new LinkedHashMap<>();
    private Map<String, String> upstreams = new LinkedHashMap<>();
    private Cors cors = new Cors();

    public Map<String, List<String>> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, List<String>> routes) {
        this.routes = routes;
    }

    public Map<String, String> getUpstreams() {
        return upstreams;
    }

    public void setUpstreams(Map<String, String> upstreams) {
        this.upstreams = upstreams;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public static class Cors {
        private String allowedOrigin = "http://localhost:13000";

        public String getAllowedOrigin() {
            return allowedOrigin;
        }

        public void setAllowedOrigin(String allowedOrigin) {
            this.allowedOrigin = allowedOrigin;
        }
    }
}
