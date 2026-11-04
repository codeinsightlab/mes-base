-- 撤回重复的旧车间与计量单位模型；保留 V1、V15 作为已执行迁移历史。
-- 执行前核对目标库、备份两表并确认无数据及正式引用；不得删除其他工厂未核对的数据。
DROP TABLE IF EXISTS md_workshop;
DROP TABLE IF EXISTS md_unit_measure;
