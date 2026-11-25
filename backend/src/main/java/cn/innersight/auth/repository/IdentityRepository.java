package cn.innersight.auth.repository;

import cn.innersight.role.dto.RoleRefVO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
    public record Account(long id, String login, String name, boolean enabled, boolean temporary, long revision, String hash) {}
    public record Session(Account account, long started, long active) {}
    public record Throttle(String failures, long blocked) {}

    private final JdbcTemplate db;
    public IdentityRepository(JdbcTemplate db) { this.db = db; }

    public boolean lockInitialized() {
        return Boolean.TRUE.equals(db.queryForObject("SELECT initialized FROM write_guard WHERE id=1 FOR UPDATE", Boolean.class));
    }
    public long initialize(String login, String name, String passwordHash) {
        db.update("INSERT INTO access_role(code,name) VALUES ('ADMIN','内置管理员')");
        long id = insertAccount(login, name, passwordHash);
        db.update("INSERT INTO account_role SELECT ?,id FROM access_role WHERE code='ADMIN'", id);
        db.update("UPDATE write_guard SET initialized=TRUE WHERE id=1");
        return id;
    }
    public long insertAccount(String login, String name, String passwordHash) {
        db.update("INSERT INTO account(login,display_name,secret_hash) VALUES (?,?,?)", login, name, passwordHash);
        return db.queryForObject("SELECT id FROM account WHERE login=?", Long.class, login);
    }
    public Account lockAccount(String login) {
        return first(db.query("SELECT * FROM account WHERE login=? FOR UPDATE", (rs, n) -> account(rs), login));
    }
    public Account findAccount(long id) {
        return first(db.query("SELECT * FROM account WHERE id=?", (rs, n) -> account(rs), id));
    }
    public void updatePassword(long id, String hash, boolean temporary) {
        db.update("UPDATE account SET secret_hash=?,temporary=?,revision=revision+1 WHERE id=?", hash, temporary, id);
    }
    public Throttle lockThrottle(String bucket, long now) {
        db.update("INSERT INTO login_throttle(bucket,touched_at) VALUES (?,?) ON DUPLICATE KEY UPDATE bucket=bucket", bucket, now);
        return db.queryForObject("SELECT failures,blocked_until FROM login_throttle WHERE bucket=? FOR UPDATE",
                (rs, row) -> new Throttle(rs.getString(1), rs.getLong(2)), bucket);
    }
    public void updateThrottle(String bucket, String failures, long blocked, long now) {
        db.update("UPDATE login_throttle SET failures=?,blocked_until=?,touched_at=? WHERE bucket=?", failures, blocked, now, bucket);
    }
    public void clearThrottle(String bucket) { db.update("DELETE FROM login_throttle WHERE bucket=?", bucket); }
    public void createSession(String digest, long accountId, long now) {
        db.update("INSERT INTO login_session VALUES (?,?,?,?)", digest, accountId, now, now);
    }
    public Session findSession(String digest) {
        return first(db.query("SELECT a.*, s.started_at, s.active_at FROM login_session s JOIN account a ON a.id=s.account_id WHERE s.token_hash=?",
                (rs,n) -> new Session(account(rs), rs.getLong("started_at"), rs.getLong("active_at")), digest));
    }
    public int touchSession(String digest, long now) {
        return db.update("UPDATE login_session SET active_at=GREATEST(active_at,?) WHERE token_hash=?", now, digest);
    }
    public void deleteSession(String digest) { db.update("DELETE FROM login_session WHERE token_hash=?", digest); }
    public void revokeSessions(long accountId) { db.update("DELETE FROM login_session WHERE account_id=?", accountId); }
    public List<RoleRefVO> activeRoles(long id) {
        return db.query("SELECT r.id,r.code,r.name,r.enabled FROM access_role r JOIN account_role ar ON ar.role_id=r.id WHERE ar.account_id=? AND r.enabled=TRUE ORDER BY r.code",
                (rs,n) -> new RoleRefVO(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)), id);
    }
    public Set<String> activePermissions(long id) {
        return Set.copyOf(db.queryForList("SELECT DISTINCT p.permission FROM role_permission p JOIN account_role ar ON ar.role_id=p.role_id JOIN access_role r ON r.id=p.role_id WHERE ar.account_id=? AND r.enabled=TRUE", String.class, id));
    }
    public void audit(Long actor, String operation, Long target) {
        db.update("INSERT INTO audit_event(actor_id,operation,target_id) VALUES (?,?,?)", actor, operation, target);
    }
    public void cleanup(long now) {
        db.update("DELETE FROM login_session WHERE active_at<=? LIMIT 1000", now - 1800);
        db.update("DELETE FROM login_session WHERE started_at<=? LIMIT 1000", now - 28800);
        db.update("DELETE FROM login_throttle WHERE touched_at<=? LIMIT 1000", now - 1200);
    }
    private static Account account(ResultSet rs) throws SQLException {
        return new Account(rs.getLong("id"), rs.getString("login"), rs.getString("display_name"), rs.getBoolean("enabled"),
                rs.getBoolean("temporary"), rs.getLong("revision"), rs.getString("secret_hash"));
    }
    private static <T> T first(List<T> rows) { return rows.isEmpty() ? null : rows.get(0); }
}
