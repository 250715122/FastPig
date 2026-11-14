package com.gt.sync;

import com.gt.cloud.CloudFileInfo;
import com.gt.cloud.CloudStorageProvider;
import com.gt.storage.NoteFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * _kb 配置目录的同步：它没有 note.md，不能走笔记的版本号通道。
 */
public class NoteFileSyncKbTest {

    private static final String REMOTE = "_kb/taxonomy.yaml";
    private static final long MINUTE = 60_000L;

    @TempDir
    Path tempDir;

    private NoteFileStorage storage;
    private FakeCloud cloud;
    private NoteFileSync sync;
    private long now;

    @BeforeEach
    public void setUp() throws Exception {
        storage = new NoteFileStorage(tempDir.toString());
        Files.createDirectories(storage.getNotesDir());
        cloud = new FakeCloud();
        sync = new NoteFileSync(storage, cloud);
        now = System.currentTimeMillis();
    }

    private Path kbFile() {
        return storage.getNotesDir().resolve("_kb").resolve("taxonomy.yaml");
    }

    private void writeLocal(String content, long modified) throws Exception {
        Path file = kbFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified));
    }

    @Test
    public void uploadsKbConfig() throws Exception {
        writeLocal("version: 1\n", now);

        assertTrue(sync.syncToCloud());
        assertEquals("version: 1\n", cloud.text(REMOTE));
    }

    @Test
    public void unchangedKbConfigIsNotUploadedAgain() throws Exception {
        writeLocal("version: 1\n", now);
        sync.syncToCloud();

        sync.syncToCloud();
        assertEquals(1, cloud.kbUploads);
    }

    @Test
    public void newerLocalKbConfigIsUploaded() throws Exception {
        writeLocal("version: 1\n", now);
        sync.syncToCloud();
        cloud.put(REMOTE, "version: 1\n", now - MINUTE);

        writeLocal("version: 2\n", now);
        sync.syncToCloud();
        assertEquals("version: 2\n", cloud.text(REMOTE));
    }

    @Test
    public void downloadsKbConfigWhenMissingLocally() throws Exception {
        cloud.put(REMOTE, "version: 7\n", now);

        assertTrue(sync.syncFromCloud());
        assertEquals("version: 7\n", Files.readString(kbFile()));
    }

    @Test
    public void newerCloudKbConfigOverwritesLocal() throws Exception {
        writeLocal("version: 1\n", now - MINUTE);
        cloud.put(REMOTE, "version: 7\n", now);

        sync.syncFromCloud();
        assertEquals("version: 7\n", Files.readString(kbFile()));
    }

    @Test
    public void newerLocalKbConfigSurvivesDownload() throws Exception {
        writeLocal("version: 9\n", now);
        cloud.put(REMOTE, "version: 7\n", now - MINUTE);

        sync.syncFromCloud();
        assertEquals("version: 9\n", Files.readString(kbFile()));
    }

    @Test
    public void kbDirIsNotTreatedAsNote() throws Exception {
        writeLocal("version: 1\n", now);
        sync.syncToCloud();

        // 笔记通道只处理带 note.md 的目录，_kb 不该被当成一篇笔记
        assertFalse(cloud.paths().contains("_kb/note.md"));
        assertTrue(cloud.paths().contains(REMOTE));
    }

    /** 内存版云盘，只实现测试用得到的语义。 */
    private static final class FakeCloud implements CloudStorageProvider {
        private final Map<String, byte[]> files = new LinkedHashMap<>();
        private final Map<String, Long> times = new LinkedHashMap<>();
        int kbUploads;

        void put(String path, String content, long modified) {
            files.put(path, content.getBytes(StandardCharsets.UTF_8));
            times.put(path, modified);
        }

        String text(String path) {
            byte[] data = files.get(path);
            return data == null ? null : new String(data, StandardCharsets.UTF_8);
        }

        List<String> paths() {
            return new ArrayList<>(files.keySet());
        }

        @Override public boolean upload(String remotePath, byte[] data) {
            if (remotePath.startsWith("_kb/")) {
                kbUploads++;
            }
            files.put(remotePath, data);
            times.put(remotePath, System.currentTimeMillis());
            return true;
        }

        @Override public byte[] download(String remotePath) {
            return files.get(remotePath);
        }

        @Override public boolean delete(String remotePath) {
            files.remove(remotePath);
            times.remove(remotePath);
            return true;
        }

        @Override public boolean exists(String remotePath) {
            return files.containsKey(remotePath);
        }

        @Override public List<CloudFileInfo> listFiles(String remoteDir) {
            List<CloudFileInfo> out = new ArrayList<>();
            String prefix = remoteDir.isEmpty() ? "" : remoteDir + "/";
            List<String> seen = new ArrayList<>();
            for (String path : files.keySet()) {
                if (!path.startsWith(prefix)) continue;
                String rest = path.substring(prefix.length());
                int slash = rest.indexOf('/');
                String name = slash < 0 ? rest : rest.substring(0, slash);
                if (seen.contains(name)) continue;
                seen.add(name);
                out.add(new CloudFileInfo(prefix + name, name, 0, times.getOrDefault(path, 0L), slash >= 0));
            }
            return out;
        }

        @Override public List<CloudFileInfo> listFilesRecursive(String remoteDir) {
            List<CloudFileInfo> out = new ArrayList<>();
            String prefix = remoteDir.isEmpty() ? "" : remoteDir + "/";
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                if (!e.getKey().startsWith(prefix)) continue;
                String name = e.getKey().substring(e.getKey().lastIndexOf('/') + 1);
                out.add(new CloudFileInfo(e.getKey(), name, e.getValue().length,
                        times.getOrDefault(e.getKey(), 0L), false));
            }
            return out;
        }

        @Override public boolean createDirectory(String remotePath) {
            return true;
        }

        @Override public CloudFileInfo getFileInfo(String remotePath) {
            return null;
        }

        @Override public boolean isEnabled() {
            return true;
        }

        @Override public String getProviderName() {
            return "fake";
        }

        @Override public String getSyncRootUrl() {
            return "memory://";
        }
    }
}