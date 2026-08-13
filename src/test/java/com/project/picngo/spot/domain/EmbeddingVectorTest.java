package com.project.picngo.spot.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EmbeddingVectorTest {

    @Test
    @DisplayName("encode/decode는 float 배열을 손실 없이 왕복한다")
    void encodeDecode_roundTrips() {
        float[] original = {0.1f, -0.5f, 3.14f, 0f, -0f, 1234.5678f};

        byte[] encoded = EmbeddingVector.encode(original);
        float[] decoded = EmbeddingVector.decode(encoded);

        assertThat(decoded).containsExactly(original);
    }

    @Test
    @DisplayName("encode는 float당 4바이트를 쓴다")
    void encode_usesFourBytesPerFloat() {
        float[] vector = new float[1536];

        byte[] encoded = EmbeddingVector.encode(vector);

        assertThat(encoded).hasSize(1536 * Float.BYTES);
    }

    @Test
    @DisplayName("정규화된 동일 벡터의 코사인 유사도는 1이다")
    void cosineSimilarity_identicalVectors_isOne() {
        float[] a = normalize(new float[]{1f, 2f, 3f});

        assertThat(EmbeddingVector.cosineSimilarity(a, a)).isCloseTo(1f, within(1e-5f));
    }

    @Test
    @DisplayName("직교하는 벡터의 코사인 유사도는 0이다")
    void cosineSimilarity_orthogonalVectors_isZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};

        assertThat(EmbeddingVector.cosineSimilarity(a, b)).isCloseTo(0f, within(1e-6f));
    }

    @Test
    @DisplayName("정반대 방향 벡터의 코사인 유사도는 -1이다")
    void cosineSimilarity_oppositeVectors_isNegativeOne() {
        float[] a = normalize(new float[]{1f, 2f, 3f});
        float[] b = normalize(new float[]{-1f, -2f, -3f});

        assertThat(EmbeddingVector.cosineSimilarity(a, b)).isCloseTo(-1f, within(1e-5f));
    }

    private float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }
}
