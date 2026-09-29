package com.mtsnippets.expander;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bin.mt.plugin.api.PluginContext;

/**
 * 模板展开引擎 + 分组匹配。
 * <p>
 * 占位符：$0$ = 展开后光标位置；$S$ = 选中内容（仅环绕模板）。
 * 模板换行使后续行自动追加当前行缩进；选中内容按 $S$ 所在行深度重缩进。
 * <p>
 * 匹配规则：分组按名称排序（与设置页显示顺序一致），同一条目多组命中取第一个
 * 适用当前文件类型的；范围不命中的组跳过并记录，用于提示。
 */
public final class SnippetEngine {

    public static final String CURSOR = "$0$";
    public static final String SEL = "$S$";

    private SnippetEngine() {
    }

    //////////////////////////////////////////////////////////////
    // 匹配
    //////////////////////////////////////////////////////////////

    public static final class MatchResult {
        /** true = 命中可用条目；false = 存在条目但范围不适用 */
        public final boolean applicable;
        public final String template;
        public final String groupName;
        public final String scopeDesc;

        MatchResult(boolean applicable, String template, String groupName, String scopeDesc) {
            this.applicable = applicable;
            this.template = template;
            this.groupName = groupName;
            this.scopeDesc = scopeDesc;
        }
    }

    public static final class SurroundItem {
        public final String name;
        public final String template;
        public final String groupName;

        SurroundItem(String name, String template, String groupName) {
            this.name = name;
            this.template = template;
            this.groupName = groupName;
        }
    }

    /** 缩写匹配：组顺序遍历，先命中先生效 */
    public static MatchResult matchSnippet(PluginContext context, String word, String fileName) {
        SnippetsStore.ensureReady(context);
        MatchResult blocked = null;
        for (SnippetsStore.Group g : SnippetsStore.listGroups(context, SnippetsStore.MODE_SNIPPET)) {
            if (!SnippetsStore.isEnabled(context, SnippetsStore.MODE_SNIPPET, g.name)) {
                continue;
            }
            SnippetsStore.Entry entry = g.entries.get(word);
            if (entry == null) {
                continue;
            }
            List<String> scope = g.effectiveScope(entry);
            if (SnippetsStore.scopeApplies(scope, fileName)) {
                return new MatchResult(true, entry.template, g.name, null);
            }
            if (blocked == null) {
                blocked = new MatchResult(false, null, g.name, SnippetsStore.scopeDesc(scope));
            }
        }
        return blocked;
    }

