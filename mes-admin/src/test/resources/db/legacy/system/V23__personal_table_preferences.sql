-- 个人列表列配置存放中心库，按工厂、用户、页面路由和表格标识隔离。
-- 唯一索引最长约 2504 字节，低于 InnoDB 3072 字节限制；不增加物理外键。
CREATE TABLE sys_user_table_preference (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '个人表格配置主键',
    factory_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '当前工厂标识',
    user_id BIGINT NOT NULL COMMENT '当前登录用户主键',
    url VARCHAR(512) COLLATE utf8mb4_bin NOT NULL COMMENT '不含查询参数的稳定页面路由',
    table_key VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '页面内稳定表格标识',
    columns_json TEXT NOT NULL COMMENT '列字段标识和显示状态数组，数组顺序为列顺序',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '首次保存时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近保存时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_table_preference (factory_id, user_id, url, table_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按工厂隔离的个人列表列展示偏好';
