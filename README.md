# LoongMD (KMP)

一个使用 Kotlin Multiplatform + Compose Multiplatform 实现的 `.md` 文件查看/编辑 App。

## 已实现功能

- KMP 工程结构：`commonMain` + `desktopMain` + `androidMain`
- Desktop:
  - 选择任意目录
  - 递归扫描目录下所有 `.md` 文件
  - 左侧文件列表 + 右侧 Markdown 预览/编辑
  - 编辑后保存到原 `.md` 文件
- Android:
  - 提供演示数据，验证 `commonMain` UI 与解析/编辑逻辑
- Markdown 渲染（增强）：
  - 标题：`#` 到 `######`
  - 段落、引用块、分割线
  - 有序/无序列表、任务列表（`[x]` / `[ ]`）
  - 代码块（含语言标签）与行内代码
  - 行内样式：粗体、斜体、删除线、链接

## 项目结构

- `composeApp/src/commonMain/kotlin/com/loongmd`
- `composeApp/src/desktopMain/kotlin/com/loongmd`
- `composeApp/src/androidMain/kotlin/com/loongmd`

## 运行方式

### Desktop

```bash
GRADLE_USER_HOME=.gradle-local ./gradlew :composeApp:run
```

### Android

```bash
GRADLE_USER_HOME=.gradle-local ./gradlew :composeApp:assembleDebug
```

然后用 Android Studio 打开项目运行 `composeApp` 模块。

## 说明

- 已包含 Gradle Wrapper，优先使用 `./gradlew`，避免依赖本机 Gradle 安装状态。
- 仅验证 Desktop 逻辑时，可直接执行：

```bash
GRADLE_USER_HOME=.gradle-local ./gradlew :composeApp:compileKotlinDesktop
```

- 构建 Android 目标时需要本机已安装 Android SDK，并在 `local.properties` 中配置 `sdk.dir`（或设置 `ANDROID_HOME` 环境变量）。
