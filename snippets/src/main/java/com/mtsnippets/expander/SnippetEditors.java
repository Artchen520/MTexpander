package com.mtsnippets.expander;

import android.graphics.Typeface;

import androidx.annotation.NonNull;

import java.util.List;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.ui.PluginEditText;
import bin.mt.plugin.api.ui.PluginView;
import bin.mt.plugin.api.ui.PluginUI;
import bin.mt.plugin.api.ui.builder.PluginUIBuilder;

/**
 * 分组编辑对话框：编辑库内容 / 新建分组 / 删除分组。
 */
public final class SnippetEditors {

    private SnippetEditors() {
    }

    /** 编辑某个分组的内容 */
    public static void openGroupEditor(PluginUI ui, PluginContext context,
                                       boolean snippetMode, String groupName) {
        String content = SnippetsStore.loadRaw(context, snippetMode, groupName);
        if (content == null) {
            ui.showToast("分组不存在：" + groupName);
            return;
        }
        openEditor(ui, context, snippetMode, groupName, content);
    }

    /** 新建分组：输入组名与默认适用范围 */
    public static void openNewGroup(final PluginUI ui, final PluginContext context, final boolean snippetMode) {
        String kind = snippetMode ? "缩写" : "环绕";
        PluginUIBuilder root = ui.buildVerticalLayout();
        root.addTextView().text("组名（作为文件名，不能含 \\/:*?\"<>|）").textSize(12).widthMatchParent();
        root.addEditBox("g_name").hint("例如：Java 日志").singleLine(true).widthMatchParent();
        root.addTextView().text("默认适用扩展名，逗号分隔，留空=所有文件（如 java 或 java,smali）")
                .textSize(12).widthMatchParent().marginTopDp(10);
        root.addEditBox("g_scope").hint("例如：java").singleLine(true).widthMatchParent();
        PluginView view = root.build();

        ui.buildDialog()
                .setTitle("新建" + kind + "分组")
                .setView(view)
                .setPositiveButton("创建", (dialog, which) -> {
                    PluginEditText nameEdit = view.requireViewById("g_name");
                    PluginEditText scopeEdit = view.requireViewById("g_scope");
                    String name = SnippetsStore.sanitizeName(nameEdit.getText().toString());
                    String scope = scopeEdit.getText().toString().trim();
                    try {
                        if (name.isEmpty()) {
                            throw new IllegalArgumentException("组名不能为空");
                        }
                        SnippetsStore.createGroup(context, snippetMode, name, scope);
                        ui.showToast("已创建分组「" + name + "」");
                        openGroupEditor(ui, context, snippetMode, name);
                    } catch (IllegalArgumentException e) {
                        ui.showToast("创建失败：" + e.getMessage());
                        openNewGroup(ui, context, snippetMode);
                    }
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private static void openEditor(final PluginUI ui, final PluginContext context,
                                   final boolean snippetMode, final String groupName, String content) {
        PluginView view = ui.buildVerticalLayout()
                .addEditBox("editor")
                .text(content)
                .typeface(Typeface.MONOSPACE)
                .textSize(12)
                .minLines(10)
                .maxLines(16)
                .softWrap(PluginEditText.SOFT_WRAP_DISABLE)
                .widthMatchParent()
                .build();

        ui.buildDialog()
                .setTitle("编辑分组：" + groupName)
                .setView(view)
                .setPositiveButton("保存", (dialog, which) -> {
                    PluginEditText edit = view.requireViewById("editor");
                    String newText = edit.getText().toString();
                    try {
                        SnippetsStore.saveGroup(context, snippetMode, groupName, newText);
                        List<String> dups = SnippetEngine.findDuplicates(context, snippetMode);
                        if (dups.isEmpty()) {
                            ui.showToast("已保存");
                        } else {
                            ui.showToast("已保存。注意：以下条目在多个分组重复（按组顺序先匹配生效）："
                                    + joinLimit(dups));
                        }
                    } catch (IllegalArgumentException e) {
                        ui.showToast("保存失败：" + e.getMessage());
                        openEditor(ui, context, snippetMode, groupName, newText);
                    }
                })
                .setNegativeButton("{cancel}", null)
                .setNeutralButton("删除该组", (dialog, which) -> confirmDelete(ui, context, snippetMode, groupName))
                .show();
    }

    private static void confirmDelete(final PluginUI ui, final PluginContext context,
                                      final boolean snippetMode, final String groupName) {
        ui.buildDialog()
                .setTitle("删除分组")
                .setMessage("确定删除分组「" + groupName + "」？该操作不可恢复。"
                        + ("内置".equals(groupName) ? "\n注意：删除内置组将停用全部内置条目。" : ""))
                .setPositiveButton("删除", (dialog, which) -> {
                    SnippetsStore.deleteGroup(context, snippetMode, groupName);
                    ui.showToast("已删除分组「" + groupName + "」");
                })
                .setNegativeButton("{cancel}", null)
                .show();
    }

    private static String joinLimit(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() && i < 6; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(list.get(i));
        }
        if (list.size() > 6) {
            sb.append(" 等").append(list.size()).append("个");
        }
        return sb.toString();
    }
}
