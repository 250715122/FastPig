package com.gt.kb.repo;

import com.gt.kb.model.BlockMeta;
import com.gt.kb.model.BlockRef;
import com.gt.kb.model.KnowledgeBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockCatalogTest {

    @TempDir
    Path tempDir;

    private BlockCatalog catalog;

    @BeforeEach
    public void setUp() {
        catalog = new BlockCatalog(tempDir.resolve("catalog.db").toString());
    }

    @AfterEach
    public void tearDown() {
        catalog.close();
    }

    private static KnowledgeBlock block(String id, String noteKey, String title, int startLine) {
        return new KnowledgeBlock(id, noteKey, title, 1, List.of(noteKey, title), "正文",
                startLine, startLine + 5, null, BlockMeta.EMPTY);
    }

    private static List<String> ids(List<BlockRef> refs) {
        return refs.stream().map(BlockRef::blockId).toList();
    }

    @Test
    public void emptyCatalogReportsEmpty() {
        assertTrue(catalog.isEmpty());
        catalog.replaceNote("mysql", List.of(block("ku_aaaaaaaa", "mysql", "索引", 1)));
        assertFalse(catalog.isEmpty());
    }

    @Test
    public void findNoteKeyResolvesBlock() {
        catalog.replaceNote("mysql", List.of(block("ku_aaaaaaaa", "mysql", "索引", 1)));
        assertEquals(Optional.of("mysql"), catalog.findNoteKey("ku_aaaaaaaa"));
        assertEquals(Optional.empty(), catalog.findNoteKey("ku_bbbbbbbb"));
    }

    @Test
    public void replaceNoteIsIdempotent() {
        List<KnowledgeBlock> blocks = List.of(
                block("ku_aaaaaaaa", "mysql", "索引", 1),
                block("ku_bbbbbbbb", "mysql", "慢查询", 10));
        catalog.replaceNote("mysql", blocks);
        catalog.replaceNote("mysql", blocks);
        assertEquals(List.of("ku_aaaaaaaa", "ku_bbbbbbbb"), ids(catalog.searchByTitlePrefix("mysql", "", 50)));
    }

    @Test
    public void replaceNoteDropsBlocksThatDisappeared() {
        catalog.replaceNote("mysql", List.of(
                block("ku_aaaaaaaa", "mysql", "索引", 1),
                block("ku_bbbbbbbb", "mysql", "慢查询", 10)));
        catalog.replaceNote("mysql", List.of(block("ku_bbbbbbbb", "mysql", "慢查询", 3)));

        assertEquals(Optional.empty(), catalog.findNoteKey("ku_aaaaaaaa"));
        assertEquals(List.of("ku_bbbbbbbb"), ids(catalog.searchByTitlePrefix("mysql", "", 50)));
    }

    @Test
    public void replaceNoteWithEmptyListClearsRows() {
        catalog.replaceNote("mysql", List.of(block("ku_aaaaaaaa", "mysql", "索引", 1)));
        catalog.replaceNote("mysql", List.of());
        assertTrue(catalog.isEmpty());
    }

    @Test
    public void deleteNoteOnlyTouchesThatNote() {
        catalog.replaceNote("mysql", List.of(block("ku_aaaaaaaa", "mysql", "索引", 1)));
        catalog.replaceNote("redis", List.of(block("ku_cccccccc", "redis", "持久化", 1)));
        catalog.deleteNote("mysql");

        assertEquals(Optional.empty(), catalog.findNoteKey("ku_aaaaaaaa"));
        assertEquals(Optional.of("redis"), catalog.findNoteKey("ku_cccccccc"));
    }

    @Test
    public void prefixMatchesBothInitialsAndFullPinyin() {
        catalog.replaceNote("mysql", List.of(block("ku_aaaaaaaa", "mysql", "索引", 1)));
        assertEquals(List.of("ku_aaaaaaaa"), ids(catalog.searchByTitlePrefix("mysql", "sy", 10)));
        assertEquals(List.of("ku_aaaaaaaa"), ids(catalog.searchByTitlePrefix("mysql", "suoyin", 10)));
        assertEquals(List.of("ku_aaaaaaaa"), ids(catalog.searchByTitlePrefix("mysql", "suo", 10)));
        assertTrue(catalog.searchByTitlePrefix("mysql", "yz", 10).isEmpty());
    }

    @Test
    public void prefixIsCaseInsensitiveAndTrimmed() {
        catalog.replaceNote("mysql", List.of(block("ku_aaaaaaaa", "mysql", "MySQL Tuning", 1)));
        assertEquals(List.of("ku_aaaaaaaa"), ids(catalog.searchByTitlePrefix("mysql", " MT ", 10)));
        assertEquals(List.of("ku_aaaaaaaa"), ids(catalog.searchByTitlePrefix("mysql", "mysqlt", 10)));
    }

    @Test
    public void prefixCanBeScopedToOneNoteOrSpanAll() {
        catalog.replaceNote("mysql", List.of(block("ku_aaaaaaaa", "mysql", "索引", 1)));
        catalog.replaceNote("redis", List.of(block("ku_cccccccc", "redis", "索引结构", 1)));

        assertEquals(List.of("ku_aaaaaaaa"), ids(catalog.searchByTitlePrefix("mysql", "sy", 10)));
        assertEquals(List.of("ku_aaaaaaaa", "ku_cccccccc"), ids(catalog.searchByTitlePrefix(null, "sy", 10)));
        assertEquals(List.of("ku_aaaaaaaa", "ku_cccccccc"), ids(catalog.searchByTitlePrefix("", "sy", 10)));
    }

    @Test
    public void likeWildcardsInPrefixAreLiteral() {
        catalog.replaceNote("mysql", List.of(block("ku_aaaaaaaa", "mysql", "索引", 1)));
        assertTrue(catalog.searchByTitlePrefix("mysql", "%", 10).isEmpty());
        assertTrue(catalog.searchByTitlePrefix("mysql", "_", 10).isEmpty());
        assertTrue(catalog.searchByTitlePrefix("mysql", "\\", 10).isEmpty());
        assertEquals(List.of("ku_aaaaaaaa"), ids(catalog.searchByTitlePrefix("mysql", "sy", 10)));
    }

    @Test
    public void limitIsRespectedAndOrderIsStable() {
        catalog.replaceNote("mysql", List.of(
                block("ku_bbbbbbbb", "mysql", "慢查询", 20),
                block("ku_aaaaaaaa", "mysql", "索引", 1)));
        List<BlockRef> all = catalog.searchByTitlePrefix("mysql", "", 50);
        assertEquals(List.of("ku_aaaaaaaa", "ku_bbbbbbbb"), ids(all));
        assertEquals(List.of("ku_aaaaaaaa"), ids(catalog.searchByTitlePrefix("mysql", "", 1)));
    }

    @Test
    public void untitledBlocksAreStillListed() {
        catalog.replaceNote("mysql", List.of(block("ku_aaaaaaaa", "mysql", "", 1)));
        assertEquals(List.of("ku_aaaaaaaa"), ids(catalog.searchByTitlePrefix("mysql", "", 10)));
        assertTrue(catalog.searchByTitlePrefix("mysql", "sy", 10).isEmpty());
    }

    @Test
    public void pathIsStoredAsEscapedJson() throws Exception {
        KnowledgeBlock b = new KnowledgeBlock("ku_aaaaaaaa", "mysql", "索引", 1,
                List.of("My\"SQL", "a\\b", "线\n程"), "正文", 1, 5, null, BlockMeta.EMPTY);
        catalog.replaceNote("mysql", List.of(b));

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("catalog.db"));
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT path FROM kb_blocks WHERE block_id = 'ku_aaaaaaaa'")) {
            assertTrue(rs.next());
            assertEquals("[\"My\\\"SQL\",\"a\\\\b\",\"线\\n程\"]", rs.getString(1));
        }
    }

    @Test
    public void governanceColumnsComeFromBlockMeta() throws Exception {
        BlockMeta meta = new BlockMeta("命令", BlockMeta.STATUS_VERIFIED, "ku_bbbbbbbb", "生产", null, 1234L);
        KnowledgeBlock b = new KnowledgeBlock("ku_aaaaaaaa", "mysql", "索引", 1,
                List.of("索引"), "正文", 1, 5, null, meta);
        catalog.replaceNote("mysql", List.of(b));

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("catalog.db"));
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT type, status, scope, superseded_by, updated_at, level,"
                     + " start_line, end_line, content_hash FROM kb_blocks")) {
            assertTrue(rs.next());
            assertEquals("命令", rs.getString(1));
            assertEquals("verified", rs.getString(2));
            assertEquals("生产", rs.getString(3));
            assertEquals("ku_bbbbbbbb", rs.getString(4));
            assertEquals(1234L, rs.getLong(5));
            assertEquals(1, rs.getInt(6));
            assertEquals(1, rs.getInt(7));
            assertEquals(5, rs.getInt(8));
            assertEquals(b.contentHash(), rs.getString(9));
        }
    }
}