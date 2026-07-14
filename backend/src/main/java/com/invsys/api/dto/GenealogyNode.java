package com.invsys.api.dto;

import java.util.List;

public record GenealogyNode(
        String id,
        String type,
        String label,
        String detail,
        List<GenealogyNode> children
) {
}
