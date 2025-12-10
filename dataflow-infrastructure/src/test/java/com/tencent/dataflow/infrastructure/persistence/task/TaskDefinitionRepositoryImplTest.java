package com.tencent.dataflow.infrastructure.persistence.task;

import com.tencent.dataflow.domain.task.*;
import com.tencent.dataflow.domain.task.repository.TaskDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TaskDefinitionRepositoryImpl 集成测试
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional
class TaskDefinitionRepositoryImplTest {

    @Autowired
    private TaskDefinitionRepository taskDefinitionRepository;

    private TaskDefinition testTaskDefinition;
    private TaskVersion testVersion;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testTaskDefinition = new TaskDefinition();
        testTaskDefinition.setNamespace("com.test.tasks");
        testTaskDefinition.setName("test_task");
        testTaskDefinition.setType(TaskType.SQL_OPERATOR);
        testTaskDefinition.setDescription("测试任务");
        testTaskDefinition.setCreatedAt(Instant.now());
        testTaskDefinition.setCreatedBy("test_user");

        // 创建测试版本
        testVersion = new TaskVersion();
        testVersion.setVersion("1.0.0");
        testVersion.setStatus(VersionStatus.PUBLISHED);
        testVersion.setCreatedAt(Instant.now());
        testVersion.setCreatedBy("test_user");

        // 添加输入变量
        VariableDefinition inputVar = VariableDefinition.builder()
            .name("input_table")
            .type(VariableType.STRING)
            .required(true)
            .description("输入表名")
            .build();
        testVersion.getInputVariables().add(inputVar);

        // 添加输出变量
        VariableDefinition outputVar = VariableDefinition.builder()
            .name("output_table")
            .type(VariableType.STRING)
            .required(true)
            .description("输出表名")
            .build();
        testVersion.getOutputVariables().add(outputVar);

        // 设置执行配置
        Map<String, Object> executionConfig = new HashMap<>();
        executionConfig.put("sql", "SELECT * FROM ${input_table}");
        executionConfig.put("timeout", 300);
        testVersion.setExecutionConfig(executionConfig);

