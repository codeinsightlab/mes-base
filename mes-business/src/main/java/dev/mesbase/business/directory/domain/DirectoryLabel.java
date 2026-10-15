package dev.mesbase.business.directory.domain;

/**
 * Narrow directory display model converted from the canonical cached entity.
 */
public record DirectoryLabel(Long id, String code, String name, Integer version,
                             String description, String status, Long departmentId, String deletionFlag) {
    public DirectoryLabel(Long id, String code, String name, Integer version) {
        this(id, code, name, version, null, null, null, null);
    }
}
