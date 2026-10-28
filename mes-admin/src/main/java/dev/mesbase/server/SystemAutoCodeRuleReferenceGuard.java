package dev.mesbase.server;

import dev.mesbase.business.autocode.dao.AutoCodeDocumentReferenceMapper;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.BusinessPersistence;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.service.AutoCodeRuleReferenceGuard;
import dev.mesbase.system.service.AutoCodeRuleType;
import dev.mesbase.server.managed.ManagedDatasourceService;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A rule can be referenced by historical documents that predate generation results. Check each
 * bound business database before deletion; an unavailable bound factory fails closed.
 */
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SystemAutoCodeRuleReferenceGuard implements AutoCodeRuleReferenceGuard {
    private static final int PAGE_SIZE = 100;
    private final ManagedDatasourceService factories;
    private final BusinessPersistence business;
    private final SysAutoCodePartMapper parts;

    public SystemAutoCodeRuleReferenceGuard(ManagedDatasourceService factories, BusinessPersistence business,
                                            SysAutoCodePartMapper parts) {
        this.factories = factories;
        this.business = business;
        this.parts = parts;
    }

    @Override
    public void requireEnabledFactory(String factoryId) {
        if (factoryId == null || factoryId.isBlank()) return;
        ManagedDatasourceService.FactoryView target = null;
        for (int offset = 0; ; offset += PAGE_SIZE) {
            List<ManagedDatasourceService.FactoryView> page = factories.factoryList(offset, PAGE_SIZE);
            target = page.stream().filter(factory -> factoryId.equals(factory.factoryId())).findFirst().orElse(null);
            if (target != null || page.size() < PAGE_SIZE) break;
        }
        if (target == null) throw new ServiceException("所选工厂不存在");
        if (!"ENABLED".equals(target.status()) || target.datasourceId() == null)
            throw new ServiceException("编码规则只能配置到已启用且绑定业务库的工厂");
        if (factories.platformDatasourceIds(List.of(target.datasourceId())).contains(target.datasourceId()))
            throw new ServiceException("编码规则不能配置到绑定中心库的工厂");
        factories.resolve(new FactoryScope(new FactoryId(factoryId)));
    }

    @Override
    public String requireUnreferenced(SysAutoCodeRule rule) {
        if (rule.getRuleType() != null && !AutoCodeRuleType.supports(rule.getRuleType()))
            throw new ServiceException("规则单据类型无效，无法安全核对单据引用，不能删除");
        String prefix = prefix(rule);
        if (prefix.isEmpty() && rule.getRuleType() == null)
            throw new ServiceException("规则没有固定前缀，无法核对单据引用，不能删除");
        scan(rule, prefix);
        return prefix;
    }

    @Override
    public void requirePrefixChangeUnreferenced(SysAutoCodeRule rule, String oldPrefix, String newPrefix) {
        if (rule == null || rule.getRuleType() != null && !AutoCodeRuleType.supports(rule.getRuleType()))
            throw new ServiceException("规则单据类型无效，无法安全核对前缀变更");
        String prefixToCheck = oldPrefix.isEmpty() ? newPrefix : oldPrefix;
        if (!prefixToCheck.isEmpty()) scan(rule, prefixToCheck);
    }

    private void scan(SysAutoCodeRule rule, String prefix) {
        if (prefix.isEmpty()) return;
        String pattern = escapeLike(prefix) + "%";
        String direction = switch (rule.getRuleType() == null ? "" : rule.getRuleType()) {
            case "STOCK_IN" -> AutoCodeRuleType.STOCK_IN.name();
            case "STOCK_OUT" -> AutoCodeRuleType.STOCK_OUT.name();
            default -> null;
        };
        boolean scopedFactoryFound = rule.getFactoryId() == null || rule.getFactoryId().isBlank();
        for (int offset = 0; ; offset += PAGE_SIZE) {
            List<ManagedDatasourceService.FactoryView> page = factories.factoryList(offset, PAGE_SIZE);
            if (!scopedFactoryFound && page.stream().anyMatch(factory -> rule.getFactoryId().equals(factory.factoryId())))
                scopedFactoryFound = true;
            var enabledFactories = page.stream()
                    .filter(factory -> "ENABLED".equals(factory.status()))
                    .filter(factory -> rule.getFactoryId() == null || rule.getFactoryId().isBlank()
                            || rule.getFactoryId().equals(factory.factoryId()))
                    .toList();
            var datasourceIds = enabledFactories.stream().map(ManagedDatasourceService.FactoryView::datasourceId)
                    .filter(Objects::nonNull).distinct().toList();
            var platformDatasourceIds = factories.platformDatasourceIds(datasourceIds);
            for (var factory : enabledFactories) {
                if (factory.datasourceId() == null || platformDatasourceIds.contains(factory.datasourceId())) continue;
                var scope = new FactoryScope(new FactoryId(factory.factoryId()));
                boolean referenced = business.transaction(scope, session -> {
                    var mapper = session.mapper(AutoCodeDocumentReferenceMapper.class);
                    if (rule.getRuleType() == null || AutoCodeRuleType.WORK_ORDER.name().equals(rule.getRuleType()))
                        if (mapper.hasWorkorderPrefix(pattern) != 0) return true;
                    if (rule.getRuleType() == null || AutoCodeRuleType.STOCK_IN.name().equals(rule.getRuleType())
                            || AutoCodeRuleType.STOCK_OUT.name().equals(rule.getRuleType()))
                        return mapper.hasStockDocumentPrefix(pattern, direction) != 0;
                    return false;
                });
                if (referenced)
                    throw new ServiceException("编码规则前缀已被单据引用，不能删除或修改");
            }
            if (page.size() < PAGE_SIZE) break;
        }
        if (!scopedFactoryFound) throw new ServiceException("规则配置的工厂不存在，无法核对单据引用");
    }

    private String prefix(SysAutoCodeRule rule) {
        SysAutoCodePart query = new SysAutoCodePart();
        query.setRuleId(rule.getRuleId());
        List<SysAutoCodePart> configured = parts.selectSysAutoCodePartList(query);
        var prefix = new StringBuilder();
        for (SysAutoCodePart part : configured) {
            if (!"FIXCHAR".equals(part.getPartType())) break;
            if (part.getFixCharacter() != null) prefix.append(part.getFixCharacter());
        }
        return prefix.toString();
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
