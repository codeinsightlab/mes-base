-- V52已将无歧义的正式批次移至生产执行字段，扫码绑定不再承担业务身份。
-- 升级前须备份旧表并核实其批次已由Execution承接；不改写已执行的V52。
-- 旧扫码事件不能由绑定记录伪造，用户行为由独立扫码行为表记录。
DROP TABLE mes_production_scan_identity;
