package com.gt.crypto;

import com.gt.NoteDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 私密笔记的启用、解锁与落盘前封装。
 *
 * 约定：NoteDto.bodyMd 在内存中始终是明文，只有在写文件那一刻才临时换成密文。
 * 这样编辑器、预览、页内搜索全部无需感知加密。
 */
public final class NoteEncryptionService {

    /** 与 NoteFileStorage 的清理逻辑保持一致的图片引用格式 */
    private static final Pattern ASSET_REF_PATTERN = Pattern.compile("!\\[.*?\\]\\(assets/([^)]+)\\)");

    private NoteEncryptionService() {}

    /**
     * 把一篇普通笔记变成私密笔记：生成 DEK，并用笔记密码与主密码各包裹一份。
     * 主密码那份是"忘记密码"的唯一恢复通道，未设置主密码时不允许启用加密。
     */
    public static void enable(NoteDto note, char[] notePassword, char[] masterPassword) throws Exception {
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalStateException("启用加密前必须先设置主密码，否则忘记密码将无法恢复");
        }

        byte[] dek = NoteCrypto.newDek();
        NoteCrypto.WrappedDek byPwd = NoteCrypto.wrapDek(dek, notePassword);
        NoteCrypto.WrappedDek byMaster = NoteCrypto.wrapDek(dek, masterPassword);

        note.encrypted = true;
        note.pwdSalt = byPwd.saltB64;
        note.pwdIv = byPwd.ivB64;
        note.pwdWrappedDek = byPwd.cipherB64;
        note.masterSalt = byMaster.saltB64;
        note.masterIv = byMaster.ivB64;
        note.masterWrappedDek = byMaster.cipherB64;
        note.runtimeDek = dek;
        note.locked = false;
    }

    /**
     * 用笔记密码或主密码解锁：解开 DEK 并把 bodyMd 从密文还原成明文。
     *
     * @param useMaster true 表示走主密码恢复通道
     */
    public static void unlock(NoteDto note, char[] password, boolean useMaster) throws Exception {
        if (note == null || !note.encrypted) {
            return;
        }

        NoteCrypto.WrappedDek wrapped = useMaster
                ? new NoteCrypto.WrappedDek(note.masterSalt, note.masterIv, note.masterWrappedDek)
                : new NoteCrypto.WrappedDek(note.pwdSalt, note.pwdIv, note.pwdWrappedDek);

        if (!wrapped.isComplete()) {
            throw new IllegalStateException(useMaster
                    ? "这篇笔记没有主密码恢复信息"
                    : "这篇笔记缺少密码校验信息");
        }

        byte[] dek = NoteCrypto.unwrapDek(wrapped, password);
        note.bodyMd = NoteCrypto.decryptBody(note.bodyMd, note.cipherIv, dek);
        note.runtimeDek = dek;
        note.locked = false;
    }

    /**
     * 落盘前把明文正文换成密文，并把图片引用清单写进 DTO。
     *
     * 必须先抽取图片清单：正文加密后 NoteFileStorage 再也扫不出引用关系，
     * 只能靠这份明文清单判断哪些图片还在用，否则 assets 下的图片会被全部删除。
     *
     * @return 原始明文，调用方写完文件后需要还原回 DTO
     */
    public static String sealForSave(NoteDto note) throws Exception {
        String plaintext = note.bodyMd == null ? "" : note.bodyMd;

        if (note.runtimeDek == null) {
            throw new IllegalStateException("笔记处于锁定态，拒绝以空内容覆盖密文");
        }

        note.assets = extractAssetRefs(plaintext);

        NoteCrypto.EncryptedBody encrypted = NoteCrypto.encryptBody(plaintext, note.runtimeDek);
        note.cipherIv = encrypted.ivB64;
        note.bodyMd = encrypted.cipherB64;

        return plaintext;
    }

    /** 修改笔记密码：只重新包裹 DEK，不必重写整篇密文 */
    public static void changePassword(NoteDto note, char[] newPassword) throws Exception {
        if (note.runtimeDek == null) {
            throw new IllegalStateException("请先解锁笔记再修改密码");
        }
        NoteCrypto.WrappedDek byPwd = NoteCrypto.wrapDek(note.runtimeDek, newPassword);
        note.pwdSalt = byPwd.saltB64;
        note.pwdIv = byPwd.ivB64;
        note.pwdWrappedDek = byPwd.cipherB64;
    }

    /** 主密码变更后，需要用新主密码重新包裹这篇笔记的 DEK */
    public static void rewrapWithMaster(NoteDto note, char[] newMasterPassword) throws Exception {
        if (note.runtimeDek == null) {
            throw new IllegalStateException("请先解锁笔记");
        }
        NoteCrypto.WrappedDek byMaster = NoteCrypto.wrapDek(note.runtimeDek, newMasterPassword);
        note.masterSalt = byMaster.saltB64;
        note.masterIv = byMaster.ivB64;
        note.masterWrappedDek = byMaster.cipherB64;
    }

    /** 取消加密。调用前笔记必须已解锁，此时 bodyMd 是明文，直接落盘即可。 */
    public static void disable(NoteDto note) {
        NoteCrypto.wipe(note.runtimeDek);
        note.runtimeDek = null;
        note.encrypted = false;
        note.locked = false;
        note.cipherIv = null;
        note.pwdSalt = null;
        note.pwdIv = null;
        note.pwdWrappedDek = null;
        note.masterSalt = null;
        note.masterIv = null;
        note.masterWrappedDek = null;
        note.assets = null;
    }

    /** 关闭笔记时清掉内存中的明文 DEK */
    public static void lock(NoteDto note) {
        if (note == null) return;
        NoteCrypto.wipe(note.runtimeDek);
        note.runtimeDek = null;
        if (note.encrypted) {
            note.locked = true;
        }
    }

    static List<String> extractAssetRefs(String markdown) {
        Set<String> refs = new LinkedHashSet<>();
        if (markdown != null && !markdown.isEmpty()) {
            Matcher m = ASSET_REF_PATTERN.matcher(markdown);
            while (m.find()) {
                refs.add(m.group(1));
            }
        }
        return new ArrayList<>(refs);
    }
}
