package com.octopus.demo.note;

import com.octopus.demo.common.bean.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Note 备忘录三接口(票 02)。响应统一 R 包装(KD2),400/404 同形且 HTTP 状态码给对。
 * 存储复用票 01 的 InMemoryNoteStore;控制器为 Spring 单例,store 随其实例化一份,重启即清空(US3)。
 */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final InMemoryNoteStore store = new InMemoryNoteStore();

    /** POST 请求体:{content} */
    public record CreateNoteRequest(String content) {
    }

    @PostMapping
    public ResponseEntity<R<Note>> create(@RequestBody CreateNoteRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(R.ok(store.create(request.content())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(R.fail(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
        }
    }

    @GetMapping
    public R<List<Note>> list() {
        return R.ok(store.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<R<Note>> get(@PathVariable long id) {
        return store.get(id)
                .map(note -> ResponseEntity.ok(R.ok(note)))
                .orElseGet(() -> notFound(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<R<Void>> delete(@PathVariable long id) {
        if (store.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return notFound(id);
    }

    /**
     * KD2 收口:请求体缺失/不可解析、路径 id 非数字等非法输入也走 R 包装的 400,
     * 不透出 Spring 默认错误体。仅此控制器生效,不影响 DemoController。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<R<Void>> invalidRequest() {
        return ResponseEntity.badRequest()
                .body(R.fail(HttpStatus.BAD_REQUEST.value(), "invalid request: expected readable JSON body and numeric id"));
    }

    private static <T> ResponseEntity<R<T>> notFound(long id) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.<T>fail(HttpStatus.NOT_FOUND.value(), "note not found: " + id));
    }
}
