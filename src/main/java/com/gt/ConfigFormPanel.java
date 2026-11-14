package com.gt;

import com.gt.config.ConfigKeys;

import javax.swing.*;
import java.awt.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用配置面板：按 ConfigKeys 分类自动生成表单，新增配置项无需手写 UI。
 *
 * 数值项用带上下界的 Spinner，布尔项用开关，字符串项用文本框。
 * 保存时记录哪些 needsReindex 项发生了变化，供设置对话框决定是否提示重建索引。
 */
public class ConfigFormPanel extends JPanel {

    private final SettingsDialog dialog;
    private final AppConfig config;
    private final ConfigKeys.Category category;
    private final List<ConfigKeys> keys = new ArrayList<>();
    private final Map<ConfigKeys, JComponent> editors = new LinkedHashMap<>();
    private final Map<ConfigKeys, String> loadedValues = new LinkedHashMap<>();

    public ConfigFormPanel(SettingsDialog parent, ConfigKeys.Category category, String title) {
        this.dialog = parent;
        this.config = parent.getConfig();
        this.category = category;

        for (ConfigKeys k : ConfigKeys.values()) {
            if (k.category == category) {
                keys.add(k);
            }
        }

        setLayout(new BorderLayout());
        setBackground(UIColors.BG_PRIMARY);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));

        form.add(createTitle(title));
        form.add(Box.createVerticalStrut(16));

        if (keys.isEmpty()) {
            JLabel empty = new JLabel("该分类暂无可配置项");
            empty.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
            empty.setForeground(UIColors.TEXT_SECONDARY);
            empty.setAlignmentX(LEFT_ALIGNMENT);
            form.add(empty);
        }

        for (ConfigKeys k : keys) {
            form.add(createRow(k));
            form.add(Box.createVerticalStrut(14));
        }
        form.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    private JLabel createTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 18));
        label.setForeground(UIColors.TEXT_PRIMARY);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JPanel createRow(ConfigKeys k) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));

        JLabel nameLabel = new JLabel(k.label + (k.needsReindex ? "　（修改后需重建索引）" : ""));
        nameLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
        nameLabel.setForeground(UIColors.TEXT_PRIMARY);
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);
        row.add(nameLabel);

        JLabel hint = new JLabel(hintOf(k));
        hint.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
        hint.setForeground(UIColors.TEXT_SECONDARY);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        row.add(Box.createVerticalStrut(2));
        row.add(hint);
        row.add(Box.createVerticalStrut(4));

        JComponent editor = createEditor(k);
        editor.setAlignmentX(LEFT_ALIGNMENT);
        row.add(editor);

        editors.put(k, editor);
        return row;
    }

    private String hintOf(ConfigKeys k) {
        StringBuilder sb = new StringBuilder(k.key);
        if (k.isNumeric() && k.min != null && k.max != null) {
            sb.append("　范围 ").append(k.min).append(" ~ ").append(k.max);
        }
        sb.append("　默认 ").append(k.def);
        return sb.toString();
    }

    private JComponent createEditor(ConfigKeys k) {
        if (k.isBoolean()) {
            JCheckBox box = new JCheckBox();
            box.setOpaque(false);
            box.setMaximumSize(new Dimension(200, 26));
            return box;
        }
        if (k.isNumeric() && k.min != null && k.max != null) {
            JSpinner spinner = new JSpinner(spinnerModel(k));
            spinner.setMaximumSize(new Dimension(160, 30));
            spinner.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
            return spinner;
        }
        JTextField field = new JTextField(24);
        field.setMaximumSize(new Dimension(420, 30));
        field.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        field.setBackground(UIColors.BG_PRIMARY);
        field.setForeground(UIColors.TEXT_PRIMARY);
        field.setCaretColor(UIColors.TEXT_PRIMARY);
        return field;
    }

    private SpinnerNumberModel spinnerModel(ConfigKeys k) {
        if (k.def instanceof Long) {
            return new SpinnerNumberModel(config.getLong(k), k.min.longValue(), k.max.longValue(), 1L);
        }
        if (k.def instanceof Double) {
            return new SpinnerNumberModel(config.getDouble(k), k.min.doubleValue(), k.max.doubleValue(), 0.05);
        }
        return new SpinnerNumberModel(config.getInt(k), k.min.intValue(), k.max.intValue(), 1);
    }

    public void loadConfig() {
        loadedValues.clear();
        for (ConfigKeys k : keys) {
            String value = currentValue(k);
            loadedValues.put(k, value);
            JComponent editor = editors.get(k);
            if (editor instanceof JCheckBox box) {
                box.setSelected(Boolean.parseBoolean(value));
            } else if (editor instanceof JSpinner spinner) {
                spinner.setValue(numberOf(k, value));
            } else if (editor instanceof JTextField field) {
                field.setText(value);
            }
        }
    }

    /** 读取当前配置值的字符串形式，作为变更检测与回填的统一口径。 */
    private String currentValue(ConfigKeys k) {
        if (k.isBoolean()) {
            return String.valueOf(config.getBoolean(k));
        }
        if (k.def instanceof Long) {
            return String.valueOf(config.getLong(k));
        }
        if (k.def instanceof Double) {
            return String.valueOf(config.getDouble(k));
        }
        if (k.isNumeric()) {
            return String.valueOf(config.getInt(k));
        }
        return config.getString(k);
    }

    private Number numberOf(ConfigKeys k, String value) {
        if (k.def instanceof Long) {
            return Long.valueOf(value);
        }
        if (k.def instanceof Double) {
            return Double.valueOf(value);
        }
        return Integer.valueOf(value);
    }

    public boolean validateConfig() {
        for (ConfigKeys k : keys) {
            JComponent editor = editors.get(k);
            if (!(editor instanceof JSpinner spinner)) {
                continue;
            }
            try {
                spinner.commitEdit();
            } catch (ParseException e) {
                JOptionPane.showMessageDialog(dialog, k.label + " 不是有效数值",
                    "配置无效", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            if (spinner.getValue() instanceof Number n && !k.inRange(n.doubleValue())) {
                JOptionPane.showMessageDialog(dialog,
                    k.label + " 超出范围 " + k.min + " ~ " + k.max,
                    "配置无效", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        return true;
    }

    public void saveConfig() {
        for (ConfigKeys k : keys) {
            config.set(k, editorValue(k));
        }
    }

    private String editorValue(ConfigKeys k) {
        JComponent editor = editors.get(k);
        if (editor instanceof JCheckBox box) {
            return String.valueOf(box.isSelected());
        }
        if (editor instanceof JSpinner spinner) {
            return String.valueOf(spinner.getValue());
        }
        if (editor instanceof JTextField field) {
            return field.getText().trim();
        }
        return currentValue(k);
    }

    /** 本次编辑是否改动了需要重建索引的项。 */
    public boolean hasReindexChanges() {
        for (ConfigKeys k : keys) {
            if (k.needsReindex && !editorValue(k).equals(loadedValues.get(k))) {
                return true;
            }
        }
        return false;
    }

    /** 热键改动无法在运行中重新注册，需要提示重启。 */
    public boolean hasHotkeyChanges() {
        if (category != ConfigKeys.Category.HOTKEY) {
            return false;
        }
        for (ConfigKeys k : keys) {
            if (!editorValue(k).equals(loadedValues.get(k))) {
                return true;
            }
        }
        return false;
    }

    public void applyConfig() {
        // 这些项都在读取处实时取配置，保存即生效，无需额外动作
    }
}