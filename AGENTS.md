# AGENTS.md

面向 AI 智能体与开发者的 Tumble 项目操作指南。Tumble 是一个基于 Lophine 的 Minecraft 服务端分支，使用 ComfreyWeight（paperweight 2.0 风格）补丁系统，目标 Minecraft 版本 26.2。

## 核心概念：补丁系统如何运作

Tumble 的补丁是「源码修改 + git 提交 + gradle 任务重建」三层结构。**不要直接手改 `.patch` 文件**——它们是构建产物，会被 `rebuild*Patches` 任务覆盖。

架构分为三类目录（均在仓库根下）：

| 目录 | 作用 |
|---|---|
| `tumble-api/`、`tumble-server/` | 实际的 Gradle 构建工程 |
| `paper-api/`、`paper-server/`、`lophine-api/`、`lophine-server/`、`tumble-server/src/minecraft/` | **工作仓库**（working repos），独立 git 仓库，补丁应用后的源码树，在此编辑源码 |
| `tumble-api/*-patches/`、`tumble-server/*-patches/` | **补丁目录**，存放生成的 `.patch` 文件（被根仓库跟踪） |

工作仓库是独立 git 仓库，其 `file` tag 是 feature 补丁的基线：
- `base` tag = 上游导入点
- `file` tag = 文件补丁（file patches）应用后的点
- `file..HEAD` 之间的每个提交 = 一个 feature patch，会被重建任务导出为 `NNNN-主题.patch`

## 补丁目录与工作仓库的对应关系

| 工作仓库（编辑处） | 补丁目录（生成处） | 重建任务（在 `tumble-server` 或根项目运行） |
|---|---|---|
| `tumble-server/src/minecraft/java`、`resources` | `tumble-server/minecraft-patches/` | `:tumble-server:rebuildMinecraftPatches` |
| `lophine-server/` | `tumble-server/lophine-patches/` | `:tumble-server:rebuildLophineServerPatches` |
| `paper-server/` | `tumble-server/paper-patches/` | `:tumble-server:rebuildPaperServerPatches` |
| `lophine-api/` | `tumble-api/lophine-patches/` | `rebuildLophineApiPatches`（根项目） |
| `paper-api/` | `tumble-api/paper-patches/` | `rebuildPaperApiPatches`（根项目） |
| `lophine-server/build.gradle.kts` 等单文件 | `tumble-server/build.gradle.kts.patch` | `rebuildLophineSingleFilePatches` |

## 添加补丁的标准流程

1. **应用补丁栈**（首次开发或上游更新后必做）：
   ```bash
   ./gradlew applyAllPatches
   ```

2. **在工作仓库中编辑源码**：
   - 服务端通用逻辑（`net/minecraft/...`）：编辑 `tumble-server/src/minecraft/java/`
   - Lophine 自有代码（`fun.bm.lophine/...`）：编辑 `lophine-server/`
   - Lophine API（`org.purpurmc/...` 等）：编辑 `lophine-api/`
   - Paper 服务端代码：编辑 `paper-server/`

3. **提交到工作仓库**（不是根仓库）：
   ```bash
   git -C <工作仓库> add .
   git -C <工作仓库> commit -m "<描述性主题>"
   ```
   提交信息会决定生成补丁的文件名（如 `Add Purpur Anvil Modify` → `0001-Add-Purpur-Anvil-Modify.patch`）。提交信息遵循上游风格。

4. **重建补丁**：
   ```bash
   ./gradlew :tumble-server:rebuildAllServerPatches   # 服务端全部（minecraft + lophineServer + paperServer）
   ./gradlew rebuildLophinePatches                     # API 全部（lophineApi + paperApi）
   ```
   或按需精确重建单个补丁目录（见上表）。重建任务会自动 `git add` 生成的 `.patch` 文件到根仓库。

5. **提交根仓库**的 `.patch` 文件（提交信息参考现有风格，如 `feat: add ...`）：
   ```bash
   git add tumble-server/minecraft-patches tumble-server/lophine-patches tumble-api/lophine-patches
   git commit -m "feat: <描述>"
   ```

6. **验证编译**：
   ```bash
   ./gradlew :tumble-api:compileJava :tumble-server:compileJava
   ```

## 修改已有补丁

1. 在对应工作仓库中直接修改源码。
2. 若修改的是**新建文件**（工作仓库中新增的文件，属于文件补丁），先 `git add`（不要提交），再运行 `fixup*FilePatches` 类任务将其并入文件补丁基线；若只是修改已有补丁覆盖的源码，直接提交即可。
3. `git commit`，然后运行对应的 `rebuild*Patches` 任务。
4. 未完成的（半提交）补丁可通过 `--fixup`/`--squash` 提交后 `git rebase -i --autosquash file` 合并。

## 关键注意事项

- **不要手动编辑 `.patch` 文件**，一律通过「改源码 → 提交工作仓库 → rebuild」完成。
- 三个核心工作仓库（`lophine-api`、`lophine-server`、`tumble-server/src/minecraft/java`）是独立的 git 仓库，提交时要 `git -C <路径>` 指定。
- `tumble-server/src/minecraft/java` 与 `resources` 是**两个**独立的 git 仓库，分别提交。
- 修改 `tumble-server/src/minecraft` 后，根仓库的 `.gitignore` 已忽略该目录，只需提交生成的 patch。
- 生成补丁前先 `./gradlew applyAllPatches`，确保工作仓库处于补丁基线之上，否则 `file..HEAD` 会混入无关提交。
- CRLF 警告（`LF will be replaced by CRLF`）可忽略，属 Windows 环境正常现象。

## 配置项（config）开发规范

- 配置文件类位于 `lophine-server/src/main/java/fun/bm/lophine/config/modules/<category>/`，使用 `@ConfigClassInfo` + `@ConfigInfo` 注解，并实现 `IConfigModule`。
- 模块通过 `ClassLoadUtil` 按包名自动扫描注册（`ConfigManager.registerConfig("lophine", ...)`），无需手动登记。
- **注意**：Tumble 的 `@ConfigInfo` 注解**不支持** `comments` 属性（上游 Lophine 新版支持，但 Tumble 基于旧版）。配置注释需写入语言文件。
- 语言文件位于 `lophine-server/src/main/resources/assets/lophine/lang/{en_us,zh_cn}.json`，键格式为 `lophine.<category>.<module_name>.<config_name>.comment`（category 使用 `EnumConfigCategory` 的 `baseKeyName`，如 `function`、`fixes`）。
- 语言文件键按字母序排列，插入新键时保持排序；若存在 `sortLangKeys` 任务则运行之（当前工程未注册该任务，手动保持排序即可）。
- 修改语言文件后提交到 `lophine-server` 工作仓库，并随配置一并重建 `rebuildLophineServerPatches`。

## 常用命令速查

```bash
./gradlew applyAllPatches                      # 应用全部补丁，建立工作仓库
./gradlew :tumble-server:rebuildAllServerPatches  # 重建服务端全部补丁
./gradlew rebuildLophinePatches                # 重建 API 全部补丁
./gradlew :tumble-server:rebuildMinecraftPatches  # 仅重建 minecraft 补丁
./gradlew :tumble-server:rebuildLophineServerPatches # 仅重建 lophine 服务端补丁
./gradlew rebuildLophineApiPatches             # 仅重建 lophine API 补丁
./gradlew :tumble-api:compileJava :tumble-server:compileJava  # 编译验证
./gradlew createPaperclipJar                   # 产出可运行服务端 jar
```

## 环境要求

- Git（需启用长路径支持，Windows 上参考 Git for Windows 文档）
- JDK 25 或更高
