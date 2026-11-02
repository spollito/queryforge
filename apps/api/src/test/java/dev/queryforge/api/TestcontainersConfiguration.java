package dev.queryforge.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs tests against a disposable PostgreSQL container. Keep the image in step
 * with the one in compose.yaml so tests exercise the same major version.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18-alpine");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGRES_IMAGE);
	}

}
