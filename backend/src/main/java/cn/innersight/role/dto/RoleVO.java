package cn.innersight.role.dto;
import java.util.Set;
public record RoleVO(long id, String code, String name, boolean enabled, long revision, long count, Set<String> permissions) {}
