package com.backend_IAS.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.r2dbc.url=r2dbc:postgresql://localhost:5432/context_test",
		"spring.r2dbc.username=context_test",
		"spring.r2dbc.password=context_test"
})
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
