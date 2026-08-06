package com.bruce.docai;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires external PostgreSQL/pgvector and runtime services that are not available in unit-test builds.")
class DocaiApplicationTests {

	@Test
	void contextLoads() {
	}

}
