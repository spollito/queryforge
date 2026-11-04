package dev.queryforge.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * A target database built from the same init scripts as the sample-db Compose
 * service, at a small scale (10,000 orders) so it starts in seconds. The API
 * connects as the read-only role, as it does in development.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SampleDatabaseConfiguration {

	static final String READER_PASSWORD = "reader-test-password";

	@Bean
	PostgreSQLContainer sampleDatabase() {
		return new PostgreSQLContainer(TestcontainersConfiguration.POSTGRES_IMAGE).withDatabaseName("sample")
			.withUsername("sample_owner")
			.withPassword("owner-test-password")
			.withEnv("SAMPLE_READER_PASSWORD", READER_PASSWORD)
			.withEnv("SAMPLE_SCALE", "0.02")
			// Maven runs tests from apps/api.
			.withCopyFileToContainer(MountableFile.forHostPath("../../infrastructure/sample-db"),
					"/docker-entrypoint-initdb.d");
	}

	@Bean
	DynamicPropertyRegistrar sampleDatabaseProperties(PostgreSQLContainer sampleDatabase) {
		return (registry) -> {
			registry.add("queryforge.target.url", sampleDatabase::getJdbcUrl);
			registry.add("queryforge.target.username", () -> "queryforge_reader");
			registry.add("queryforge.target.password", () -> READER_PASSWORD);
		};
	}

}
