package dev.mesbase.system.dto;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import java.util.List;

/** Business query result; HTTP field whitelists are applied by the controller. */
public record AutoCodeRuleDocument(SysAutoCodeRule rule, List<SysAutoCodePart> parts,
                                   String version, boolean prefixEditable) { }
