package com.gt.cloud;

import java.util.List;

/**
 * 云存储提供者统一接口
 * 支持可插拔切换不同的云存储服务（坚果云、阿里云、腾讯云等）
 */
public interface CloudStorageProvider {

    /**
     * 上传文件到云端
     * @param remotePath 云端路径（相对于同步根目录）
     * @param data 文件内容
     * @return 是否成功
     */
    boolean upload(String remotePath, byte[] data);

    /**
     * 从云端下载文件
     * @param remotePath 云端路径
     * @return 文件内容，失败返回 null
     */
    byte[] download(String remotePath);

    /**
     * 删除云端文件
     * @param remotePath 云端路径
     * @return 是否成功
     */
    boolean delete(String remotePath);

    /**
     * 检查云端文件是否存在
     * @param remotePath 云端路径
     * @return 是否存在
     */
    boolean exists(String remotePath);

    /**
     * 列出云端目录下的文件（仅当前目录，不递归）
     * @param remoteDir 云端目录路径
     * @return 文件信息列表
     */
    List<CloudFileInfo> listFiles(String remoteDir);

    /**
     * 递归列出云端目录下的所有文件和子目录
     * @param remoteDir 云端目录路径
     * @return 所有文件和目录的完整列表（包含完整相对路径）
     */
    List<CloudFileInfo> listFilesRecursive(String remoteDir);

    /**
     * 在云端创建目录
     * @param remotePath 目录路径
     * @return 是否成功
     */
    boolean createDirectory(String remotePath);

    /**
     * 获取文件的元信息
     * @param remotePath 云端路径
     * @return 文件信息，不存在返回 null
     */
    CloudFileInfo getFileInfo(String remotePath);

    /**
     * 检查云存储是否已启用（配置正确）
     * @return 是否启用
     */
    boolean isEnabled();

    /**
     * 获取提供者名称
     * @return 提供者名称（如 "nutstore", "aliyun", "tencent"）
     */
    String getProviderName();

    /**
     * 获取同步根目录的完整 URL
     * @return 根目录 URL
     */
    String getSyncRootUrl();
}

