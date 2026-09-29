package com.mtsnippets.expander;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import bin.mt.plugin.api.PluginContext;

/**
 * 分组库存储层。
 * <p>
 * 目录结构（插件私有目录）：
 *   groups/snippets/{组名}.txt   缩写分组
 *   groups/surrounds/{组名}.txt  环绕分组
 *   groups/disabled_snippets.txt  持久停用的缩写分组名（每行一个）
 *   groups/disabled_surrounds.txt 持久停用的环绕分组名
 * <p>
 * 分组文件格式（UTF-8）：
 *   #适用：java,smali        组默认适用范围（可省略=通用；token 也可写完整文件名）
 *   # 注释行
 *   缩写 = 模板 @java,smali  条目级范围覆盖；@（空）=强制通用；无 @ =沿用组默认
 *   环绕名称 = 模板 @...      （环绕模板必须包含 $S$）
 * <p>
 * 范围匹配规则：token == 扩展名（忽略大小写）或 token == 完整文件名。
 */
public final class SnippetsStore {

    public static final boolean MODE_SNIPPET = true;
    public static final boolean MODE_SURROUND = false;

    private static final String DIR_SNIPPETS = "groups/snippets";
    private static final String DIR_SURROUNDS = "groups/surrounds";
    private static final String FILE_DISABLED_SNIPPETS = "groups/disabled_snippets.txt";
    private static final String FILE_DISABLED_SURROUNDS = "groups/disabled_surrounds.txt";
    private static final String FILE_PANEL_PERSIST = "groups/panel_persist.txt";
    private static final String LEGACY_SNIPPETS = "custom_snippets.txt";
    private static final String LEGACY_SURROUNDS = "custom_surrounds.txt";
    private static final String BUILTIN_GROUP = "内置";
    private static final String JAVA_GROUP = "Java";
    private static final String SEED_MARKER = ".seeded_v3.2";
    private static final String MIGRATED_GROUP = "通用";

    /** 临时开关状态（进程内），key=组名 */
    private static final Map<String, Boolean> TEMP_SNIPPET = new LinkedHashMap<>();
    private static final Map<String, Boolean> TEMP_SURROUND = new LinkedHashMap<>();

    private static boolean ready = false;

    private SnippetsStore() {
    }

    //////////////////////////////////////////////////////////////
    // 数据模型
    //////////////////////////////////////////////////////////////

    public static final class Entry {
        public final String key;
        public final String template;
        /** null = 沿用组默认范围；空列表 = 强制通用；非空 = token 列表 */
        public final List<String> scope;

        Entry(String key, String template, List<String> scope) {
            this.key = key;
            this.template = template;
            this.scope = scope;
        }
    }

    public static final class Group {
        public final String name;
        public final List<String> defaultScope;
        public final LinkedHashMap<String, Entry> entries;

        Group(String name, List<String> defaultScope, LinkedHashMap<String, Entry> entries) {
            this.name = name;
            this.defaultScope = defaultScope;
            this.entries = entries;
        }

        /** 条目生效范围（不为 null；空=通用） */
        public List<String> effectiveScope(Entry entry) {
            return entry.scope != null ? entry.scope : defaultScope;
        }

        public String scopeDesc() {
            return SnippetsStore.scopeDesc(defaultScope);
        }
    }

    public static String scopeDesc(List<String> scope) {
        return scope == null || scope.isEmpty() ? "所有文件" : join(scope, ",");
    }

    private static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    //////////////////////////////////////////////////////////////
    // 范围解析与匹配（纯逻辑，可单测）
    //////////////////////////////////////////////////////////////

    /** 解析 "#适用：java,smali" 的值部分；空串 = 通用 */
    public static List<String> parseScope(String value) {
        List<String> tokens = new ArrayList<>();
        if (value != null) {
            String[] parts = value.trim().split("[,，;；\\s]+");
            for (String p : parts) {
                p = p.trim();
                if (!p.isEmpty()) {
                    tokens.add(p);
                }
            }
        }
        return tokens;
    }

