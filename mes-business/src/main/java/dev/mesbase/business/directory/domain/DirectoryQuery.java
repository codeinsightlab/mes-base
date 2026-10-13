package dev.mesbase.business.directory.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DirectoryQuery(@Size(max = 30) String keyword, @Positive Long departmentId,
                             @Min(0) Integer offset, @Min(1) @Max(100) Integer limit) {
    public int pageOffset() {
        return offset == null ? 0 : offset;
    }

    public int pageLimit() {
        return limit == null ? 20 : limit;
    }
}
