package com.mtsnippets.expander;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import bin.mt.plugin.api.drawable.MaterialIcons;
import bin.mt.plugin.api.editor.BaseTextEditorFloatingMenu;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;

/**
 * 选中文字时的「批量注释」入口：按行切换 // 注释。
 */
public class CommentFloatingMenu extends BaseTextEditorFloatingMenu {

    @NonNull
    @Override
    public String name() {
        return "批量注释";
    }

    @NonNull
    @Override
    public Drawable icon() {
        return SnippetExpander.icon("comment", "text_snippet");
    }

    @Override
    public boolean checkVisible(@NonNull TextEditor editor) {
        return editor.hasTextSelected();
    }

    @Override
    public void onMenuClick(@NonNull PluginUI pluginUI, @NonNull TextEditor editor) {
        SnippetExpander.toggleComments(pluginUI, editor);
    }
}
