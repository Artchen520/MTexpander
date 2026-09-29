package com.mtsnippets.expander;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.ui.PluginUI;
import bin.mt.plugin.api.ui.PluginView;
import bin.mt.plugin.api.ui.builder.PluginCheckBoxBuilder;
import bin.mt.plugin.api.ui.builder.PluginSwitchButtonBuilder;
import bin.mt.plugin.api.ui.builder.PluginUIBuilder;

/**
 * 编辑器内的分组开关面板：临时/持久切换，不离开编辑器。
 */
public final class SnippetGroupPanel {

    private SnippetGroupPanel() {
    }

    public static void show(PluginUI ui, PluginContext context, String fileName) {
        SnippetsStore.ensureReady(context);

        List<Object[]> rows = new ArrayList<>(); // {Boolean snippetMode, String groupName}
        final AtomicReference<PluginView> viewRef = new AtomicReference<>();

        PluginUIBuilder root = ui.buildVerticalLayout();
        root.addTextView().text("当前文件：" + (fileName.isEmpty() ? "无类型" : fileName))
                .textSize(12).widthMatchParent();
        buildSection(ui, root, context, rows, true, viewRef);
        buildSection(ui, root, context, rows, false, viewRef);

        PluginCheckBoxBuilder persist = root.addCheckBox("persist_cb")
                .text("持久记住（勾选后立即固化当前开关状态，重启仍生效）")
                .widthMatchParent().marginTopDp(10);
        if (SnippetsStore.panelPersistDefault(context)) {
            persist.check();
        }

        PluginView view = root.build();
        viewRef.set(view);

        ui.buildDialog()
                .setTitle("分组开关")
                .setView(view)
                .setNeutralButton("管理分组…", (dialog, which) -> ui.showPreference(null))
                .setPositiveButton("{close}", null)
                .show();
    }

    private static void buildSection(PluginUI ui, PluginUIBuilder root, PluginContext context,
                                     List<Object[]> rows, boolean snippetMode,
                                     AtomicReference<PluginView> viewRef) {
        root.addTextView().text(snippetMode ? "缩写分组" : "环绕分组")
                .bold().widthMatchParent().marginTopDp(10);
        List<SnippetsStore.Group> groups = SnippetsStore.listGroups(context, snippetMode);
        if (groups.isEmpty()) {
            root.addTextView().text("（无分组）").textSize(12).widthMatchParent();
            return;
        }
        int idx = 0;
        for (SnippetsStore.Group g : groups) {
            final boolean snippetModeF = snippetMode;
            final String name = g.name;
            boolean enabled = SnippetsStore.isEnabled(context, snippetMode, g.name);
            PluginSwitchButtonBuilder b = root.addSwitchButton("sw_" + (snippetMode ? "s" : "d") + idx)
                    .text(g.name + " · " + g.entries.size() + " 条 · " + SnippetsStore.scopeDesc(g.defaultScope))
                    .widthMatchParent();
            if (enabled) {
                b.check();
            }
            b.onCheckedChange((buttonView, isChecked) ->
                    SnippetsStore.setGroupEnabled(context, snippetModeF, name, isChecked,
                            readPersist(ui, context, viewRef)));
            rows.add(new Object[]{snippetModeF, name});
            idx++;
        }
    }

    private static boolean readPersist(PluginUI ui, PluginContext context, AtomicReference<PluginView> viewRef) {
        PluginView view = viewRef.get();
        if (view == null) {
            return SnippetsStore.panelPersistDefault(context);
        }
        try {
            bin.mt.plugin.api.ui.PluginCheckBox cb = view.requireViewById("persist_cb");
            return cb.isChecked();
        } catch (Exception e) {
            return SnippetsStore.panelPersistDefault(context);
        }
    }
}
