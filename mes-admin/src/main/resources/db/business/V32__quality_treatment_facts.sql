-- 质量V1.3：保留既有不良完整版本，追加单节点处置及返工事实；不修改历史报工和不良记录。
CREATE TABLE mes_quality_treatment (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '命令唯一主键，采用规范UUID保证请求幂等',
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '命令与可信操作者的规范摘要',
 declaration_id VARCHAR(36) NOT NULL COMMENT '关联原始不良申报身份',
 declaration_revision INT NOT NULL COMMENT '操作时核对并冻结的不良版本号',
 execution_id BIGINT NOT NULL COMMENT '所属生产执行单元主键',
 routing_node_id BIGINT NOT NULL COMMENT '不良所属且唯一允许返工的实际路径节点',
 action_type VARCHAR(16) NOT NULL COMMENT '质量事实动作，ASSESS归属核实、ALLOCATE处置、START返工开始、RESULT结果确认',
 parent_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '返工开始关联处置记录，结果关联返工开始记录',
 attribution_type VARCHAR(16) NULL COMMENT '归属结论，INDEPENDENT独立不良、OVERLAP关联重复、UNVERIFIED待核实',
 related_declaration_id VARCHAR(36) NULL COMMENT '重叠不良所关联的原始申报身份，不重复扣减',
 basis VARCHAR(500) NULL COMMENT '归属核实的人工依据，不伪造产品序列身份',
 scrap_qty DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '本次直接报废分配数量，不允许通过返工恢复',
 rework_qty DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '本次分配或开始返工数量',
 processed_qty DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '本次实际返工加工完成数量，与正常报工分开',
 recovered_qty DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '本次结果确认的成功恢复数量',
 failed_qty DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '本次返工失败并转报废数量',
 operator_user_id BIGINT NOT NULL COMMENT '实际执行本次质量操作的系统用户主键',
 confirmed_by BIGINT NOT NULL COMMENT '可信登录的记录或结果确认用户主键',
 occurred_at DATETIME(6) NOT NULL COMMENT '实际处理时间，不作为正常路径实物流转时间',
 recorded_at DATETIME(6) NOT NULL COMMENT '服务端不可变事实记录时间',
 reason VARCHAR(500) NOT NULL COMMENT '本次核实或处置原因',
 PRIMARY KEY(id),
 KEY idx_quality_treatment_declaration(declaration_id,declaration_revision),
 KEY idx_quality_treatment_execution(execution_id,routing_node_id),
 KEY idx_quality_treatment_parent(parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量归属核实与单节点报废返工不可变事实';
CREATE TRIGGER mes_quality_treatment_no_update BEFORE UPDATE ON mes_quality_treatment
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Quality treatment facts are immutable';
CREATE TRIGGER mes_quality_treatment_no_delete BEFORE DELETE ON mes_quality_treatment
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Quality treatment facts are immutable';
