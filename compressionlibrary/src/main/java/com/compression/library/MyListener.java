package com.compression.library;

public interface MyListener {
    void onFinished(String filePath);
    void onCancelled();
}