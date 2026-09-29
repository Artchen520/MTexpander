package com.mtsnippets.expander;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import bin.mt.json.JSONObject;
import bin.mt.plugin.api.editor.BaseTextEditorFunction;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;

/**
 * 编辑器底部工具栏按钮：光标停在缩写词后面，点击即可展开。
 */
public class SnippetFunction extends BaseTextEditorFunction {

    @NonNull
    @Override
    public String name() {
        return "展开片段";
    }

    @Override
    public boolean supportEditTextView() {
        return false;
    }

    @Override
    public boolean supportRepeat() {
        return false;
    }

    @Override
    public void doFunction(@NonNull PluginUI pluginUI, @NonNull TextEditor editor, @Nullable JSONObject data) {
        SnippetExpander.expandAt(pluginUI, getContext(), editor);
    }
}
