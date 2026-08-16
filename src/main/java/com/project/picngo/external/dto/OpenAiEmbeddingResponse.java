package com.project.picngo.external.dto;

import java.util.List;

public record OpenAiEmbeddingResponse(
        List<Item> data
) {
    public record Item(
            int index,
            List<Float> embedding
    ) {}
}
