package com.tencent.dataflow.domain.pipeline;

import com.tencent.dataflow.domain.node.Node;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline - 流水线
 * <p>
 * 节点的集合容器。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pipeline {
    /**
     * Pipeline ID
     */
    private String id;

    /**
     * Pipeline 名称
     */
    private String name;

    /**
     * 包含的节点列表
     */
    @Builder.Default
    private List<Node> nodes = new ArrayList<>();
}
