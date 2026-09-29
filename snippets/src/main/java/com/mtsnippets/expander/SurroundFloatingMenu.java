package com.mtsnippets.expander;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.editor.BaseTextEditorFloatingMenu;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;

import java.util.List;

/**
 * 选中文字时的「环绕包裹」入口：按当前文件类型过滤模板。
 */
public class SurroundFloatingMenu extends BaseTextEditorFloatingMenu {

    @NonNull
    @Override
    public String name() {
        return "环绕包裹";
    }

    @NonNull
    @Override
    public Drawable icon() {
        return SnippetExpander.icon("wrap_text", "content_copy");
    }

    @Override
    public boolean checkVisible(@NonNull TextEditor editor) {
        return editor.hasTextSelected();
    }

    @Override
    public void onMenuClick(@NonNull PluginUI pluginUI, @NonNull TextEditor editor) {
        PluginContext context = getContext();
        String fileName = editor.getFileName();
        List<SnippetEngine.SurroundItem> items = SnippetEngine.surroundsFor(context, fileName);
        if (items.isEmpty()) {
            pluginUI.showToast("当前文件类型没有可用的环绕模板");
            return;
        }

        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) {
            SnippetEngine.SurroundItem item = items.get(i);
            labels[i] = item.name + " · " + item.groupName;
        }

        final TextEditor ed = editor;
        pluginUI.buildDialog()
                .setTitle("环绕包裹")
                .setItems(labels, (dialog, which) -> {
                    SnippetEngine.SurroundItem item = items.get(which);
                    SnippetExpander.surroundWith(pluginUI, ed, item.name, item.template);
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }
}
