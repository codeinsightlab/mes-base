ALTER TABLE mf_user_preference
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 COMMENT '工厂偏好写入版本，阻止迟到切换覆盖新选择';
