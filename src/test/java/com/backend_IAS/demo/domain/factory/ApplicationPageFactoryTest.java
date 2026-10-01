package com.backend_IAS.demo.domain.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationPageFactoryTest {
    @Test
    void shouldCalculatePageCountWithoutOverflowOrFloatingPointRounding() {
        var page = ApplicationPageFactory.create(List.of(), Integer.MAX_VALUE, 2, Long.MAX_VALUE);
        assertEquals(4611686018427387904L, page.getTotalPages());
        assertFalse(page.isFirst());
        assertFalse(page.isLast());
    }

    @Test
    void shouldMarkEmptyAndOutOfRangePagesAsLast() {
        var empty = ApplicationPageFactory.create(List.of(), 0, 20, 0);
        assertEquals(0, empty.getTotalPages());
        assertTrue(empty.isFirst());
        assertTrue(empty.isLast());
        var beyondLast = ApplicationPageFactory.create(List.of(), 3, 20, 40);
        assertEquals(2, beyondLast.getTotalPages());
        assertFalse(beyondLast.isFirst());
        assertTrue(beyondLast.isLast());
    }
}
