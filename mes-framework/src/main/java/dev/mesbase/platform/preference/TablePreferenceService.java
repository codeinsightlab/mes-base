package dev.mesbase.platform.preference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.preference.TableColumnPreference;
import dev.mesbase.system.preference.TablePreferenceKey;
import dev.mesbase.system.preference.TablePreferenceMapper;
import dev.mesbase.system.preference.TablePreferenceOwner;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal display preferences use only the center database; they never grant field/data permissions.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class TablePreferenceService {
    private final TablePreferenceMapper mapper;
    private final JsonMapper json = JsonMapper.builder().build();

    public TablePreferenceService(TablePreferenceMapper mapper) {
        this.mapper = mapper;
    }

    private TablePreferenceOwner owner() {
        return new TablePreferenceOwner(FactoryContext.required().factoryId().value(), SecurityUtils.getUserId());
    }

    private static void validate(TablePreferenceKey key) {
        if (key == null || key.url() == null || key.url().length() > 512
                || !key.url().startsWith("/") || key.url().contains("//")
                || (key.url().length() > 1 && key.url().endsWith("/"))
                || key.url().matches(".*[\\s?#<>\\\\\"'\\p{Cntrl}].*")
                || key.tableKey() == null || !key.tableKey().matches("[A-Za-z0-9][A-Za-z0-9:_./-]{0,191}")) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<TableColumnPreference> get(TablePreferenceKey key) {
        validate(key);
        String stored = mapper.selectColumns(owner(), key);
        if (stored == null) return null;
        try {
            return json.readValue(stored, new TypeReference<List<TableColumnPreference>>() {
            });
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored table preference invalid", failure);
        }
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void save(TablePreferenceKey key, List<TableColumnPreference> columns) {
        validate(key);
        if (columns == null || columns.isEmpty() || columns.size() > 100)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var keys = new HashSet<String>();
        for (var column : columns) {
            if (column == null || column.key() == null || !column.key().matches("[A-Za-z_][A-Za-z0-9_.:-]{0,63}")
                    || !keys.add(column.key())) throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        if (columns.stream().noneMatch(TableColumnPreference::visible))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        try {
            String value = json.writeValueAsString(columns);
            if (value.getBytes(StandardCharsets.UTF_8).length > 16384)
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            mapper.upsert(owner(), key, value);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Table preference serialization failed", failure);
        }
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void reset(TablePreferenceKey key) {
        validate(key);
        mapper.delete(owner(), key);
    }
}
