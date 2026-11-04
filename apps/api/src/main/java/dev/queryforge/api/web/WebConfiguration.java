package dev.queryforge.api.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfiguration implements WebMvcConfigurer {

	private final CorsProperties cors;

	WebConfiguration(CorsProperties cors) {
		this.cors = cors;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
			.allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
			.allowedMethods("GET", "POST");
	}

}
