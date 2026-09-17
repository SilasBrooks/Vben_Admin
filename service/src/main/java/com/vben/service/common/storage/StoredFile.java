package com.vben.service.common.storage;

/**
 * 存储结果：key 为存储层内部标识（本地实现即相对存储根目录的键），size 为字节数。
 * 业务层不应解读 key 的内部结构，只负责透传保存。
 */
public record StoredFile(String key, long size) {}
