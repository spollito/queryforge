package dev.queryforge.api.web;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Browser origins allowed to call the API, such as the web client's dev server. */
@ConfigurationProperties("queryforge.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
