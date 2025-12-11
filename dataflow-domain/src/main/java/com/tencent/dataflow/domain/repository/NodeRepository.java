package com.tencent.dataflow.domain.repository;

import com.tencent.dataflow.domain.node.Node;
import java.util.List;

public interface NodeRepository {
    /**
     * 根据 ID 查找节点
     */
    Node findById(String nodeId);

    /**
     * 查找所有活跃的节点 (简化版，实际可能根据 PipelineId 或订阅关系查找)
     */
    List<Node> findAllActiveNodes();

    /**
     * 保存节点
     */
    void save(Node node);
}
