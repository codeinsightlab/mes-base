package dev.mesbase.system.service.impl;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeResult;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.mapper.SysAutoCodeResultMapper;
import dev.mesbase.system.strategy.PartTypeHandler;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Source AutoCodeUtil PORT. Platform identifiers only; no MES/Factory consumer registered here.
 * A row lock serializes generation across processes; no mutable singleton or ThreadLocal state.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysAutoCodeGenerationServiceImpl {
    private static final ZoneId AUTO_CODE_ZONE = ZoneId.of("Asia/Shanghai");
    private final AutoCodeRuleSnapshotCache configs;
    private final SysAutoCodeRuleMapper rules;
    private final SysAutoCodePartMapper parts;
    private final SysAutoCodeResultMapper results;
    private final AutoCodeExtraMapper extra;
    private final PartTypeHandler handler;
    private final TransactionTemplate transactions;

    public SysAutoCodeGenerationServiceImpl(AutoCodeRuleSnapshotCache configs, SysAutoCodeRuleMapper rules,
                                            SysAutoCodePartMapper parts,
                                            SysAutoCodeResultMapper results, AutoCodeExtraMapper extra,
                                            PartTypeHandler handler,
                                            @Qualifier("masterTransactionManager") PlatformTransactionManager manager) {
        this.configs = configs;
        this.rules = rules;
        this.parts = parts;
        this.results = results;
        this.extra = extra;
        this.handler = handler;
        this.transactions = new TransactionTemplate(manager);
    }

    public String generate(String ruleCode, String inputCharacter) {
        return run(ruleCode, inputCharacter, true, false);
    }

    /** Public platform numbering entry. Each call allocates a new value; it is not request-idempotent. */
    public String getDocNo(String type) {
        return run(type, null, true, true);
    }

    public String preview(String ruleCode, String inputCharacter) {
        return run(ruleCode, inputCharacter, false, false);
    }

    /** Preview the getDocNo contract without persisting or advancing the daily counter. */
    public String previewDocNo(String type) {
        return run(type, null, false, true);
    }

    private String run(String code, String input, boolean persist, boolean docNoContract) {
        if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || input != null && input.length() > 64)
            throw new ServiceException("规则编号或输入字符不符合要求");
        var config = configs.get(code);
        if (config == null) throw new ServiceException("未找到编码规则");
        return transactions.execute(status -> execute(code, input, persist, docNoContract, config));
    }

    private String execute(String code, String input, boolean persist, boolean docNoContract,
                           AutoCodeRuleSnapshotCache.RuleConfig config) {
        if (extra.lockRule(config.ruleId()) == null) throw new ServiceException("编码规则不存在或已删除");
        // Locking reads bypass an earlier repeatable-read snapshot and see the committed writer
        // that won the rule lock, even if shared-cache invalidation was unavailable.
        SysAutoCodeRule rule = rules.findByIdForUpdate(config.ruleId());
        if (rule == null || !code.equals(rule.getRuleCode())) throw new ServiceException("编码规则不存在或已删除");
        if (!"Y".equals(rule.getEnableFlag())) throw new ServiceException("编码规则已停用");
        if (rule.getMaxLength() == null || rule.getMaxLength() < 1 || rule.getMaxLength() > 64)
            throw new ServiceException("规则最大长度必须在1到64之间");
        var components = parts.selectByRuleIdForUpdate(rule.getRuleId());
        if (components.isEmpty()) throw new ServiceException("请先配置规则组成");
        var serial = components.stream().filter(p -> "SERIALNO".equals(p.getPartType())).toList();
        if (serial.size() > 1) throw new ServiceException("只能配置一个流水号组成");
        var now = LocalDateTime.now(Clock.system(AUTO_CODE_ZONE));
        if (docNoContract) validateDocNoContract(rule, components, serial);
        String cycle = serial.isEmpty() ? "GLOBAL" : cycle(serial.get(0), input, now);
        var previous = extra.counter(rule.getRuleId(), cycle);
        int next = 0;
        if (!serial.isEmpty()) {
            try {
                next = previous == null ? serial.get(0)
                        .getSeriaStartNo() : Math.addExact(previous.getLastSerialNo(), serial.get(0).getSeriaStep());
            } catch (ArithmeticException e) {
                throw new ServiceException("流水号已耗尽");
            }
            serial.get(0).setSeriaNowNo(next);
        }
        var value = new StringBuilder();
        for (var part : components) {
            if ("NOWDATE".equals(part.getPartType())) {
                String piece = DateTimeFormatter.ofPattern(part.getDateFormat()).format(now);
                if (piece.length() != part.getPartLength()) throw new ServiceException("组成长度不符合配置");
                value.append(piece);
                continue;
            }
            part.setInputCharacter(input);
            String piece;
            try {
                piece = handler.choiceExecute(part);
            } catch (IllegalArgumentException e) {
                throw new ServiceException("输入字符与组成配置不符合要求");
            }
            if (piece == null || (!"SERIALNO".equals(part.getPartType()) && piece.length() != part.getPartLength()))
                throw new ServiceException("组成长度不符合配置");
            value.append(piece);
        }
        String generated = padding(rule, value.toString());
        if (persist) {
            var row = previous == null ? new SysAutoCodeResult() : previous;
            row.setRuleId(rule.getRuleId());
            row.setCycleKey(cycle);
            row.setGenDate(DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now));
            row.setGenIndex(previous == null ? 1 : Math.addExact(previous.getGenIndex(), 1));
            row.setLastResult(generated);
            row.setLastSerialNo(next);
            row.setLastInputChar(input);
            if (previous == null) results.add(row);
            else results.updateAutoCodeResult(row);
        }
        return generated;
    }

    static String padding(SysAutoCodeRule rule, String value) {
        if (value.isEmpty() || value.length() > rule.getMaxLength())
            throw new ServiceException("生成编码超过最大长度或为空");
        if (!"Y".equals(rule.getIsPadded())) return value;
        String fill = rule.getPaddedChar().repeat(rule.getMaxLength() - value.length());
        return "L".equals(rule.getPaddedMethod()) ? fill + value : value + fill;
    }

    static String cycle(SysAutoCodePart part, String input, LocalDateTime now) {
        if (!"Y".equals(part.getCycleFlag())) return "GLOBAL";
        String pattern = switch (part.getCycleMethod()) {
            case "YEAR" -> "yyyy";
            case "MONTH" -> "yyyyMM";
            case "DAY" -> "yyyyMMdd";
            case "HOUR" -> "yyyyMMddHH";
            case "MINUTE" -> "yyyyMMddHHmm";
            case "OTHER" -> null;
            default -> throw new ServiceException("不支持的循环方式");
        };
        if (pattern != null) return part.getCycleMethod() + ":" + DateTimeFormatter.ofPattern(pattern).format(now);
        if (StringUtils.isBlank(input)) throw new ServiceException("按输入字符循环时必须传入字符");
        return "INPUT:" + input;
    }

    private static void validateDocNoContract(SysAutoCodeRule rule,
            List<SysAutoCodePart> parts, List<SysAutoCodePart> serial) {
        boolean prefix = !parts.isEmpty() && "FIXCHAR".equals(parts.get(0).getPartType())
                && parts.get(0).getFixCharacter() != null
                && parts.get(0).getPartLength() != null
                && parts.get(0).getFixCharacter().length() == parts.get(0).getPartLength();
        boolean date = parts.size() > 1 && "NOWDATE".equals(parts.get(1).getPartType())
                && "yyyyMMdd".equals(parts.get(1).getDateFormat());
        boolean validSerial = serial.size() == 1;
        if (parts.size() == 3) {
            var part = parts.get(2);
            validSerial &= "SERIALNO".equals(part.getPartType())
                    && Integer.valueOf(1).equals(part.getSeriaStartNo())
                    && Integer.valueOf(1).equals(part.getSeriaStep())
                    && "Y".equals(part.getCycleFlag()) && "DAY".equals(part.getCycleMethod())
                    && part.getPartLength() != null && part.getPartLength() >= 3;
        } else validSerial = false;
        if (!prefix || !date || !validSerial || rule.getMaxLength() == null || rule.getMaxLength() > 64
                || "Y".equals(rule.getIsPadded()))
            throw new ServiceException("规则不符合getDocNo(type)契约：需依次配置FIXCHAR前缀、yyyyMMdd日期、DAY每日流水（起始1、步长1、最小宽度3），且不启用整号补齐");
    }
}
