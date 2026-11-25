package cn.innersight.common.repository;

import cn.innersight.common.error.ApiException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public record ListQuery(String where, Object[] filters, int page, int size) {
    public Object[] paged() {
        var args = Arrays.copyOf(filters, filters.length+2);
        args[filters.length] = size;
        args[filters.length+1] = (long) page * size;
        return args;
    }

    public static ListQuery of(String search, Boolean enabled, int page, int size, String codeColumn, String nameColumn) {
        if (page < 0 || size < 1 || search.length() > 100) throw ApiException.invalid("筛选条件不正确。");
        var where = new StringBuilder(" WHERE 1=1");
        var filters = new ArrayList<>();
        if (!search.isBlank()) {
            String like = "%" + search.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_") + "%";
            where.append(" AND (LOWER(").append(codeColumn).append(") LIKE ? ESCAPE '!' OR LOWER(").append(nameColumn).append(") LIKE ? ESCAPE '!')");
            filters.add(like);
            filters.add(like);
        }
        if (enabled != null) { where.append(" AND enabled=?"); filters.add(enabled); }
        return new ListQuery(where.toString(), filters.toArray(), page, Math.min(size,100));
    }
}
