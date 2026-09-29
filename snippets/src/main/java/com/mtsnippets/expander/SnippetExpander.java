package com.mtsnippets.expander;

import android.graphics.drawable.Drawable;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;

/**
 * 编辑动作的公共实现：缩写展开 / 环绕包裹 / 批量注释切换。
 */
public final class SnippetExpander {

    private SnippetExpander() {
    }

    /** 缩写展开：优先取选中文本，否则取光标前的单词；按分组范围过滤 */
    public static void expandAt(PluginUI pluginUI, PluginContext context, TextEditor editor) {
        CharSequence text = editor.getBufferedText();
        int rangeStart;
        int rangeEnd;
        String word;

        if (editor.hasTextSelected()) {
            rangeStart = editor.getSelectionStart();
            rangeEnd = editor.getSelectionEnd();
            String selected = editor.getSelectedText();
            int lead = 0;
            while (lead < selected.length() && isWs(selected.charAt(lead))) {
                lead++;
            }
            int trail = 0;
            while (trail < selected.length() - lead && isWs(selected.charAt(selected.length() - 1 - trail))) {
                trail++;
            }
            rangeStart += lead;
            rangeEnd -= trail;
            word = selected.substring(lead, selected.length() - trail);
        } else {
            rangeEnd = editor.getSelectionStart();
            rangeStart = SnippetEngine.wordStartBefore(text, rangeEnd);
            word = text.subSequence(rangeStart, rangeEnd).toString();
        }

        if (word.isEmpty()) {
            pluginUI.showToast("光标前没有可展开的缩写");
            return;
        }

        String fileName = editor.getFileName();
        SnippetEngine.MatchResult match = SnippetEngine.matchSnippet(context, word, fileName);
        if (match == null) {
            pluginUI.showToast("未知缩写「" + word + "」，可在插件设置中查看各分组");
            return;
        }
        if (!match.applicable) {
            pluginUI.showToast("「" + word + "」在分组「" + match.groupName + "」中，仅适用于 "
                    + match.scopeDesc + "（当前：" + (fileName.isEmpty() ? "无类型文件" : fileName) + "）");
            return;
        }

        String indent = SnippetEngine.indentAt(text, rangeStart);
        SnippetEngine.Expansion expansion = SnippetEngine.expand(match.template, indent, null);

        editor.pushSelectionToUndoBuffer();
        editor.replaceText(rangeStart, rangeEnd, expansion.text);

        int cursorPos = expansion.cursorOffset < 0
                ? rangeStart + expansion.text.length()
                : rangeStart + expansion.cursorOffset;
        editor.setSelection(cursorPos);
        editor.showCursor();
        editor.ensureSelectionVisible();
        pluginUI.showToast("已展开 " + word);
    }

    /** 环绕包裹：用模板包住选中内容 */
    public static void surroundWith(PluginUI pluginUI, TextEditor editor, String name, String template) {
        if (!editor.hasTextSelected()) {
            pluginUI.showToast("请先选中要包裹的代码");
            return;
        }
        CharSequence text = editor.getBufferedText();
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        String selection = editor.getSelectedText();

        String baseIndent = SnippetEngine.indentAt(text, selStart);
        SnippetEngine.Expansion expansion = SnippetEngine.expand(template, baseIndent, selection);

        editor.pushSelectionToUndoBuffer();
        editor.replaceText(selStart, selEnd, expansion.text);

        int cursorPos = expansion.cursorOffset < 0
                ? selStart + expansion.text.length()
                : selStart + expansion.cursorOffset;
        editor.setSelection(cursorPos);
        editor.showCursor();
        editor.ensureSelectionVisible();
        pluginUI.showToast("已用「" + name + "」包裹");
    }

    /** 批量注释切换：整块对齐 + 按文件类型选择注释符 */
    public static void toggleComments(PluginUI pluginUI, TextEditor editor) {
        if (!editor.hasTextSelected()) {
            pluginUI.showToast("请先选中要注释的代码");
            return;
        }
        CharSequence text = editor.getBufferedText();
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();

        // 扩展到整行
        int start = selStart;
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }
        int end = selEnd;
        if (end > start && text.charAt(end - 1) == '\n') {
            end--;
        }
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }

        String block = text.subSequence(start, end).toString();
        int style = CommentOps.styleFor(editor.getFileName());
        String result = CommentOps.toggle(block, style);
        int changed = countNonBlank(block);

        editor.pushSelectionToUndoBuffer();
        editor.replaceText(start, end, result);
        editor.setSelection(start, start + result.length());
        boolean commented = CommentOps.isCommented(result, style);
        pluginUI.showToast(commented ? "已注释（" + changed + " 行）" : "已取消注释（" + changed + " 行）");
    }

    private static int countNonBlank(String block) {
        int n = 0;
        for (String line : block.split("\n", -1)) {
            if (!line.trim().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** 安全取图标：首选名不可用时回退到已验证可用的图标 */
    public static Drawable icon(String preferred, String fallback) {
        try {
            Drawable d = bin.mt.plugin.api.drawable.MaterialIcons.get(preferred);
            if (d != null) {
                return d;
            }
        } catch (Throwable ignored) {
        }
        return bin.mt.plugin.api.drawable.MaterialIcons.get(fallback);
    }

    private static boolean isWs(char c) {
        return c == ' ' || c == '\t';
    }
}
