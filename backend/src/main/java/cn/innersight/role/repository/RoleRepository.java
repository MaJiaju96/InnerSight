package cn.innersight.role.repository;

import cn.innersight.common.dto.PageVO;
import cn.innersight.common.repository.ListQuery;
import cn.innersight.role.dto.RoleRefVO;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import cn.innersight.common.error.ApiException;
import cn.innersight.role.dto.RoleVO;
import static cn.innersight.auth.service.IdentityRules.ADMIN;
import static cn.innersight.auth.service.IdentityRules.CATALOG;

@Repository
public class RoleRepository {
    public record RoleRow(long id, String code, String name, boolean enabled, long revision, long count) {}
    private final JdbcTemplate db;
    public RoleRepository(JdbcTemplate db) { this.db = db; }

    public PageVO<RoleVO> roles(String search, Boolean enabled, int page, int size) {
        ListQuery query = ListQuery.of(search, enabled, page, size, "code", "name");
        long total = db.queryForObject("SELECT COUNT(*) FROM access_role" + query.where(), Long.class, query.filters());
        var roles = db.query(roleSelect() + query.where() + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", this::roleRow, query.paged());
        Map<Long, Set<String>> permissions = permissionsFor(roles.stream().map(RoleRow::id).toList());
        return new PageVO<>(roles.stream().map(r -> view(r, permissions)).toList(),total,page,query.size());
    }
    private Map<Long,Set<String>> permissionsFor(List<Long> ids) {
        Map<Long,Set<String>> result = new HashMap<>();
        if (!ids.isEmpty()) db.query("SELECT role_id,permission FROM role_permission WHERE role_id IN (" + placeholders(ids) + ")",
                rs -> { result.computeIfAbsent(rs.getLong(1), key -> new TreeSet<>()).add(rs.getString(2)); }, ids.toArray());
        return result;
    }
    private String placeholders(List<Long> ids) { return String.join(",",Collections.nCopies(ids.size(),"?")); }
    private RoleRefVO ref(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new RoleRefVO(rs.getLong(i),rs.getString(i+1),rs.getString(i+2),rs.getBoolean(i+3));
    }
    private String roleSelect() {
        return "SELECT r.id,r.code,r.name,r.enabled,r.revision,(SELECT COUNT(*) FROM account_role ar WHERE ar.role_id=r.id) FROM access_role r";
    }
    private RoleRow roleRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new RoleRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getLong(5),rs.getLong(6));
    }
    public RoleRow findRole(long id) {
        var rows = db.query(roleSelect() + " WHERE r.id=?", this::roleRow, id);
        var result = rows.isEmpty() ? null : rows.get(0);
        if (result == null) throw ApiException.missing();
        return result;
    }
    private RoleVO view(RoleRow r, Map<Long,Set<String>> permissions) {
        return new RoleVO(r.id,r.code,r.name,r.enabled,r.revision,r.count,ADMIN.equals(r.code) ? CATALOG : permissions.getOrDefault(r.id, Set.of()));
    }
    public RoleVO role(long id) { return view(findRole(id), permissionsFor(List.of(id))); }
    public List<RoleRefVO> options() {
        return db.query("SELECT id,code,name,enabled FROM access_role ORDER BY code", (rs,n) -> ref(rs,1));
    }
    public long insert(String code, String name, Set<String> permissions) {
        db.update("INSERT INTO access_role(code,name) VALUES (?,?)", code, name);
        long id = db.queryForObject("SELECT id FROM access_role WHERE code=?", Long.class, code);
        replacePermissions(id, permissions);
        return id;
    }
    public void update(long id, String name, boolean enabled, Set<String> permissions) {
        db.update("UPDATE access_role SET name=?,enabled=?,revision=revision+1 WHERE id=?", name, enabled, id);
        replacePermissions(id, permissions);
    }
    private void replacePermissions(long id, Set<String> permissions) {
        db.update("DELETE FROM role_permission WHERE role_id=?", id);
        db.batchUpdate("INSERT INTO role_permission(role_id,permission) VALUES (?,?)", permissions.stream().map(p -> new Object[]{id,p}).toList());
    }
}
