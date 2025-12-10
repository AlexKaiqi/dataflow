-- 任务定义表
CREATE TABLE IF NOT EXISTS task_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID（仅用于基础设施层，不暴露到领域层）',
    namespace VARCHAR(255) NOT NULL COMMENT '命名空间',
    name VARCHAR(255) NOT NULL COMMENT '任务名称',
    type VARCHAR(50) NOT NULL COMMENT '任务类型',
    description TEXT COMMENT '任务描述',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    created_by VARCHAR(100) NOT NULL COMMENT '创建者',
    deleted BOOLEAN DEFAULT FALSE NOT NULL COMMENT '逻辑删除标记',
    UNIQUE KEY uk_namespace_name (namespace, name)
) COMMENT='任务定义表';

-- 任务版本表
CREATE TABLE IF NOT EXISTS task_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    namespace VARCHAR(255) NOT NULL COMMENT '命名空间',
    name VARCHAR(255) NOT NULL COMMENT '任务名称',
    version VARCHAR(50) NOT NULL COMMENT '版本号',
    status VARCHAR(20) NOT NULL COMMENT '版本状态',
    input_variables JSON COMMENT '输入变量定义',
    output_variables JSON COMMENT '输出变量定义',
    execution_config JSON COMMENT '执行配置',
    release_notes TEXT COMMENT '发布说明',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    created_by VARCHAR(100) NOT NULL COMMENT '创建者',
    deleted BOOLEAN DEFAULT FALSE NOT NULL COMMENT '逻辑删除标记',
    UNIQUE KEY uk_namespace_name_version (namespace, name, version),
    FOREIGN KEY (namespace, name) REFERENCES task_definition(namespace, name)
) COMMENT='任务版本表';

-- 索引
CREATE INDEX idx_namespace ON task_definition(namespace);
CREATE INDEX idx_version_status ON task_version(status);