package com.example.demo.repo;

import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserRepo {
  private final JdbcTemplate jdbc;

  public UserRepo(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void init() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(100))");
    jdbc.update("MERGE INTO users KEY(id) VALUES (1, 'alice')");
    jdbc.update("MERGE INTO users KEY(id) VALUES (2, 'bob')");
    jdbc.update("MERGE INTO users KEY(id) VALUES (3, 'charlie')");
  }
}
