package dev.queryforge.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QueryForgeApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(QueryForgeApiApplication.class, args);
	}

}
