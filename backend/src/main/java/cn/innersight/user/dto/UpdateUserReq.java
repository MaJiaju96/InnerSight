package cn.innersight.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserReq(@NotNull Long revision,@NotNull @Size(max=100) String name,@NotNull Boolean enabled) {}
