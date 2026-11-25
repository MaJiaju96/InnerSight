package cn.innersight.auth.service;

import cn.innersight.common.error.ApiException;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class IdentityRules {
    public static final String ADMIN = "ADMIN";
    public static final String USER_READ = "user:read";
    public static final String USER_MANAGE = "user:manage";
    public static final String ROLE_READ = "role:read";
    public static final Set<String> CATALOG = Set.of(USER_READ, USER_MANAGE, ROLE_READ);

    private IdentityRules() {}

    public static String login(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
    public static String newLogin(String value) {
        String canonical = value == null ? "" : value.strip();
        if (!canonical.matches("[A-Za-z0-9._-]{3,32}")) throw ApiException.invalid("登录名格式不正确。");
        return canonical.toLowerCase(Locale.ROOT);
    }
    public static String roleCode(String value) {
        String canonical = value == null ? "" : value.strip();
        if (!canonical.matches("[A-Za-z0-9_]{3,32}")) throw ApiException.invalid("角色编码格式不正确。");
        return canonical.toUpperCase(Locale.ROOT);
    }
    public static String name(String value) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty() || length(clean) > 50) throw ApiException.invalid("名称须为 1–50 个字符。");
        return clean;
    }
    public static String password(String value) {
        if (value == null || length(value) < 15 || length(value) > 128)
            throw ApiException.invalid("密码须为 15–128 个字符。");
        return value;
    }
    public static int length(String value) { return value.codePointCount(0, value.length()); }
    public static Set<String> permissions(Collection<String> input) {
        if (input == null || input.stream().anyMatch(p -> p == null || !CATALOG.contains(p)))
            throw ApiException.invalid("含有未登记的权限。");
        Set<String> result = new TreeSet<>(input);
        if (result.contains(USER_MANAGE)) result.add(USER_READ);
        return result;
    }
    public static List<Long> failures(List<Long> previous, long now) {
        var result = new java.util.ArrayList<>(previous.stream().filter(t -> now - t < 600).toList());
        result.add(now);
        return List.copyOf(result);
    }
    public static boolean expired(long started, long active, long now) {
        return now - started >= 28_800 || now - active >= 1_800;
    }
}
