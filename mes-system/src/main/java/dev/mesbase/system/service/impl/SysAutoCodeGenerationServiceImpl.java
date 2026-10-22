package dev.mesbase.system.service.impl;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeResult;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodeResultMapper;
import dev.mesbase.system.service.IAutoCodePartService;
import dev.mesbase.system.service.IAutoCodeRuleService;
import dev.mesbase.system.strategy.PartTypeHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Source AutoCodeUtil PORT. Platform identifiers only; no MES/Factory consumer registered here.
 * A row lock serializes generation across processes; no mutable singleton or ThreadLocal state.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysAutoCodeGenerationServiceImpl {
    private final IAutoCodeRuleService rules;
    private final IAutoCodePartService parts;
    private final SysAutoCodeResultMapper results;
    private final AutoCodeExtraMapper extra;
    private final PartTypeHandler handler;

    public SysAutoCodeGenerationServiceImpl(IAutoCodeRuleService rules, IAutoCodePartService parts,
                                            SysAutoCodeResultMapper results, AutoCodeExtraMapper extra,
                                            PartTypeHandler handler) {
        this.rules = rules;
        this.parts = parts;
        this.results = results;
        this.extra = extra;
        this.handler = handler;
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public String generate(String ruleCode, String inputCharacter) {
        return execute(ruleCode, inputCharacter, true);
    }

    public String preview(String ruleCode, String inputCharacter) {
        return execute(ruleCode, inputCharacter, false);
    }

    private String execute(String code, String input, boolean persist) {
        if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || input != null && input.length() > 64)
            throw new ServiceException("规则编号或输入字符不符合要求");
        var rule = rules.getOne(code);
        if (rule == null) throw new ServiceException("未找到编码规则");
        extra.lockRule(rule.getRuleId());
        rule = rules.findById(rule.getRuleId());
        if (!"Y".equals(rule.getEnableFlag())) throw new ServiceException("编码规则已停用");
        var query = new SysAutoCodePart();
        query.setRuleId(rule.getRuleId());
        var components = parts.listPart(query);
        if (components.isEmpty()) throw new ServiceException("请先配置规则组成");
        var serial = components.stream().filter(p -> "SERIALNO".equals(p.getPartType())).toList();
        if (serial.size() > 1) throw new ServiceException("只能配置一个流水号组成");
        var now = LocalDateTime.now();
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
            part.setInputCharacter(input);
            String piece;
            try {
                piece = handler.choiceExecute(part);
            } catch (IllegalArgumentException e) {
                throw new ServiceException("输入字符与组成配置不符合要求");
            }
            if (piece == null || piece.length() != part.getPartLength())
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
}
