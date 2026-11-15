# 清理磁盘

```bash
# 查看各目录占用
du -sh /var/* | sort -h

# 清理过期日志
find /var/log -name '*.log' -mtime +30 -delete
```

# 校验结果

确认磁盘水位下降。