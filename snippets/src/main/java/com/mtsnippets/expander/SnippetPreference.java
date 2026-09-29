package com.mtsnippets.expander;

import java.util.List;
import java.util.Map;

import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.preference.PluginPreference;
import bin.mt.plugin.api.ui.PluginUI;

/**
 * 插件设置页：使用说明、分组管理、开关默认行为、注释规则。
 */
public class SnippetPreference implements PluginPreference {

    @Override
    public void onBuild(PluginContext context, Builder builder) {
        SnippetsStore.ensureReady(context);
        builder.title("代码修补");
        builder.subtitle("IDEA 风格的代码片段展开与框选操作 v4.1");

        builder.addText("项目网址")
                .summary("github.com/Artchen520/-")
                .url("https://github.com/Artchen520/-");

        builder.addHeader("使用方法");
        builder.addText("缩写展开（光标模式）")
                .summary("输入缩写后，光标停在缩写词后面，点底部工具栏的「展开片段」");
        builder.addText("缩写展开（选中模式）")
                .summary("选中一个缩写词，在浮动菜单里点「展开片段」");
        builder.addText("批量注释")
                .summary("选中多行代码，浮动菜单点「批量注释」；注释符对齐到块内最小缩进列，且跟随文件类型");
        builder.addText("环绕包裹")
                .summary("选中一段代码，浮动菜单点「环绕包裹」，选模板即可包上 try-catch、for、if 等");
        builder.addText("分组开关（编辑器内）")
                .summary("编辑器顶部菜单点「分组开关」，随时开关任意分组，默认临时生效，可勾选持久记住");

        builder.addHeader("开关面板默认行为");
        boolean persist = SnippetsStore.panelPersistDefault(context);
        builder.addText("面板开关默认持久：" + (persist ? "开" : "关"))
                .summary("开启后，编辑器内面板的「持久记住」默认勾选；点按切换")
                .onClick((ui, item) -> {
                    boolean now = !SnippetsStore.panelPersistDefault(context);
                    SnippetsStore.setPanelPersistDefault(context, now);
                    ui.showToast("开关面板默认持久：" + (now ? "开" : "关") + "（重新打开本页刷新显示）");
                });

        buildGroupSection(builder, context, true);
        buildGroupSection(builder, context, false);

        builder.addHeader("批量注释标记（按文件类型）");
        builder.addText("注释符对照")
                .summary("java/kt/c/cpp/js/ts/go/cs/php/swift → //；xml/html → <!-- -->；"
                        + "py/rb/pl/sh/smali/yaml/toml/ini → #；sql/lua → --；未知类型 → //");

        builder.addHeader("匹配规则");
        builder.addText("分组顺序与重名")
                .summary("分组按名称排序显示；同一条目在多个分组出现时，按显示顺序先匹配先生效，保存时会提醒重名");
        builder.addText("适用范围写法")
                .summary("组头 #适用：逗号分隔扩展名，留空=所有文件；条目行尾 @java,smali 单条限定，@（空）=强制通用；也可写完整文件名精确匹配");
    }

    private static void buildGroupSection(Builder builder, PluginContext context, boolean snippetMode) {
        String kind = snippetMode ? "缩写" : "环绕";
        builder.addHeader(kind + "分组");
        java.util.List<SnippetsStore.Group> groups = SnippetsStore.listGroups(context, snippetMode);
        for (final SnippetsStore.Group g : groups) {
            java.util.List<String> dups = SnippetEngine.groupDuplicates(context, snippetMode, g.name);
            String mark = dups.isEmpty() ? "" : "　※" + dups.size() + " 条重名";
            builder.addText(g.name + mark)
                    .summary(g.entries.size() + " 条 · " + SnippetsStore.scopeDesc(g.defaultScope)
                            + "（点击编辑）")
                    .onClick((ui, item) -> SnippetEditors.openGroupEditor(ui, context, snippetMode, g.name));
        }
        builder.addText("＋ 新建" + kind + "分组")
                .summary("创建一个新的" + kind + "分组并开始编辑")
                .onClick((ui, item) -> SnippetEditors.openNewGroup(ui, context, snippetMode));
    }
}
