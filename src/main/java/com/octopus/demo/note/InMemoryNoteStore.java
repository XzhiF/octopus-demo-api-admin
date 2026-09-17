package com.octopus.demo.note;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版 Note 存储(KD1:ConcurrentHashMap + AtomicLong,JVM 级并发安全,不引框架)。
 * 无持久化,进程重启即清空。
 */
public class InMemoryNoteStore {

    private final Map<Long, Note> notes = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    /**
     * 新建备忘录。
     *
     * @param content 内容,不允许 null 或空白
     * @return 带自增 id 与 createdAt 的 Note
     * @throws IllegalArgumentException content 为 null 或空白
     */
    public Note create(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        long id = idSequence.incrementAndGet();
        Note note = new Note(id, content, Instant.now());
        notes.put(id, note);
        return note;
    }

    /**
     * 全部备忘录,新在前(id 倒序)。
     */
    public List<Note> list() {
        List<Note> result = new ArrayList<>(notes.values());
        result.sort(Comparator.comparingLong(Note::id).reversed());
        return result;
    }

    /**
     * 按 id 查找。
     */
    public Optional<Note> get(long id) {
        return Optional.ofNullable(notes.get(id));
    }

    /**
     * 按 id 删除。
     *
     * @return 命中删除 true,未命中 false
     */
    public boolean delete(long id) {
        return notes.remove(id) != null;
    }
}
