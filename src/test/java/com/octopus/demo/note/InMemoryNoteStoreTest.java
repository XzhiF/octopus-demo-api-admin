package com.octopus.demo.note;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InMemoryNoteStore 单测:空白拒绝、id 单调递增、list 倒序、get/delete 命中与未命中、并发安全。
 */
class InMemoryNoteStoreTest {

    private InMemoryNoteStore store;

    @BeforeEach
    void freshStore() {
        store = new InMemoryNoteStore();
    }

    @Test
    void createStoresContentWithIdOneAndCreatedAt() {
        Note note = store.create("买咖啡豆");
        assertEquals(1L, note.id());
        assertEquals("买咖啡豆", note.content());
        assertNotNull(note.createdAt());
    }

    @Test
    void createRejectsEmptyContent() {
        assertThrows(IllegalArgumentException.class, () -> store.create(""));
    }

    @Test
    void createRejectsWhitespaceOnlyContent() {
        assertThrows(IllegalArgumentException.class, () -> store.create("   \t  "));
    }

    @Test
    void createRejectsNullContent() {
        assertThrows(IllegalArgumentException.class, () -> store.create(null));
    }

    @Test
    void rejectedCreateDoesNotConsumeIdOrStoreAnything() {
        assertThrows(IllegalArgumentException.class, () -> store.create(" "));
        assertTrue(store.list().isEmpty());
        assertEquals(1L, store.create("ok").id());
    }

    @Test
    void idsIncreaseMonotonically() {
        List<Long> ids = new ArrayList<>();
        ids.add(store.create("a").id());
        ids.add(store.create("b").id());
        ids.add(store.create("c").id());
        assertEquals(List.of(1L, 2L, 3L), ids);
    }

    @Test
    void listIsEmptyForFreshStore() {
        assertTrue(store.list().isEmpty());
    }

    @Test
    void listReturnsNewestFirst() {
        store.create("first");
        store.create("second");
        store.create("third");
        List<Note> all = store.list();
        assertEquals(3, all.size());
        assertEquals(List.of("third", "second", "first"), all.stream().map(Note::content).toList());
        assertEquals(List.of(3L, 2L, 1L), all.stream().map(Note::id).toList());
    }

    @Test
    void getReturnsMatchingNote() {
        Note created = store.create("命中我");
        Optional<Note> found = store.get(created.id());
        assertTrue(found.isPresent());
        assertEquals("命中我", found.get().content());
        assertEquals(created.createdAt(), found.get().createdAt());
    }

    @Test
    void getReturnsEmptyForUnknownId() {
        store.create("存在");
        assertTrue(store.get(999L).isEmpty());
        assertTrue(store.get(0L).isEmpty());
    }

    @Test
    void deleteRemovesNoteAndReturnsTrue() {
        Note note = store.create("待删");
        assertTrue(store.delete(note.id()));
        assertTrue(store.get(note.id()).isEmpty());
        assertTrue(store.list().isEmpty());
    }

    @Test
    void deleteReturnsFalseForUnknownId() {
        assertFalse(store.delete(42L));
    }

    @Test
    void deletedIdIsNotResurrectableAndIdsNeverReuse() {
        Note first = store.create("旧");
        assertTrue(store.delete(first.id()));
        Note second = store.create("新");
        assertEquals(2L, second.id());
        assertFalse(store.delete(first.id()));
    }

    @Test
    void concurrentCreatesProduceUniqueMonotonicIds() throws Exception {
        int threads = 8;
        int perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Note>> tasks = new ArrayList<>();
            for (int i = 0; i < threads * perThread; i++) {
                tasks.add(() -> store.create("并发"));
            }
            List<Note> created = new ArrayList<>();
            for (Future<Note> f : pool.invokeAll(tasks)) {
                created.add(f.get());
            }
            long distinctIds = created.stream().mapToLong(Note::id).distinct().count();
            assertEquals(threads * perThread, distinctIds);
            assertEquals(threads * perThread, store.list().size());
            assertEquals(threads * perThread, store.list().get(0).id());
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
