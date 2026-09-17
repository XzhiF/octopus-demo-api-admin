package com.octopus.demo.note;

import java.time.Instant;

/**
 * 备忘录实体(KD1:纯内存,无持久化)。
 *
 * @param id        存储分配的自增 id(从 1 起,单调递增)
 * @param content   备忘录内容(非空白)
 * @param createdAt 创建时刻
 */
public record Note(long id, String content, Instant createdAt) {
}
