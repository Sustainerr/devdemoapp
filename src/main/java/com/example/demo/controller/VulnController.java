package com.example.demo.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VulnController {
  private final JdbcTemplate jdbc;

  public VulnController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/vuln/search")
  public List<Map<String, Object>> vulnerableSearch(@RequestParam String q) {
    String sql = "SELECT id, username FROM users WHERE username LIKE '%" + q + "%'";
    return jdbc.queryForList(sql);
  }

  @GetMapping("/vuln/reflect")
  public String vulnerableReflect(@RequestParam String input) {
    return "<html><body>User said: " + input + "</body></html>";
  }

  @GetMapping("/vuln/cmd")
  public String cmdExec(@RequestParam String cmd) throws Exception {
    Process p = Runtime.getRuntime().exec(cmd);
    BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = br.readLine()) != null) sb.append(line).append("\n");
    return sb.toString();
  }
}
