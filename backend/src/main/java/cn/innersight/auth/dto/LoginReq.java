package cn.innersight.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginReq(@NotNull @Size(min=1,max=128) String login,@NotNull @Size(min=1,max=256) String password) {}
