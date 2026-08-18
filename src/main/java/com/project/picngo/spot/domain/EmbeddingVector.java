package com.project.picngo.spot.domain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 임베딩 벡터의 저장 형식과 유사도 계산.
 *
 * <p>DB에는 float32 배열을 그대로 이진 저장한다(4바이트 × 차원 수). JSON이나 문자열로
 * 담지 않는 이유는 search-eval 실험에서 이미 겪은 문제 때문이다 - 벡터를 텍스트로
 * 다루면 개수가 많아질 때 문자열 하나가 너무 커진다. 스팟 하나당 벡터 하나뿐이라
 * 여기서는 그 문제가 안 생기지만, 애초에 이진으로 두면 걱정할 이유 자체가 없다.
 */
public final class EmbeddingVector {

    private EmbeddingVector() {
    }

    public static byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    public static float[] decode(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    /**
     * 코사인 유사도. 임베딩 API가 돌려주는 벡터는 이미 길이가 1로 정규화되어 있어
     * 내적만 계산하면 된다(search-eval의 embedding.js와 같은 전제).
     * 1에 가까울수록 의미가 비슷하다.
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        float dot = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
