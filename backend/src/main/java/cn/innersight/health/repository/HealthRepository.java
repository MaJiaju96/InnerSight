package cn.innersight.health.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HealthRepository {
    private final JdbcTemplate db;
    public HealthRepository(JdbcTemplate db) { this.db = db; }
    public void ping() { db.queryForObject("SELECT 1", Integer.class); }
}
