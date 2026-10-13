package dev.mesbase.business.directory.controller;

/**
 * Only public reference fields; never serialize the platform persistence entities.
 */
public record DirectoryOption(String id, String name, String code, String parentId, String departmentId) {
}
