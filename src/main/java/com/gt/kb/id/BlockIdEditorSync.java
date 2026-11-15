package com.gt.kb.id;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.util.List;

/**
 * 把保存时补出的 ID 行插回编辑区。
 *
 * 必须回填：编辑区留着没有 ID 的旧文本，下次保存又会重新分配一批 ID，块 ID 永远稳定不下来。
 * 用 insertString 而不是 setText——只增不删，光标与选区由 Swing 的 Position 自动顺延，
 * 撤销栈里留下的也是一条条正常的插入记录，不会把用户此前的编辑历史冲掉。
 */
public final class BlockIdEditorSync {

    private BlockIdEditorSync() {
    }

    /**
     * @param area 编辑区，第 0 行是首行（key + desc），正文从第 1 行起
     * @param ids  {@link BlockIdAssigner#assign} 的结果，insertions 为空时什么都不做
     */
    public static void apply(JTextArea area, BlockIdAssigner.Result ids) throws BadLocationException {
        List<BlockIdAssigner.Result.Insertion> insertions = ids.insertions();
        Document doc = area.getDocument();
        // 从后往前插，前面的行号才不会被已插入的行推偏
        for (int i = insertions.size() - 1; i >= 0; i--) {
            BlockIdAssigner.Result.Insertion ins = insertions.get(i);
            int line = ins.line() + 1;
            int offset = line >= area.getLineCount() ? doc.getLength() : area.getLineStartOffset(line);
            doc.insertString(offset, ins.text(), null);
        }
    }
}