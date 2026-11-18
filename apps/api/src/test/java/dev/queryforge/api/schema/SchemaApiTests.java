package dev.queryforge.api.schema;

import static org.assertj.core.api.Assertions.assertThat;

import dev.queryforge.api.SampleDatabaseConfiguration;
import dev.queryforge.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** End to end: reads schema metadata from a real seeded PostgreSQL via the read-only role. */
@SpringBootTest(properties = "queryforge.target.statement-timeout=2s")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, SampleDatabaseConfiguration.class })
class SchemaApiTests {

	@Autowired
	private MockMvcTester mvc;

	private MvcTestResult schema(String uri) {
		return mvc.get().uri(uri).exchange();
	}

	@Test
	void describesOrdersTableWithKeysColumnsAndNoSecondaryIndex() {
		MvcTestResult result = schema("/api/v1/schema?tables=orders");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.tables[0].name").isEqualTo("orders");
		assertThat(result).bodyJson().extractingPath("$.tables[0].primary_key[0]").isEqualTo("id");
		assertThat(result).bodyJson().extractingPath("$.tables[0].foreign_keys.customer_id").isEqualTo("customers.id");
		assertThat(result).bodyJson().extractingPath("$.tables[0].columns[0].name").isEqualTo("id");
		assertThat(result).bodyJson().extractingPath("$.tables[0].columns[0].data_type").isEqualTo("bigint");
		assertThat(result).bodyJson().extractingPath("$.tables[0].columns[0].nullable").isEqualTo(false);
		// customer_id is deliberately unindexed, so orders has no secondary index.
		assertThat(result).bodyJson().extractingPath("$.tables[0].indexes.length()").isEqualTo(0);
		// reltuples is populated because the init scripts run ANALYZE.
		assertThat(result).bodyJson().extractingPath("$.tables[0].estimated_rows").asNumber().isNotNull();
	}

	@Test
	void reportsUniqueSecondaryIndexOnCustomers() {
		MvcTestResult result = schema("/api/v1/schema?tables=customers");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.tables[0].indexes[0].name").isEqualTo("customers_email_key");
		assertThat(result).bodyJson().extractingPath("$.tables[0].indexes[0].unique").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.tables[0].indexes[0].columns[0]").isEqualTo("email");
	}

	@Test
	void listsAllBaseTablesWhenNoFilterGiven() {
		MvcTestResult result = schema("/api/v1/schema");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.tables.length()").isEqualTo(4);
	}

}
