package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class VulnControllerTests {

  @Autowired
  MockMvc mvc;

  @Test
  void vulnSearchSqlInjectionLeadsToMoreResults() throws Exception {
    mvc.perform(get("/vuln/search").param("q", "' OR '1'='1"))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith("application/json"))
      .andExpect(jsonPath("$.length()").value(greaterThan(1)));
  }

  @Test
  void vulnReflectEchoesRawHtml() throws Exception {
    mvc.perform(get("/vuln/reflect").param("input", "<script>alert(1)</script>"))
      .andExpect(status().isOk())
      .andExpect(content().string(containsString("<script>alert(1)</script>")));
  }
}
