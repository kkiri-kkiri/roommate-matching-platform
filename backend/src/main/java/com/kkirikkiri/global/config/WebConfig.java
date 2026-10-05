package com.kkirikkiri.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;
	private final String imageDirectory;

	public WebConfig(@Value("${app.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins,
			@Value("${app.community.image-directory:uploads/community}") String imageDirectory) {
		this.allowedOrigins = allowedOrigins;
		this.imageDirectory = imageDirectory;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = Path.of(imageDirectory).toAbsolutePath().normalize().toUri().toString();
		registry.addResourceHandler("/api/post-images/**")
				.addResourceLocations(location.endsWith("/") ? location : location + "/");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*");
	}
}
