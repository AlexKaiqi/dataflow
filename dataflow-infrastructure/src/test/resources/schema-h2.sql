-- 任务定义表 (H2 compatible)
CREATE TABLE IF NOT EXISTS task_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    description CLOB,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT uk_namespace_name UNIQUE (namespace, name)
);

-- 任务版本表 (H2 compatible)
CREATE TABLE IF NOT EXISTS task_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_variables CLOB,
    output_variables CLOB,
    execution_config CLOB,
    release_notes CLOB,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT uk_namespace_name_version UNIQUE (namespace, name, version),
    CONSTRAINT fk_task_version_definition FOREIGN KEY (namespace, name) REFERENCES task_definition(namespace, name)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_namespace ON task_definition(namespace);
CREATE INDEX IF NOT EXISTS idx_version_status ON task_version(status);
