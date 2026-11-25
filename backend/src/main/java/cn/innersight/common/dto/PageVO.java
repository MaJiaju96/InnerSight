package cn.innersight.common.dto;

import java.util.List;

public record PageVO<T>(List<T> items, long total, int page, int size) {}
