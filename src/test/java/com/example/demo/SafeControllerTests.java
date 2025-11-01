package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SafeControllerTests {

  @Autowired
  MockMvc mvc;

  @Test
  void safeSearchReturnsJsonArray() throws Exception {
    mvc.perform(get("/safe/search").param("q", "a"))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith("application/json"));
  }

  @Test
  void safeReflectEscapesHtml() throws Exception {
    mvc.perform(get("/safe/reflect").param("input", "<script>alert(1)</script>"))
      .andExpect(status().isOk())
      .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
  }
}
