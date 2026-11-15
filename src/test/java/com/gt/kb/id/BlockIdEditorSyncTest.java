package com.gt.kb.id;

import com.gt.kb.chunk.ChunkProfile;
import org.junit.jupiter.api.Test;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.undo.UndoManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保存补 ID 后回填编辑区。纯内存文档，不碰任何文件。
 *
 * 核心不变量：回填后编辑区正文必须与落盘正文逐字相同，否则下次保存又会重新分配一批 ID。
 */
public class BlockIdEditorSyncTest {

    private static final ChunkProfile DEFAULT = new ChunkProfile(false, 1500, 800, 100, 30);
    private static final String FIRST = "mysql MySQL 运维";

    private static final List<String> BODIES = List.of(
            "# 部署步骤\n\n正文一\n\n# 回滚步骤\n\n正文二\n",
            "引言文字。\n\n# 标题\n\n正文。\n",
            "# 只有标题没有正文",
            "只有一行且不以换行结尾",
            "# 甲\r\n\r\n# 乙",
            "# 标题\r\n\r\n正文甲\n\n## 第二节\r正文乙\r\n",
            "# 甲\n<!-- ku:abcd2345 -->\n\n正文甲\n\n# 乙\n\n正文乙\n",
            "");

    private static JTextArea area(String body) {
        JTextArea area = new JTextArea();
        // 真实保存跑在 EDT 上，光标由 DefaultCaret 自动顺延；测试线程不是 EDT，
        // 默认的 UPDATE_WHEN_ON_EDT 策略会直接跳过顺延，必须显式改成 ALWAYS_UPDATE
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        area.setText(FIRST + "\n" + body);
        return area;
    }

    private static String lineText(JTextArea area, int line) throws BadLocationException {
        String text = area.getText().substring(area.getLineStartOffset(line), area.getLineEndOffset(line));
        if (text.endsWith("\r\n")) {
            return text.substring(0, text.length() - 2);
        }
        return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
    }

    private static String bodyOf(JTextArea area) {
        String text = area.getText();
        int nl = text.indexOf('\n');
        return nl < 0 ? "" : text.substring(nl + 1);
    }

    private static BlockIdAssigner.Result assign(String body) {
        return BlockIdAssigner.assign("mysql", "MySQL 运维", body, DEFAULT);
    }

    @Test
    public void editorBodyMatchesSavedBody() throws Exception {
        for (String body : BODIES) {
            JTextArea area = area(body);
            BlockIdAssigner.Result result = assign(bodyOf(area));
            BlockIdEditorSync.apply(area, result);
            assertEquals(FIRST + "\n" + result.body(), area.getText(), "回填后与落盘正文不一致: " + body);
        }
    }

    @Test
    public void secondSaveAfterSyncAssignsNothing() throws Exception {
        for (String body : BODIES) {
            JTextArea area = area(body);
            BlockIdEditorSync.apply(area, assign(bodyOf(area)));
            assertFalse(assign(bodyOf(area)).changed(), "回填后再保存仍会补 ID: " + body);
        }
    }

    @Test
    public void caretStaysOnTheSameText() throws Exception {
        for (String body : BODIES) {
            JTextArea area = area(body);
            String text = area.getText();
            for (int offset : new int[]{0, FIRST.length(), FIRST.length() + 1, text.length() / 2, text.length()}) {
                area.setText(text);
                area.setCaretPosition(offset);
                int line = area.getLineOfOffset(offset);
                int column = offset - area.getLineStartOffset(line);
                String lineText = lineText(area, line);

                BlockIdEditorSync.apply(area, assign(bodyOf(area)));

                int caret = area.getCaretPosition();
                if (offset == text.length()) {
                    assertEquals(area.getText().length(), caret, "文末光标没有跟到新的文末: " + body);
                    continue;
                }
                int newLine = area.getLineOfOffset(caret);
                assertEquals(lineText, lineText(area, newLine), "光标所在行被换了: " + body + " offset=" + offset);
                assertEquals(column, caret - area.getLineStartOffset(newLine), "光标列号被推偏: " + body);
            }
        }
    }

    @Test
    public void selectionStaysOnTheSameText() throws Exception {
        String body = "# 部署步骤\n\n正文一\n\n# 回滚步骤\n\n正文二\n";
        JTextArea area = area(body);
        String text = area.getText();
        int start = text.indexOf("正文一");
        int end = start + "正文一".length();
        area.select(start, end);
        BlockIdEditorSync.apply(area, assign(bodyOf(area)));
        assertEquals("正文一", area.getSelectedText());
    }

    @Test
    public void insertionsAreIndividuallyUndoable() throws Exception {
        String body = "# 部署步骤\n\n正文一\n\n# 回滚步骤\n\n正文二\n";
        JTextArea area = area(body);
        UndoManager undo = new UndoManager();
        area.getDocument().addUndoableEditListener(e -> undo.addEdit(e.getEdit()));

        BlockIdAssigner.Result result = assign(bodyOf(area));
        BlockIdEditorSync.apply(area, result);
        assertEquals(FIRST + "\n" + result.body(), area.getText());

        int edits = 0;
        while (undo.canUndo()) {
            undo.undo();
            edits++;
        }
        assertEquals(result.insertions().size(), edits, "每行 ID 应该是一条独立的插入记录");
        assertEquals(FIRST + "\n" + body, area.getText(), "撤销后没有回到保存前的原文");
    }

    @Test
    public void emptyResultLeavesDocumentAlone() throws Exception {
        JTextArea area = area("# 甲\n<!-- ku:abcd2345 -->\n\n正文甲\n");
        String before = area.getText();
        BlockIdEditorSync.apply(area, assign(bodyOf(area)));
        assertEquals(before, area.getText());
        assertTrue(area.getText().contains("ku:abcd2345"));
    }
}