package cn.innersight.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserReq(@NotNull @Size(max=128) String login,@NotNull @Size(max=100) String name,@NotNull @Size(max=256) String password) {}
