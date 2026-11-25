package cn.innersight.user.repository;

import cn.innersight.common.dto.PageVO;
import cn.innersight.common.repository.ListQuery;
import cn.innersight.role.dto.RoleRefVO;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import cn.innersight.user.dto.UserVO;

@Repository
public class UserRepository {
    private record UserRow(long id, String login, String name, boolean enabled, long revision) {}
    private final JdbcTemplate db;
    public UserRepository(JdbcTemplate db) { this.db = db; }

    public PageVO<UserVO> users(String search, Boolean enabled, int page, int size) {
        ListQuery query = ListQuery.of(search, enabled, page, size, "login", "display_name");
        long total = db.queryForObject("SELECT COUNT(*) FROM account" + query.where(), Long.class, query.filters());
        var users = db.query("SELECT id,login,display_name,enabled,revision FROM account" + query.where() + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs,n) -> new UserRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getLong(5)), query.paged());
        Map<Long, List<RoleRefVO>> roles = rolesFor(users.stream().map(UserRow::id).toList());
        return new PageVO<>(users.stream().map(u -> new UserVO(u.id,u.login,u.name,u.enabled,u.revision,roles.getOrDefault(u.id,List.of()))).toList(), total, page, query.size());
    }
    public Map<Long,List<RoleRefVO>> rolesFor(List<Long> ids) {
        Map<Long,List<RoleRefVO>> result = new HashMap<>();
        if (!ids.isEmpty()) db.query("SELECT ar.account_id,r.id,r.code,r.name,r.enabled FROM account_role ar JOIN access_role r ON r.id=ar.role_id WHERE ar.account_id IN (" + placeholders(ids) + ") ORDER BY r.code",
                rs -> { result.computeIfAbsent(rs.getLong(1), key -> new ArrayList<>()).add(ref(rs,2)); }, ids.toArray());
        return result;
    }
    private String placeholders(List<Long> ids) { return String.join(",", Collections.nCopies(ids.size(), "?")); }
    private RoleRefVO ref(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new RoleRefVO(rs.getLong(i),rs.getString(i+1),rs.getString(i+2),rs.getBoolean(i+3));
    }
    public void update(long id, String name, boolean enabled) {
        db.update("UPDATE account SET display_name=?,enabled=?,revision=revision+1 WHERE id=?", name, enabled, id);
    }
    public void assign(long id, Set<Long> selection) {
        db.update("DELETE FROM account_role WHERE account_id=?", id);
        db.batchUpdate("INSERT INTO account_role(account_id,role_id) VALUES (?,?)", selection.stream().map(r -> new Object[]{id,r}).toList());
        db.update("UPDATE account SET revision=revision+1 WHERE id=?", id);
    }
    public int otherEnabledAdmins(long excluded) {
        return db.queryForObject("SELECT COUNT(*) FROM account a JOIN account_role ar ON ar.account_id=a.id JOIN access_role r ON r.id=ar.role_id WHERE a.enabled=TRUE AND r.code='ADMIN' AND a.id<>?", Integer.class, excluded);
    }
}
