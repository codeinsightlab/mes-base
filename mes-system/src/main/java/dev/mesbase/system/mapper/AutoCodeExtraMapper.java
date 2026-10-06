package dev.mesbase.system.mapper;
import dev.mesbase.common.core.domain.entity.SysAutoCodeResult;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
public interface AutoCodeExtraMapper {
    @Select("SELECT rule_id FROM sys_auto_code_rule WHERE rule_id=#{ruleId} FOR UPDATE")
    Long lockRule(long ruleId);
    @Select("SELECT COUNT(*) FROM sys_auto_code_part WHERE rule_id=#{ruleId}")
    int partCount(long ruleId);
    @Select("SELECT COUNT(*) FROM sys_auto_code_result WHERE rule_id=#{ruleId}")
    int resultCount(long ruleId);
    SysAutoCodeResult counter(@Param("ruleId") long ruleId, @Param("cycleKey") String cycleKey);
}
