package com.tencent.dataflow.domain.repository;

import com.tencent.dataflow.domain.node.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InMemoryNodeRepository implements NodeRepository {
    private final Map<String, Node> nodeStore;

    public InMemoryNodeRepository(Map<String, Node> nodeStore) {
        this.nodeStore = nodeStore;
    }

    @Override
    public Node findById(String nodeId) {
        return nodeStore.get(nodeId);
    }

    @Override
    public List<Node> findAllActiveNodes() {
        return new ArrayList<>(nodeStore.values());
    }

    @Override
    public void save(Node node) {
        nodeStore.put(node.getId(), node);
    }
}
