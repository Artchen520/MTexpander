package com.mtsnippets.expander;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.editor.BaseTextEditorFloatingMenu;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;

/**
 * 选中文字时的「展开片段」入口：把选中内容当作缩写词展开。
 */
public class SnippetFloatingMenu extends BaseTextEditorFloatingMenu {

    @NonNull
    @Override
    public String name() {
        return "展开片段";
    }

    @NonNull
    @Override
    public Drawable icon() {
        return SnippetExpander.icon("text_snippet", "code");
    }

    @Override
    public boolean checkVisible(@NonNull TextEditor editor) {
        return editor.hasTextSelected();
    }

    @Override
    public void onMenuClick(@NonNull PluginUI pluginUI, @NonNull TextEditor editor) {
        SnippetExpander.expandAt(pluginUI, getContext(), editor);
    }
}
