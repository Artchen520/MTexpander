package com.mtsnippets.expander;

/**
 * 批量注释纯逻辑（无 Android 依赖，可单测）。
 * <p>
 * 对齐规则：所有注释符插在块内非空行最小缩进列（IDEA 式）；
 * 取消注释同时兼容对齐式与旧式（各行自身缩进）。
 * 注释符按文件类型选择。
 */
public final class CommentOps {

    /** 注释样式 */
    public static final int STYLE_SLASH = 0;   // //
    public static final int STYLE_HASH = 1;    // #
    public static final int STYLE_DASH = 2;    // --
    public static final int STYLE_XML = 3;     // <!-- -->

    private CommentOps() {
    }

    /** 按文件名选择注释样式 */
    public static int styleFor(String fileName) {
        String ext = SnippetsStore.extOf(fileName == null ? "" : fileName);
        switch (ext) {
            case "xml":
            case "html":
            case "htm":
            case "xhtml":
            case "svg":
                return STYLE_XML;
            case "sql":
            case "lua":
                return STYLE_DASH;
            case "py":
            case "pyw":
            case "rb":
            case "pl":
            case "sh":
            case "bash":
            case "zsh":
            case "smali":
            case "yaml":
            case "yml":
            case "toml":
            case "ini":
            case "cfg":
            case "conf":
            case "properties":
                return STYLE_HASH;
            default:
                return STYLE_SLASH;
        }
    }

    public static String markerText(int style) {
        switch (style) {
            case STYLE_HASH:
                return "#";
            case STYLE_DASH:
                return "--";
            case STYLE_XML:
                return "<!--";
            default:
                return "//";
        }
    }

    /** 块内所有非空行是否已处于注释状态 */
    public static boolean isCommented(String block, int style) {
        String[] lines = block.split("\n", -1);
        String marker = markerText(style);
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            int w = leadingWs(line);
            String content = line.substring(w);
            boolean commented;
            if (style == STYLE_XML) {
                String rs = rstrip(line);
                commented = content.startsWith("<!--") && rs.endsWith("-->");
            } else {
                commented = content.startsWith(marker);
            }
            if (!commented) {
                return false;
            }
        }
        return true;
    }

    /** 切换：已注释→取消；未注释→添加 */
    public static String toggle(String block, int style) {
        return isCommented(block, style) ? uncommentBlock(block, style) : commentBlock(block, style);
    }

    /** 注释：所有非空行的注释符对齐到最小缩进列 */
    public static String commentBlock(String block, int style) {
        String[] lines = block.split("\n", -1);
        int minWs = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            int w = leadingWs(line);
            if (w < minWs) {
                minWs = w;
            }
        }
        if (minWs == Integer.MAX_VALUE) {
            minWs = 0;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) {
                sb.append('\n');
            }
            if (line.trim().isEmpty()) {
                sb.append(line);
                continue;
            }
            if (style == STYLE_XML) {
                sb.append(line, 0, Math.min(minWs, line.length()))
                        .append("<!-- ").append(rstrip(line.substring(Math.min(minWs, line.length()))))
                        .append(" -->");
            } else {
                sb.append(line, 0, minWs)
                        .append(markerText(style)).append(' ')
                        .append(line.substring(minWs));
            }
        }
        return sb.toString();
    }

    /** 取消注释：优先按对齐列移除，兼容旧行首注释 */
    public static String uncommentBlock(String block, int style) {
        String[] lines = block.split("\n", -1);
        String marker = markerText(style);
        int minWs = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            int w = leadingWs(line);
            if (w < minWs) {
                minWs = w;
            }
        }
        if (minWs == Integer.MAX_VALUE) {
            minWs = 0;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) {
                sb.append('\n');
            }
            if (line.trim().isEmpty()) {
                sb.append(line);
                continue;
            }
            if (style == STYLE_XML) {
                sb.append(uncommentXmlLine(line));
                continue;
            }
            int at = findMarker(line, marker, minWs);
            if (at < 0) {
                sb.append(line);
                continue;
            }
            int keep = at;
            sb.append(line, 0, keep);
            int rest = at + marker.length();
            if (rest < line.length() && line.charAt(rest) == ' ') {
                rest++;
            } else if (rest < line.length() && line.charAt(rest) == '\t') {
                rest++;
            }
            sb.append(line.substring(rest));
        }
        return sb.toString();
    }

    private static String uncommentXmlLine(String line) {
        int open = line.indexOf("<!--");
        String rs = rstrip(line);
        if (open < 0 || !rs.endsWith("-->")) {
            return line;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(line, 0, open);
        String mid = line.substring(open + 4, line.length() - 3);
        if (mid.startsWith(" ")) {
            mid = mid.substring(1);
        }
        if (mid.endsWith(" ")) {
            mid = mid.substring(0, mid.length() - 1);
        }
        sb.append(mid);
        return sb.toString();
    }

    /** 定位注释符：优先对齐列（minWs 可能落在前导空白内），其次内容起始处 */
    private static int findMarker(String line, String marker, int minWs) {
        if (minWs + marker.length() <= line.length()
                && line.startsWith(marker, minWs)) {
            return minWs;
        }
        int w = leadingWs(line);
        if (line.startsWith(marker, w)) {
            return w;
        }
        return -1;
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
