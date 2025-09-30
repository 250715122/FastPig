package com.gt;

/**
 * 测试数据库同步功能
 */
public class TestDbSync {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("FastPig 数据库同步功能测试");
        System.out.println("========================================");
        System.out.println();
        
        // 测试1: 初始化服务
        System.out.println("[测试1] 初始化同步服务...");
        DbSyncService service = DbSyncService.getInstance();
        System.out.println("  状态: " + (service.isEnabled() ? "已启用" : "未启用（需配置NUTSTORE_DIR）"));
        System.out.println();
        
        // 测试2: 启动时拉取
        System.out.println("[测试2] 测试启动时拉取...");
        boolean pullResult = service.syncFromCloudOnStart();
        System.out.println("  结果: " + (pullResult ? "成功拉取" : "跳过或失败"));
        System.out.println();
        
        // 测试3: 手动同步
        System.out.println("[测试3] 测试手动同步到云端...");
        boolean pushResult = service.syncToCloud();
        System.out.println("  结果: " + (pushResult ? "成功上传" : "跳过或失败"));
        System.out.println();
        
        System.out.println("========================================");
        System.out.println("测试完成！");
        System.out.println("========================================");
        System.out.println();
        System.out.println("配置说明：");
        System.out.println("  - 设置环境变量: set NUTSTORE_DIR=<坚果云目录>");
        System.out.println("  - 或启动参数: java -Dnutstore.dir=<坚果云目录> ...");
        System.out.println();
    }
}
