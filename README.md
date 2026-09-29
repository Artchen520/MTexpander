# 代码修补 (Code Patcher)

适用于 [MT管理器](https://mt2.cn) 的插件（.mtp），提供 IDEA 风格的代码编辑辅助功能。

## 功能

- **缩写一键展开**：输入缩写（`logd`、`psvm`、`fori`…），光标停在缩写词后点击底部工具栏「展开片段」，即可展开为完整代码；多行片段自动保持缩进，光标停在参数位置
- **批量注释**：选中多行代码，浮动菜单一键按行注释/取消注释；注释符对齐到块内最小缩进列，并跟随文件类型（`//`、`#`、`<!-- -->`、`--`）
- **环绕包裹**：选中一段代码包上 `try-catch`、`for`、`if`、`lambda` 等模板（类似 IDEA Surround With），选中内容自动加深缩进
- **分组系统**：缩写库与环绕模板按分组管理，每组可限定适用的文件类型（如仅 `.java`）；支持编辑器内开关面板（临时/持久）
- **全部自定义**：内置「内置」「Java」分组开箱即用且可修改，缩写与环绕模板均为文本格式，随时增删

## 安装要求

- MT 管理器 2.26.9+（需 VIP 安装插件）
- 在 MT 中打开 `.mtp` 文件安装

## 下载成品

最新插件安装包：[`releases/代码修补_v4.4.mtp`](releases/代码修补_v4.4.mtp)

下载后用 MT 管理器打开即可安装。

## 从源码构建

```
git clone https://github.com/Artchen520/MTexpander.git
```

- JDK 17+ / AGP 8.13 / Gradle 8.13（项目自带 wrapper）
- Android Studio 打开，或直接执行：

```
./gradlew :snippets:packageReleaseMtp
```

产物位于 `snippets/build/outputs/mt-plugin/`。

## 分组文件格式

分组为 UTF-8 文本文件（一行一条）：

```
#适用：java                       ← 组默认适用扩展名（省略=所有文件）
logd = Log.d(TAG, $0$); @java     ← 行尾 @java 单条限定（@ 空=强制通用）
判空执行 = if ($0$ != null) {\n    $S$\n}    ← 环绕模板必须含 $S$
```

- `$0$` = 展开后光标位置；`$S$` = 选中内容（仅环绕模板）；`\n` = 模板内换行
- `#` 开头为注释；不同分组允许同名条目，按组名顺序先匹配生效

## 环境与依赖

- 插件系统：MT 插件 SDK v3（`bin.mt.plugin` 3.0.0，[官方文档](https://mt2.cn/guide/plugin-v3/plugin-intro.html)）
- compileSdk 36 / minSdk 21 / Java 17

## License

暂未指定（作者保留所有权利）。
