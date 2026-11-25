package cn.innersight.role.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateRoleReq(@NotNull Long revision,@NotNull @Size(max=100) String name,@NotNull Boolean enabled,
                         @NotNull Set<@NotNull String> permissions,@NotNull Long count) {}