    /** 某分组内处于重名状态的条目 */
    public static List<String> groupDuplicates(PluginContext context, boolean snippetMode, String groupName) {
        SnippetsStore.Group group = SnippetsStore.loadGroup(context, snippetMode, groupName);
        if (group == null) {
            return new ArrayList<>();
        }
        List<String> all = SnippetsStore.findDuplicates(context, snippetMode);
        List<String> out = new ArrayList<>();
        for (String key : group.entries.keySet()) {
            if (all.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    /** 环绕模板列表：启用组按顺序，范围过滤，同名先命中优先 */
    public static List<SurroundItem> surroundsFor(PluginContext context, String fileName) {
        SnippetsStore.ensureReady(context);
        List<SurroundItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SnippetsStore.Group g : SnippetsStore.listGroups(context, SnippetsStore.MODE_SURROUND)) {
            if (!SnippetsStore.isEnabled(context, SnippetsStore.MODE_SURROUND, g.name)) {
                continue;
            }
            for (Map.Entry<String, SnippetsStore.Entry> e : g.entries.entrySet()) {
                if (seen.contains(e.getKey())) {
                    continue;
                }
                List<String> scope = g.effectiveScope(e.getValue());
                if (SnippetsStore.scopeApplies(scope, fileName)) {
                    seen.add(e.getKey());
                    items.add(new SurroundItem(e.getKey(), e.getValue().template, g.name));
                }
            }
        }
        return items;
    }

    /** 找出在多个分组中重复的条目名 */
    public static List<String> findDuplicates(PluginContext context, boolean snippetMode) {
        return SnippetsStore.findDuplicates(context, snippetMode);
    }

    //////////////////////////////////////////////////////////////
    // 展开（纯逻辑，可单测）
    //////////////////////////////////////////////////////////////

    /** 从 cursor 位置向前扫描标识符，返回单词起始位置 */
    public static int wordStartBefore(CharSequence text, int cursor) {
        int start = cursor;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    /** 返回 text 中 position 所在行的行首缩进（空格/制表符） */
    public static String indentAt(CharSequence text, int position) {
        int lineStart = position;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        int indentEnd = lineStart;
        while (indentEnd < position) {
            char c = text.charAt(indentEnd);
            if (c != ' ' && c != '\t') {
                break;
            }
            indentEnd++;
        }
        return text.subSequence(lineStart, indentEnd).toString();
    }

    /** 展开结果：替换文本 + 光标相对偏移（-1 表示末尾） */
    public static final class Expansion {
        public final String text;
        public final int cursorOffset;

        Expansion(String text, int cursorOffset) {
            this.text = text;
            this.cursorOffset = cursorOffset;
        }
    }

    /**
     * 展开模板：单遍扫描，处理 $0$/$S$/换行缩进。
     * selection 为 null 时不允许出现 $S$（保持原样）。
     */
    public static Expansion expand(String template, String baseIndent, String selection) {
        StringBuilder out = new StringBuilder();
        int cursorOffset = -1;
        int tailWs = 0;
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == '$' && i + 2 < n && template.charAt(i + 2) == '$') {
                char c1 = template.charAt(i + 1);
                if (c1 == '0') {
                    cursorOffset = out.length();
                    i += 3;
                    continue;
                }
                if (c1 == 'S' && selection != null) {
                    // 当前输出行已带 baseIndent；tailWs 含 baseIndent，
                    // extra = 模板行内 $S$ 之前的额外空白（不含 baseIndent）
                    String extra = tailWs > baseIndent.length()
                            ? out.substring(out.length() - (tailWs - baseIndent.length()))
                            : "";
                    String contIndent = baseIndent + extra;
                    String normalized = selection.replace("\r\n", "\n").replace("\r", "\n");
                    String[] selLines = normalized.split("\n", -1);
                    int last = selLines.length - 1;
                    while (last > 0 && selLines[last].trim().isEmpty()) {
                        last--;
                    }
                    selLines[last] = rstrip(selLines[last]);
                    int minWs = Integer.MAX_VALUE;
                    for (int k = 0; k <= last; k++) {
                        if (selLines[k].trim().isEmpty()) {
                            continue;
                        }
                        int w = leadingWs(selLines[k]);
                        if (w < minWs) {
                            minWs = w;
                        }
                    }
                    if (minWs == Integer.MAX_VALUE) {
                        minWs = 0;
                    }
                    for (int k = 0; k <= last; k++) {
                        String line = selLines[k];
                        if (k == 0) {
                            out.append(line, Math.min(minWs, line.length()), line.length());
                        } else {
                            out.append('\n');
                            if (!line.trim().isEmpty()) {
                                out.append(contIndent)
                                        .append(line, Math.min(minWs, line.length()), line.length());
                            }
                        }
                    }
                    tailWs = 0;
                    i += 3;
                    continue;
                }
            }
            if (c == '\n') {
                out.append('\n').append(baseIndent);
                tailWs = baseIndent.length();
                i++;
                continue;
            }
            out.append(c);
            if (c == ' ' || c == '\t') {
                tailWs++;
            } else {
                tailWs = 0;
            }
            i++;
        }
        return new Expansion(out.toString(), cursorOffset);
    }

    private static int leadingWs(String s) {
        int w = 0;
        while (w < s.length()) {
            char c = s.charAt(w);
            if (c != ' ' && c != '\t') {
                break;
            }
            w++;
        }
        return w;
    }

    private static String rstrip(String s) {
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c != ' ' && c != '\t' && c != '\r') {
                break;
            }
            end--;
        }
        return s.substring(0, end);
    }
}
