package com.octopus.demo.controller;

import com.octopus.demo.common.bean.PageResultBean;
import com.octopus.demo.common.bean.R;
import com.octopus.demo.common.util.PageUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Paginated item endpoint (spec KD3): in-memory seed data, no DB.
 * Pagination itself is delegated to java-common's {@link PageUtil} —
 * the cross-repo reuse this demo exists to prove.
 */
@RestController
public class ItemController {

    static final int SEED_COUNT = 25;

    private final List<Item> items = IntStream.rangeClosed(1, SEED_COUNT)
            .mapToObj(i -> new Item(i, "item-" + i))
            .toList();

    @GetMapping("/api/items")
    public R<PageResultBean<Item>> list(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageUtil.paginate(items, page, size));
    }

    /** Seed entity: fixed, deterministic, demo-only. */
    public record Item(long id, String name) {
    }
}
