package com.gt.cloud;

/**
 * 云端文件信息
 * 用于描述云存储中的文件或目录的元信息
 */
public class CloudFileInfo {
    private String path;
    private String name;
    private long size;
    private long lastModified;
    private boolean directory;

    public CloudFileInfo() {}

    public CloudFileInfo(String path, String name, long size, long lastModified, boolean directory) {
        this.path = path;
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.directory = directory;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    @Override
    public String toString() {
        return "CloudFileInfo{" +
                "path='" + path + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", lastModified=" + lastModified +
                ", directory=" + directory +
                '}';
    }
}

