package com.gt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时自动上传调度器（单例）
 * 根据用户配置的间隔，定期调用 syncToCloud 将本地变更上传到云端。
 */
public class AutoUploadScheduler {

    private static final Logger logger = LogManager.getLogger(AutoUploadScheduler.class);
    private static volatile AutoUploadScheduler INSTANCE;

    private Timer timer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int intervalMinutes;

    private AutoUploadScheduler() {}

    public static AutoUploadScheduler getInstance() {
        if (INSTANCE == null) {
            synchronized (AutoUploadScheduler.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AutoUploadScheduler();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 启动定时上传。读取配置中的间隔（分钟），0 表示禁用。
     */
    public synchronized void start() {
        int minutes = AppConfig.getInstance().getInt(AppConfig.SYNC_AUTO_UPLOAD_INTERVAL, 30);
        startWithInterval(minutes);
    }

    /**
     * 以指定间隔（分钟）启动定时上传，0 表示禁用。
     */
    public synchronized void startWithInterval(int minutes) {
        stop();
        this.intervalMinutes = minutes;

        if (minutes <= 0) {
            logger.info("[AutoUpload] 定时自动上传已禁用 (interval={})", minutes);
            return;
        }

        long periodMs = (long) minutes * 60 * 1000;
        timer = new Timer("AutoUpload-Timer", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                doAutoUpload();
            }
        }, periodMs, periodMs);

        running.set(true);
        logger.info("[AutoUpload] 定时自动上传已启动，间隔 {} 分钟", minutes);
    }

    /**
     * 停止定时上传
     */
    public synchronized void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        running.set(false);
    }

    /**
     * 重启（配置变更时调用）
     */
    public synchronized void restart() {
        start();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    private void doAutoUpload() {
        try {
            logger.info("[AutoUpload] 定时自动上传触发");

            UnifiedNoteAppFrame af = UnifiedNoteAppFrame.getActiveInstance();
            if (af != null) {
                af.updateStatusLeft("自动上传中…");
            }

            boolean ok = DbSyncService.getInstance().syncToCloud();

            if (af != null) {
                af.updateStatusLeft(ok ? "自动上传完成" : "自动上传失败");
            }
            logger.info("[AutoUpload] 定时自动上传完成，结果: {}", ok ? "成功" : "失败");
        } catch (Exception e) {
            logger.error("[AutoUpload] 定时自动上传异常: {}", e.getMessage(), e);
        }
    }
}
