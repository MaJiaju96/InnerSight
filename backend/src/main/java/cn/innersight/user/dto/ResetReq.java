package cn.innersight.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResetReq(@NotNull Long revision,@NotNull @Size(max=256) String password) {}
