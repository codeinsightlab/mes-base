-- Source Product COPY/PORT: doc/设计文档/数据库设计/mes_sys.sql (schema only).
-- Platform rules and counters; no customer rules, no MES consumer, no implicit Factory grant.
create table sys_auto_code_rule (
  rule_id           bigint(20)      not null auto_increment    comment '规则ID',
  rule_code         varchar(64)     not null                   comment '规则编码',
  rule_name         varchar(255)    not null                   comment '规则名称',
  rule_desc     	varchar(500)    default null               comment '描述',
  max_length        int(11)                                    comment '最大长度',
  is_padded         char(1)         not null                   comment '是否补齐',
  padded_char       varchar(20)          	                   comment '补齐字符',
  padded_method     char(1)         default 'L'                comment '补齐方式',
  enable_flag       char(1)         default 'Y' not null       comment '是否启用',
  remark            varchar(500)    default ''                 comment '备注',
  attr1             varchar(64)     default null               comment '预留字段1',
  attr2             varchar(255)     default null              comment '预留字段2',
  attr3             int(11)         default 0                  comment '预留字段3',
  attr4             int(11)         default 0                  comment '预留字段4',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  unique key uk_autocode_rule_code (rule_code),
  unique key uk_autocode_rule_name (rule_name),
  primary key (rule_id)
) engine=innodb  comment = '编码生成规则表';


-- ----------------------------
-- 2、编码生成规则组成表
-- ----------------------------

create table sys_auto_code_part (
  part_id           bigint(20)      not null auto_increment    comment '分段ID',
  rule_id           bigint(20)      not null                   comment '规则ID',
  part_index        int(11)         not null                   comment '分段序号',
  part_type         varchar(20)     not null                   comment '分段类型，INPUTCHAR：输入字符，NOWDATE：当前日期时间，FIXCHAR：固定字符，SERIALNO：流水号',
  part_code			varchar(64)                                comment '分段编号',
  part_name         varchar(255)                               comment '分段名称',
  part_length       int(11)         not null                   comment '分段长度',
  date_format      varchar(20)          	                   comment '日期时间格式',
  input_character   varchar(64)                                comment '输入字符',
  fix_character     varchar(64)                                comment '固定字符',
  seria_start_no    int(11)         					       comment '流水号起始值',
  seria_step        int(11)         					       comment '流水号步长',
  seria_now_no      int(11)         					       comment '流水号当前值',
  cycle_flag        char(1)         					       comment '流水号是否循环',
  cycle_method      varchar(20)     					       comment '循环方式，YEAR：按年，MONTH：按月，DAY：按天，HOUR：按小时，MINITE：按分钟，OTHER：按传入字符变',
  remark            varchar(500)    default ''                 comment '备注',
  attr1             varchar(64)     default null               comment '预留字段1',
  attr2             varchar(255)     default null              comment '预留字段2',
  attr3             int(11)         default 0                  comment '预留字段3',
  attr4             int(11)         default 0                  comment '预留字段4',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  unique key uk_autocode_part_index (rule_id,part_index),
  unique key uk_autocode_part_code (rule_id,part_code),
  unique key uk_autocode_part_name (rule_id,part_name),
  constraint fk_autocode_part_rule foreign key(rule_id) references sys_auto_code_rule(rule_id),
  primary key (part_id)
) engine=innodb  comment = '编码生成规则组成表';


-- ----------------------------
-- 3、编码生成记录表
-- ----------------------------

create table sys_auto_code_result (
  code_id           bigint(20)      not null auto_increment    comment '记录ID',
  rule_id           bigint(20)      not null                   comment '规则ID',
  gen_date          varchar(20)     not null                   comment '生成日期时间',
  gen_index         int(11)                                    comment '最后产生的序号',
  last_result		varchar(64)                                comment '最后产生的值',
  last_serial_no    int(11)                                    comment '最后产生的流水号',
  last_input_char   varchar(64)                                comment '最后传入的参数',
  remark            varchar(500)    default ''                 comment '备注',
  attr1             varchar(64)     default null               comment '预留字段1',
  attr2             varchar(255)    default null               comment '预留字段2',
  attr3             int(11)         default 0                  comment '预留字段3',
  attr4             int(11)         default 0                  comment '预留字段4',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  cycle_key varchar(80) not null default 'GLOBAL' comment 'Platform counter reset partition; no business datasource scope',
  unique key uk_autocode_counter (rule_id,cycle_key),
  constraint fk_autocode_result_rule foreign key(rule_id) references sys_auto_code_rule(rule_id),
  primary key (code_id)
) engine=innodb  comment = '编码生成记录表';


-- ----------------------------

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,create_by,create_time)
SELECT '自动编码规则',menu_id,20,'autocode','system/autocode/index','C','system:autocode:rule:list','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE path='system' AND menu_type='M' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '查询规则',menu_id,0,'','F','system:autocode:rule:query','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '新增规则',menu_id,0,'','F','system:autocode:rule:add','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '修改规则',menu_id,0,'','F','system:autocode:rule:edit','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '删除规则',menu_id,0,'','F','system:autocode:rule:remove','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '生成编码',menu_id,0,'','F','system:autocode:rule:generate','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '查询组成',menu_id,0,'','F','system:autocode:part:list','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '组成详情',menu_id,0,'','F','system:autocode:part:query','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '新增组成',menu_id,0,'','F','system:autocode:part:insert','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '修改组成',menu_id,0,'','F','system:autocode:part:update','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time) SELECT '删除组成',menu_id,0,'','F','system:autocode:part:remove','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/autocode/index' AND legacy_id IS NULL;

INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r CROSS JOIN sys_menu m WHERE r.kind='PLATFORM' AND r.status='0' AND r.del_flag='0' AND m.perms LIKE 'system:autocode:%'
AND EXISTS(SELECT 1 FROM sys_role_menu rm JOIN sys_menu old_menu ON old_menu.menu_id=rm.menu_id WHERE rm.role_id=r.role_id AND old_menu.perms='system:role:edit');

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) VALUES('编码组成类型','sys_autocode_parttype','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(1,'输入字符','INPUTCHAR','sys_autocode_parttype','N','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(2,'当前日期','NOWDATE','sys_autocode_parttype','N','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(3,'固定字符','FIXCHAR','sys_autocode_parttype','N','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(4,'流水号','SERIALNO','sys_autocode_parttype','N','0','migration',CURRENT_TIMESTAMP);

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) VALUES('流水号循环方式','sys_autocode_cyclemethod','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(1,'按年','YEAR','sys_autocode_cyclemethod','N','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(2,'按月','MONTH','sys_autocode_cyclemethod','N','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(3,'按日','DAY','sys_autocode_cyclemethod','N','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(4,'按小时','HOUR','sys_autocode_cyclemethod','N','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(5,'按分钟','MINUTE','sys_autocode_cyclemethod','N','0','migration',CURRENT_TIMESTAMP);
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time) VALUES(6,'按输入字符','OTHER','sys_autocode_cyclemethod','N','0','migration',CURRENT_TIMESTAMP);
