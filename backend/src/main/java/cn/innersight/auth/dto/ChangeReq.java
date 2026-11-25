package cn.innersight.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeReq(@NotNull @Size(max=256) String current,@NotNull @Size(max=256) String next) {}
