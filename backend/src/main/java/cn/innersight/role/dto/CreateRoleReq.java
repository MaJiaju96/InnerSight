package cn.innersight.role.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateRoleReq(@NotNull @Size(max=128) String code,@NotNull @Size(max=100) String name,@NotNull Set<@NotNull String> permissions) {}
