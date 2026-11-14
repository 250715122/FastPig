package com.gt;

import com.gt.sync.SyncMetadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 验证 SyncMetadata JSON 解析不会因字段位于开头而丢失数值
 */
public class SyncMetadataParseTest {

    @Test
    public void testParseFieldsAtStart() {
        String json = "{"
                + "\"lastSyncTime\": 1700000000000,"
                + "\"files\": {"
                + "  \"note-1\": {\"lastModified\": 1700000000001, \"size\": 123, \"cloudModified\": 1700000000002, \"hash\": \"\"}"
                + " }"
                + "}";

        SyncMetadata meta = SyncMetadata.loadFromStringForTest(json);
        Assertions.assertEquals(1700000000000L, meta.getLastSyncTime());
        SyncMetadata.FileMetadata fm = meta.getFiles().get("note-1");
        Assertions.assertNotNull(fm);
        Assertions.assertEquals(1700000000001L, fm.lastModified);
        Assertions.assertEquals(123L, fm.size);
        Assertions.assertEquals(1700000000002L, fm.cloudModified);
    }
}