    /** 范围是否命中：空=通用；token 等于扩展名或完整文件名 */
    public static boolean scopeApplies(List<String> scope, String fileName) {
        if (scope == null || scope.isEmpty()) {
            return true;
        }
        String ext = extOf(fileName);
        for (String token : scope) {
            if (token.equalsIgnoreCase(ext) || token.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    public static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /** 拆分模板行尾的 " @scope" 后缀（@ 前必须有空白，避免误伤含 @ 的模板）：返回 [模板, scopeToken串或null] */
    public static String[] splitScopeSuffix(String template) {
        int at = -1;
        for (int i = template.length() - 1; i > 0; i--) {
            if (template.charAt(i) == '@'
                    && (template.charAt(i - 1) == ' ' || template.charAt(i - 1) == '\t')) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return new String[]{template, null};
        }
        for (int i = at + 1; i < template.length(); i++) {
            char c = template.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == ',' || c == ' ' || c == '\t';
            if (!ok) {
                return new String[]{template, null};
            }
        }
        String before = template.substring(0, at);
        int cut = before.length();
        while (cut > 0 && (before.charAt(cut - 1) == ' ' || before.charAt(cut - 1) == '\t')) {
            cut--;
        }
        return new String[]{template.substring(0, cut), template.substring(at + 1).trim()};
    }

    //////////////////////////////////////////////////////////////
    // 分组解析（纯逻辑，可单测）
    //////////////////////////////////////////////////////////////

    public static Group parseGroup(String name, String content, boolean snippetMode) {
        List<String> defaultScope = null;
        LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
        String[] lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                if (line.startsWith("#适用：") || line.startsWith("#适用:")) {
                    defaultScope = parseScope(line.substring(4));
                } else if (line.startsWith("#适用")) {
                    defaultScope = new ArrayList<>();
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行缺少 \"=\"（格式：名称 = 模板）");
            }
            String key = line.substring(0, eq).trim();
            String rhs = line.substring(eq + 1).trim();
            if (key.isEmpty() || rhs.isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行缺少名称或模板");
            }
            if (snippetMode) {
                // 缩写名必须是合法标识符（用户要敲的词）
                if (!key.matches("[A-Za-z_][A-Za-z0-9_$]*")) {
                    throw new IllegalArgumentException("第 " + (i + 1) + " 行缩写名「" + key
                            + "」只能由字母、数字、下划线组成（且不以数字开头）");
                }
            } else if (key.contains("\n")) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行名称不合法");
            }
            String[] sp = splitScopeSuffix(rhs);
            String template = unescape(sp[0]);
            List<String> scope = sp[1] == null ? null : parseScope(sp[1]);
            if (!snippetMode && !template.contains(SnippetEngine.SEL)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行模板缺少 $S$（选中内容占位符）。"
                        + "注意：$S$ 只有环绕分组需要；如果你粘贴的是缩写库内容，请建到「缩写分组」里");
            }
            if (entries.containsKey(key)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行与前面重复定义了「" + key + "」");
            }
            entries.put(key, new Entry(key, template, scope));
        }
        return new Group(name, defaultScope, entries);
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 'n') {
                    sb.append('\n');
                    i++;
                    continue;
                }
                if (n == '\\') {
                    sb.append('\\');
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    //////////////////////////////////////////////////////////////
    // 文件读写
    //////////////////////////////////////////////////////////////

    private static File groupsDir(PluginContext context, boolean snippetMode) {
        return new File(context.getFilesDir(), snippetMode ? DIR_SNIPPETS : DIR_SURROUNDS);
    }

    private static File disabledFile(PluginContext context, boolean snippetMode) {
        return new File(context.getFilesDir(), snippetMode ? FILE_DISABLED_SNIPPETS : FILE_DISABLED_SURROUNDS);
    }

    /** 首次访问时播种内置组并迁移 v2 旧库（幂等） */
    public static void ensureReady(PluginContext context) {
        if (ready) {
            return;
        }
        synchronized (SnippetsStore.class) {
            if (ready) {
                return;
            }
            File marker = new File(context.getFilesDir(), SEED_MARKER);
            if (!marker.exists()) {
                seedNamed(context, MODE_SNIPPET, BUILTIN_GROUP, SEED_SNIPPETS);
                seedNamed(context, MODE_SURROUND, BUILTIN_GROUP, SEED_SURROUNDS);
                seedNamed(context, MODE_SNIPPET, JAVA_GROUP, SEED_JAVA_SNIPPETS);
                seedNamed(context, MODE_SURROUND, JAVA_GROUP, SEED_JAVA_SURROUNDS);
                migrateLegacy(context, MODE_SNIPPET, LEGACY_SNIPPETS, MIGRATED_GROUP);
                migrateLegacy(context, MODE_SURROUND, LEGACY_SURROUNDS, MIGRATED_GROUP);
                writeText(marker, "v3.2");
            }
            ready = true;
        }
    }

    private static void seedNamed(PluginContext context, boolean mode, String groupName, String seed) {
        File f = new File(groupsDir(context, mode), groupName + ".txt");
        if (!f.exists()) {
            writeText(f, seed);
        }
    }

    private static void migrateLegacy(PluginContext context, boolean mode, String legacyName, String groupName) {
        File legacy = new File(context.getFilesDir(), legacyName);
        File target = new File(groupsDir(context, mode), groupName + ".txt");
        if (legacy.exists() && !target.exists()) {
            String content = readText(legacy);
            if (content != null && !content.trim().isEmpty()) {
                writeText(target, "# 由 v2 自定义库迁移而来\n" + content);
            }
        }
    }

    public static List<Group> listGroups(PluginContext context, boolean snippetMode) {
        ensureReady(context);
        File dir = groupsDir(context, snippetMode);
        File[] files = dir.listFiles();
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(".txt")) {
                    names.add(n.substring(0, n.length() - 4));
                }
            }
        }
        Collections.sort(names);
        List<Group> groups = new ArrayList<>();
        for (String name : names) {
            Group g = loadGroup(context, snippetMode, name);
            if (g != null) {
                groups.add(g);
            }
        }
        return groups;
    }

    /** 读取分组（文件缺失或解析失败返回 null） */
    public static Group loadGroup(PluginContext context, boolean snippetMode, String name) {
        String content = readText(groupFile(context, snippetMode, name));
        if (content == null) {
            return null;
        }
        try {
            return parseGroup(name, content, snippetMode);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String loadRaw(PluginContext context, boolean snippetMode, String name) {
        String content = readText(groupFile(context, snippetMode, name));
        return content == null ? "" : content;
    }

    public static File groupFile(PluginContext context, boolean snippetMode, String name) {
        return new File(groupsDir(context, snippetMode), name + ".txt");
    }

    public static boolean groupExists(PluginContext context, boolean snippetMode, String name) {
        ensureReady(context);
        return groupFile(context, snippetMode, name).exists();
    }

    /** 保存分组内容（先解析校验，失败抛 IllegalArgumentException） */
    public static void saveGroup(PluginContext context, boolean snippetMode, String name, String content) {
        parseGroup(name, content, snippetMode);
        writeText(groupFile(context, snippetMode, name), content);
    }

    public static void deleteGroup(PluginContext context, boolean snippetMode, String name) {
        File f = groupFile(context, snippetMode, name);
        if (f.exists() && !f.delete()) {
            throw new IllegalArgumentException("删除文件失败：" + f.getAbsolutePath());
        }
    }

    /** 新建分组：写入默认头与示例，名字冲突或不合法抛异常 */
    public static File createGroup(PluginContext context, boolean snippetMode, String name, String scopeDesc) {
        String safe = sanitizeName(name);
        if (groupExists(context, snippetMode, safe)) {
            throw new IllegalArgumentException("分组「" + safe + "」已存在");
        }
        StringBuilder sb = new StringBuilder();
        if (scopeDesc != null && !scopeDesc.trim().isEmpty()) {
            sb.append("#适用：").append(scopeDesc.trim()).append('\n');
        }
        sb.append("# 每行一条：名称 = 模板\n");
        if (snippetMode) {
            sb.append("# $0$ = 展开后光标位置；\\n = 换行；行尾 @java 限定适用类型，@ = 通用\n");
        } else {
            sb.append("# $S$ = 选中内容（必须包含）；$0$ = 光标位置；\\n = 换行\n");
        }
        File f = groupFile(context, snippetMode, safe);
        writeText(f, sb.toString());
        return f;
    }

    public static String sanitizeName(String name) {
        String n = name == null ? "" : name.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|\\n\\r]", "_");
        n = n.replaceAll("\\s+", "_");
        if (n.isEmpty()) {
            throw new IllegalArgumentException("分组名不能为空");
        }
        if (n.length() > 40) {
            n = n.substring(0, 40);
        }
        return n;
    }

    /** 找出在多个分组中重复的条目名（排序去重） */
    public static List<String> findDuplicates(PluginContext context, boolean snippetMode) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (Group g : listGroups(context, snippetMode)) {
            for (String key : g.entries.keySet()) {
                Integer c = count.get(key);
                count.put(key, c == null ? 1 : c + 1);
            }
        }
        List<String> dups = new ArrayList<>();
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            if (e.getValue() > 1) {
                dups.add(e.getKey());
            }
        }
        Collections.sort(dups);
        return dups;
    }

    //////////////////////////////////////////////////////////////
    // 分组开关（持久 + 临时）
    //////////////////////////////////////////////////////////////

    public static Set<String> persistentDisabled(PluginContext context, boolean snippetMode) {
        String content = readText(disabledFile(context, snippetMode));
        Set<String> set = new HashSet<>();
        if (content != null) {
            for (String line : content.split("\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    set.add(line);
                }
            }
        }
        return set;
    }

    private static void writeDisabled(PluginContext context, boolean snippetMode, Set<String> names) {
        StringBuilder sb = new StringBuilder();
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        for (String n : sorted) {
            sb.append(n).append('\n');
        }
        writeText(disabledFile(context, snippetMode), sb.toString());
    }

    /** 分组当前是否启用（临时状态优先于持久状态） */
    public static boolean isEnabled(PluginContext context, boolean snippetMode, String name) {
        Map<String, Boolean> temp = snippetMode ? TEMP_SNIPPET : TEMP_SURROUND;
        Boolean t;
        synchronized (temp) {
            t = temp.get(name);
        }
        if (t != null) {
            return t;
        }
        return !persistentDisabled(context, snippetMode).contains(name);
    }

    /**
     * 设置单个分组的开关状态。
     * persist=true：写入持久停用列表并清掉该组的临时状态；
     * persist=false：与持久状态求差记入临时状态（本次进程有效）。
     */
    public static void setGroupEnabled(PluginContext context, boolean snippetMode,
                                       String name, boolean enabled, boolean persist) {
        Map<String, Boolean> temp = snippetMode ? TEMP_SNIPPET : TEMP_SURROUND;
        if (persist) {
            Set<String> off = persistentDisabled(context, snippetMode);
            if (enabled) {
                off.remove(name);
            } else {
                off.add(name);
            }
            writeDisabled(context, snippetMode, off);
            synchronized (temp) {
                temp.remove(name);
            }
            return;
        }
        boolean persistentOn = !persistentDisabled(context, snippetMode).contains(name);
        synchronized (temp) {
            if (persistentOn == enabled) {
                temp.remove(name);
            } else {
                temp.put(name, enabled);
            }
        }
    }

    /** 开关面板默认持久（true=勾选「持久记住」的初始状态） */
    public static boolean panelPersistDefault(PluginContext context) {
        String c = readText(new File(context.getFilesDir(), FILE_PANEL_PERSIST));
        return "1".equals(c == null ? null : c.trim());
    }

    public static void setPanelPersistDefault(PluginContext context, boolean value) {
        writeText(new File(context.getFilesDir(), FILE_PANEL_PERSIST), value ? "1" : "0");
    }

    //////////////////////////////////////////////////////////////
    // 内置种子
    //////////////////////////////////////////////////////////////

    private static final String SEED_SNIPPETS = ""
            + "#适用：\n"
            + "# 内置缩写分组：本文件可直接修改，行尾可加 @java 限定类型（@ = 通用）。\n"
            + "# 删除本文件 = 停用全部内置缩写。\n"
            + "sout = System.out.println($0$); @java\n"
            + "serr = System.err.println($0$); @java\n"
            + "psvm = public static void main(String[] args) {\\n    $0$\\n} @java\n"
            + "main = public static void main(String[] args) {\\n    $0$\\n} @java\n"
            + "sop = System.out.println($0$); @java\n"
            + "sysout = System.out.println($0$); @java\n"
            + "psf = public static final $0$ @java\n"
            + "thr = throw new $0$; @java\n"
            + "fori = for (int i = 0; i < $0$; i++) {\\n    \\n}\n"
            + "iter = for (Object o : $0$) {\\n    \\n}\n"
            + "ifn = if ($0$ == null) {\\n    \\n}\n"
            + "inn = if ($0$ != null) {\\n    \\n}\n";

    private static final String SEED_SURROUNDS = ""
            + "#适用：\n"
            + "# 内置环绕分组：本文件可直接修改，行尾可加 @java 限定类型（@ = 通用）。\n"
            + "# 删除本文件 = 停用全部内置环绕模板。\n"
            + "try-catch = try {\\n    $S$\\n} catch (Exception e) {\\n    $0$\\n} @java\n"
            + "try-finally = try {\\n    $S$\\n} finally {\\n    $0$\\n} @java\n"
            + "synchronized = synchronized ($0$) {\\n    $S$\\n} @java\n"
            + "if = if ($0$) {\\n    $S$\\n}\n"
            + "while = while ($0$) {\\n    $S$\\n}\n"
            + "for = for (int i = 0; i < $0$; i++) {\\n    $S$\\n}\n"
            + "/* */ = /* $S$ */$0$\n";

    private static final String SEED_JAVA_SNIPPETS = ""
            + "#适用：java\n"
            + "# ═══ Java 分组 · 缩写库（可直接修改）═══\n"
            + "# 一行一条：缩写 = 模板；\\n = 换行；$0$ = 展开后光标位置；# = 注释\n"
            + "# 组默认范围 = java，未写 @ 的条目只对 .java 生效\n"
            + "\n"
            + "# ── 日志 ──\n"
            + "logd = Log.d(TAG, $0$);\n"
            + "logi = Log.i(TAG, $0$);\n"
            + "logw = Log.w(TAG, $0$);\n"
            + "loge = Log.e(TAG, $0$);\n"
            + "tag = private static final String TAG = \"$0$\";\n"
            + "\n"
            + "# ── Android 常用 ──\n"
            + "toast = Toast.makeText(this, $0$, Toast.LENGTH_SHORT).show();\n"
            + "intent = Intent $0$ = new Intent();\n"
            + "\n"
            + "# ── 集合 ──\n"
            + "alst = List<$0$> list = new ArrayList<>();\n"
            + "hmap = Map<String, $0$> map = new HashMap<>();\n"
            + "fore = for (Map.Entry<String, String> e : $0$.entrySet()) {\\n    \\n}\n"
            + "\n"
            + "# ── 流程控制 ──\n"
            + "wh = while ($0$) {\\n    \\n}\n"
            + "ife = if ($0$) {\\n    \\n} else {\\n    \\n}\n"
            + "elif = else if ($0$) {\\n    \\n}\n"
            + "switch = switch ($0$) {\\n    \\n}\n"
            + "nret = if ($0$ == null) {\\n    return null;\\n}\n"
            + "\n"
            + "# ── 工具 ──\n"
            + "pra = System.out.println(Arrays.toString($0$));\n"
            + "fmt = String.format(\"$0$\", );\n";

    private static final String SEED_JAVA_SURROUNDS = ""
            + "#适用：java\n"
            + "# ═══ Java 分组 · 环绕库（可直接修改）═══\n"
            + "# 一行一条：名称 = 模板；$S$ = 选中内容（必须）；$0$ = 光标位置；\\n = 换行\n"
            + "# 框选代码后浮动菜单「环绕包裹」里按名称出现\n"
            + "\n"
            + "判空执行 = if ($0$ != null) {\\n    $S$\\n}\n"
            + "lambda = () -> {\\n    $S$\\n}\n"
            + "匿名线程 = new Thread(() -> {\\n    $S$\\n}).start();\n"
            + "资源自动关闭 = try ($0$) {\\n    $S$\\n} catch (Exception e) {\\n    \\n}\n"
            + "遍历包裹 = for (Object o : $0$) {\\n    $S$\\n}\n"
            + "UI线程执行 = runOnUiThread(() -> {\\n    $S$\\n});\n";

    //////////////////////////////////////////////////////////////
    // 底层 IO
    //////////////////////////////////////////////////////////////

    private static String readText(File f) {
        if (!f.exists()) {
            return null;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeText(File f, String content) {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IllegalArgumentException("无法创建目录：" + parent.getAbsolutePath());
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("写入文件失败：" + e.getMessage());
        }
    }
}
