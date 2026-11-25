package cn.innersight.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AssignmentReq(@NotNull Long revision,@NotNull Set<@NotNull Long> roles) {}
