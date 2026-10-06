package dev.mesbase.system.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserLookupQuery {
    private List<Long> userIds;
    private String keyword;
}
