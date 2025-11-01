package com.example.demo.controller;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SafeController {
  private final JdbcTemplate jdbc;

  public SafeController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/safe/search")
  public List<Map<String, Object>> search(@RequestParam String q) {
    String sql = "SELECT id, username FROM users WHERE username LIKE ?";
    return jdbc.queryForList(sql, new Object[] { "%" + q + "%" });
  }

  @GetMapping("/safe/reflect")
  public String reflect(@RequestParam String input) {
    return org.springframework.web.util.HtmlUtils.htmlEscape(input);
  }

  @GetMapping("/safe/echo")
  public String echo(@RequestParam String msg) {
    if (msg == null) return "";
    return java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
  }
}
