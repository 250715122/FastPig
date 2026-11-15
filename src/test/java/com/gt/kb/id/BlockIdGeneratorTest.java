package com.gt.kb.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockIdGeneratorTest {

    @Test
    public void generatesValidModelIds() {
        for (int i = 0; i < 200; i++) {
            String id = BlockIdGenerator.next();
            assertTrue(BlockIdCodec.isValidId(id), id);
            assertEquals(BlockIdCodec.MODEL_PREFIX.length() + BlockIdCodec.ID_LENGTH, id.length());
        }
    }

    @Test
    public void idsDoNotCollideInPractice() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            assertTrue(seen.add(BlockIdGenerator.next()), "5000 次生成内出现碰撞");
        }
    }

    @Test
    public void retriesUntilIdIsFree() {
        AtomicInteger calls = new AtomicInteger();
        String id = BlockIdGenerator.next(taken -> calls.incrementAndGet() <= 3);
        assertTrue(BlockIdCodec.isValidId(id));
        assertEquals(4, calls.get());
    }

    @Test
    public void givesUpWhenEveryIdIsTaken() {
        assertThrows(IllegalStateException.class, () -> BlockIdGenerator.next(taken -> true));
    }

    @Test
    public void skipsIdsAlreadyUsedInFile() {
        Set<String> used = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String id = BlockIdGenerator.next(used::contains);
            assertTrue(used.add(id));
        }
        assertEquals(100, used.size());
    }
}