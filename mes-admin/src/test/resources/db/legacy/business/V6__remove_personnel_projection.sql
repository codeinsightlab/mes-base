-- 撤回 V5 的独立生产人员投影；当前执行人员身份直接引用中心库 sys_user。
-- 删除范围仅为用户确认的开发/迁移验收表，设备和工作站保持不变。
DROP TABLE IF EXISTS personnel;
