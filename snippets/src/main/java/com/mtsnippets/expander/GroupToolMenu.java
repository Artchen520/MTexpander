package com.mtsnippets.expander;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import bin.mt.plugin.api.editor.BaseTextEditorToolMenu;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;

/**
 * 顶部编辑菜单里的「分组开关」入口。
 */
public class GroupToolMenu extends BaseTextEditorToolMenu {

    @NonNull
    @Override
    public String name() {
        return "分组开关";
    }

    @NonNull
    @Override
    public Drawable icon() {
        return SnippetExpander.icon("tune", "code");
    }

    @Override
    public boolean checkVisible(@NonNull TextEditor editor) {
        return true;
    }

    @Override
    public void onMenuClick(@NonNull PluginUI pluginUI, @NonNull TextEditor editor) {
        SnippetGroupPanel.show(pluginUI, getContext(), editor.getFileName());
    }
}
