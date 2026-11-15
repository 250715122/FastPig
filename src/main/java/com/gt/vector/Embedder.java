package com.gt.vector;

/**
 * 文本向量来源。抽成接口只为一件事：单测能注入确定性向量，不必加载 ONNX 模型。
 */
public interface Embedder {

    float[] embed(String text);

    boolean isAvailable();

    void initialize();

    String getErrorMessage();
}