        testTaskDefinition.getVersions().add(testVersion);
    }

    @Test
    void testSaveAndFindByNamespaceAndName() {
        // 保存任务定义
        taskDefinitionRepository.save(testTaskDefinition);

        // 查询任务定义
        Optional<TaskDefinition> found = taskDefinitionRepository.findByNamespaceAndName(
            "com.test.tasks", "test_task");

        // 验证
        assertThat(found).isPresent();
        TaskDefinition taskDef = found.get();
        assertThat(taskDef.getNamespace()).isEqualTo("com.test.tasks");
        assertThat(taskDef.getName()).isEqualTo("test_task");
        assertThat(taskDef.getType()).isEqualTo(TaskType.SQL_OPERATOR);
        assertThat(taskDef.getDescription()).isEqualTo("测试任务");
        
        // 验证版本
        assertThat(taskDef.getVersions()).hasSize(1);
        TaskVersion version = taskDef.getVersions().get(0);
        assertThat(version.getVersion()).isEqualTo("1.0.0");
        assertThat(version.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(version.getInputVariables()).hasSize(1);
        assertThat(version.getOutputVariables()).hasSize(1);
    }

    @Test
    void testFindByCompositeKey() {
        // 保存任务定义
        taskDefinitionRepository.save(testTaskDefinition);

        // 通过 namespace+name+version 查询
        Optional<TaskVersion> found = taskDefinitionRepository.findByCompositeKey(
            "com.test.tasks", "test_task", "1.0.0");

        // 验证
        assertThat(found).isPresent();
        TaskVersion version = found.get();
        assertThat(version.getVersion()).isEqualTo("1.0.0");
        assertThat(version.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(version.getInputVariables()).hasSize(1);
        assertThat(version.getInputVariables().get(0).getName()).isEqualTo("input_table");
        assertThat(version.getOutputVariables()).hasSize(1);
        assertThat(version.getOutputVariables().get(0).getName()).isEqualTo("output_table");
    }

    @Test
    void testFindByCompositeKey_NotFound() {
        // 查询不存在的版本
        Optional<TaskVersion> found = taskDefinitionRepository.findByCompositeKey(
            "com.test.tasks", "non_existent", "1.0.0");

        // 验证
        assertThat(found).isEmpty();
    }

    @Test
    void testSaveMultipleVersions() {
        // 添加第二个版本
        TaskVersion version2 = new TaskVersion();
        version2.setVersion("2.0.0");
        version2.setStatus(VersionStatus.PUBLISHED);
        version2.setCreatedAt(Instant.now());
        version2.setCreatedBy("test_user");
        version2.setReleaseNotes("升级到2.0版本");
        
        Map<String, Object> config2 = new HashMap<>();
        config2.put("sql", "SELECT * FROM ${input_table} WHERE status = 'active'");
        version2.setExecutionConfig(config2);
        
        testTaskDefinition.getVersions().add(version2);

        // 保存
        taskDefinitionRepository.save(testTaskDefinition);

        // 查询
        Optional<TaskDefinition> found = taskDefinitionRepository.findByNamespaceAndName(
            "com.test.tasks", "test_task");

        // 验证
        assertThat(found).isPresent();
        assertThat(found.get().getVersions()).hasSize(2);
        
        // 验证可以单独查询每个版本
        Optional<TaskVersion> v1 = taskDefinitionRepository.findByCompositeKey(
            "com.test.tasks", "test_task", "1.0.0");
        assertThat(v1).isPresent();
        
        Optional<TaskVersion> v2 = taskDefinitionRepository.findByCompositeKey(
            "com.test.tasks", "test_task", "2.0.0");
        assertThat(v2).isPresent();
        assertThat(v2.get().getReleaseNotes()).isEqualTo("升级到2.0版本");
    }

    @Test
    void testUpdateTaskDefinition() {
        // 保存初始版本
        taskDefinitionRepository.save(testTaskDefinition);

        // 更新描述
        testTaskDefinition.setDescription("更新后的描述");
        taskDefinitionRepository.save(testTaskDefinition);

        // 查询验证
        Optional<TaskDefinition> found = taskDefinitionRepository.findByNamespaceAndName(
            "com.test.tasks", "test_task");

        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isEqualTo("更新后的描述");
    }

    @Test
    void testFindByNamespace() {
        // 保存多个任务定义
        taskDefinitionRepository.save(testTaskDefinition);

        TaskDefinition task2 = new TaskDefinition();
        task2.setNamespace("com.test.tasks");
        task2.setName("another_task");
        task2.setType(TaskType.PYSPARK_OPERATOR);
        task2.setDescription("另一个任务");
        task2.setCreatedAt(Instant.now());
        task2.setCreatedBy("test_user");
        
        TaskVersion v2 = new TaskVersion();
        v2.setVersion("1.0.0");
        v2.setStatus(VersionStatus.DRAFT);
        v2.setCreatedAt(Instant.now());
        v2.setCreatedBy("test_user");
        task2.getVersions().add(v2);
        
        taskDefinitionRepository.save(task2);

        // 查询命名空间下的所有任务
        List<TaskDefinition> tasks = taskDefinitionRepository.findByNamespace("com.test.tasks");

        // 验证
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(TaskDefinition::getName)
            .containsExactlyInAnyOrder("test_task", "another_task");
    }

    @Test
    void testExists() {
        // 保存任务
        taskDefinitionRepository.save(testTaskDefinition);

        // 验证存在
        boolean exists = taskDefinitionRepository.exists("com.test.tasks", "test_task");
        assertThat(exists).isTrue();

        // 验证不存在
        boolean notExists = taskDefinitionRepository.exists("com.test.tasks", "non_existent");
        assertThat(notExists).isFalse();
    }

    @Test
    void testDelete() {
        // 保存任务
        taskDefinitionRepository.save(testTaskDefinition);

        // 验证存在
        assertThat(taskDefinitionRepository.exists("com.test.tasks", "test_task")).isTrue();

        // 删除
        taskDefinitionRepository.delete("com.test.tasks", "test_task");

        // 验证已删除
        assertThat(taskDefinitionRepository.exists("com.test.tasks", "test_task")).isFalse();
        
        Optional<TaskDefinition> found = taskDefinitionRepository.findByNamespaceAndName(
            "com.test.tasks", "test_task");
        assertThat(found).isEmpty();
    }

    @Test
    void testDraftVersion() {
        // 创建草稿版本
        TaskVersion draftVersion = new TaskVersion();
        draftVersion.setVersion("draft-20231210120000");
        draftVersion.setStatus(VersionStatus.DRAFT);
        draftVersion.setCreatedAt(Instant.now());
        draftVersion.setCreatedBy("test_user");
        
        testTaskDefinition.getVersions().clear();
        testTaskDefinition.getVersions().add(draftVersion);

        // 保存
        taskDefinitionRepository.save(testTaskDefinition);

        // 查询
        Optional<TaskVersion> found = taskDefinitionRepository.findByCompositeKey(
            "com.test.tasks", "test_task", "draft-20231210120000");

        // 验证
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(VersionStatus.DRAFT);
    }

    @Test
    void testVariableValidationRules() {
        // 创建带验证规则的变量
        VariableDefinition varWithValidation = VariableDefinition.builder()
            .name("age")
            .type(VariableType.NUMBER)
            .required(true)
            .description("年龄")
            .validation(ValidationRule.builder()
                .minValue(0.0)
                .maxValue(150.0)
                .build())
            .build();

        testVersion.getInputVariables().add(varWithValidation);
        taskDefinitionRepository.save(testTaskDefinition);

        // 查询验证
        Optional<TaskVersion> found = taskDefinitionRepository.findByCompositeKey(
            "com.test.tasks", "test_task", "1.0.0");

        assertThat(found).isPresent();
        VariableDefinition foundVar = found.get().getInputVariables().stream()
            .filter(v -> v.getName().equals("age"))
            .findFirst()
            .orElseThrow();

        assertThat(foundVar.getValidation()).isNotNull();
        assertThat(foundVar.getValidation().getMinValue()).isEqualTo(0.0);
        assertThat(foundVar.getValidation().getMaxValue()).isEqualTo(150.0);
    }

    @Test
    void testUniqueConstraint_NamespaceAndName() {
        // 保存第一个任务
        taskDefinitionRepository.save(testTaskDefinition);

        // 尝试保存相同 namespace+name 但不同 type 的任务
        // 应该更新而不是插入新记录
        testTaskDefinition.setType(TaskType.PYSPARK_OPERATOR);
        taskDefinitionRepository.save(testTaskDefinition);

        // 查询验证
        Optional<TaskDefinition> found = taskDefinitionRepository.findByNamespaceAndName(
            "com.test.tasks", "test_task");

        assertThat(found).isPresent();
        assertThat(found.get().getType()).isEqualTo(TaskType.PYSPARK_OPERATOR);
    }

    @Test
    void testUniqueConstraint_NamespaceAndNameAndVersion() {
        // 保存任务
        taskDefinitionRepository.save(testTaskDefinition);

        // 尝试更新相同版本
        testVersion.setReleaseNotes("更新说明");
        taskDefinitionRepository.save(testTaskDefinition);

        // 查询验证
        Optional<TaskVersion> found = taskDefinitionRepository.findByCompositeKey(
            "com.test.tasks", "test_task", "1.0.0");

        assertThat(found).isPresent();
        assertThat(found.get().getReleaseNotes()).isEqualTo("更新说明");
    }

    @Test
    void testValidation_NullTaskDefinition() {
        // 验证 null 参数会抛出异常
        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.validation.ConstraintViolationException.class,
            () -> taskDefinitionRepository.save(null)
        );
    }

    @Test
    void testValidation_BlankNamespace() {
        // 验证空白 namespace 会抛出异常
        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.validation.ConstraintViolationException.class,
            () -> taskDefinitionRepository.findByNamespaceAndName("", "test_task")
        );
        
        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.validation.ConstraintViolationException.class,
            () -> taskDefinitionRepository.findByNamespaceAndName("   ", "test_task")
        );
    }

    @Test
    void testValidation_BlankName() {
        // 验证空白 name 会抛出异常
        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.validation.ConstraintViolationException.class,
            () -> taskDefinitionRepository.findByNamespaceAndName("com.test.tasks", "")
        );
    }

    @Test
    void testValidation_BlankVersion() {
        // 验证空白 version 会抛出异常
        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.validation.ConstraintViolationException.class,
            () -> taskDefinitionRepository.findByCompositeKey("com.test.tasks", "test_task", "")
        );
    }

    @Test
    void testValidation_NullParameters() {
        // 验证 null 参数会抛出异常
        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.validation.ConstraintViolationException.class,
            () -> taskDefinitionRepository.exists(null, "test_task")
        );
        
        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.validation.ConstraintViolationException.class,
            () -> taskDefinitionRepository.delete("com.test.tasks", null)
        );
    }
}
