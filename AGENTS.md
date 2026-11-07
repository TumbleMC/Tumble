# AGENTS.md

面向 AI 智能体与开发者的 Tumble 项目操作指南。Tumble 是一个基于 Lophine 的 Minecraft 服务端分支，使用 ComfreyWeight（paperweight 2.0 风格）补丁系统，目标 Minecraft 版本 26.3。

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
- **从其他分支/服务端移植的功能必须在「外部移植功能登记」中登记**来源补丁、上游源码、Tumble 补丁与注入点；未登记视为未完成，版本更新时无法核对。

## 配置项（config）开发规范

- 配置文件类位于 `lophine-server/src/main/java/fun/bm/lophine/config/modules/<category>/`，使用 `@ConfigClassInfo` + `@ConfigInfo` 注解，并实现 `IConfigModule`。
- 模块通过 `ClassLoadUtil` 按包名自动扫描注册（`ConfigManager.registerConfig("lophine", ...)`），无需手动登记。
- **注意**：Tumble 的 `@ConfigInfo` 注解**不支持** `comments` 属性（上游 Lophine 新版支持，但 Tumble 基于旧版）。配置注释需写入语言文件。
- 语言文件位于 `lophine-server/src/main/resources/assets/lophine/lang/{en_us,zh_cn}.json`，键格式为 `lophine.<category>.<module_name>.<config_name>.comment`（category 使用 `EnumConfigCategory` 的 `baseKeyName`，如 `function`、`fixes`）。
- 语言文件键按字母序排列，插入新键时保持排序；若存在 `sortLangKeys` 任务则运行之（当前工程未注册该任务，手动保持排序即可）。
- 修改语言文件后提交到 `lophine-server` 工作仓库，并随配置一并重建 `rebuildLophineServerPatches`。

## 外部移植功能登记（强制）

从其他分支或服务端（DeerFolia、Carpet / Carpet TIS Addition、AMS、Leaves、Purpur 等）移植功能时，**必须在同一批提交内**在本节登记；未登记的移植视为未完成。登记内容要保证后来者只读本节就能完成版本更新时的重放与比对：

1. **来源标识**：仓库 URL、分支、移植时 commit、上游设计文档链接。
2. **上游补丁路径**：原端 patch 文件路径，用于 diff / 重放。
3. **上游源码路径**：被移植的 `.java` 文件（含行数）。
4. **Tumble 落点**：生成的 `NNNN-*.patch`（minecraft / lophine / api 分开列）+ 工作仓库中的 `.java` 路径。
5. **注入点清单**：每个被修改的 MC 文件 + 方法/字段 + 当前行号（行号会漂移，以方法名与锚点文本为准）。
6. **配置与 lang 键前缀**。
7. **更新核对清单**：上游更新后要 diff 哪些文件、重点检查哪些锚点。

### DeerFolia：AFK Network Optimization（AFK 网络优化）

**来源**

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/LunaDeerMC/DeerFolia |
| 分支 | `ver/26.3.x` |
| 移植时 commit | `10d9b98`（2026-11-05） |
| 设计文档 | https://github.com/LunaDeerMC/DeerFolia/blob/ver/26.3.x/docs/afk-network-optimization.md |
| 上游补丁 | `DeerFolia-server/minecraft-patches/features/0012-AFK-network-optimization.patch` |

**上游源码**

| 文件 | 行数 | 作用 |
|---|---|---|
| `DeerFolia-server/src/main/java/cn/lunadeer/mc/deerfolia/afknetwork/AfkNetworkManager.java` | 851 | 状态机、抑制分类、脏状态、重同步、统计 |
| `.../afknetwork/AfkSuppressibleCategory.java` | 32 | 5 个抑制类别枚举 |
| `.../afknetwork/AfkNetworkStatsCommand.java` | 33 | `/afknetstats` |
| `.../deerfolia/DeerFoliaConfiguration.java` 122-175 | — | `AfkNetworkOptimization` 配置 + `@PostProcess` 归一化 |

**Tumble 落点**

| 类型 | 路径 |
|---|---|
| minecraft 补丁 | `tumble-server/minecraft-patches/features/0003-DeerFolia-AFK-network-optimization.patch` |
| lophine 补丁（代码） | `tumble-server/lophine-patches/features/0006-DeerFolia-AFK-network-optimization.patch` |
| lophine 补丁（配置 + lang） | `tumble-server/lophine-patches/features/0007-Add-Config-of-DeerFolia-AFK-network-optimization.patch` |
| 代码 | `lophine-server/src/main/java/cn/lunadeer/mc/deerfolia/afknetwork/`：`AfkNetworkManager.java`(854)、`AfkSuppressibleCategory.java`(32)、`AfkNetworkStatsCommand.java`(33) |
| 配置 | `lophine-server/src/main/java/org/tumblemc/tumble/config/modules/optimizations/AfkNetworkConfig.java` |
| lang | `lophine-server/src/main/resources/assets/lophine/lang/{en_us,zh_cn}.json`，前缀 `tumble.optimizations.afk_network.*` |

> 移植时代码包名保留 `cn.lunadeer.mc.deerfolia.afknetwork`，目的是与上游逐行 diff；改名需同步 Manager 内 13 处引用与 MC 注入点里的 12 处全限定调用。

**注入点**（`tumble-server/src/minecraft/java/` 下，行号为 2026-11-07 状态）

| 文件 | 方法 / 锚点 | 行 | 注入内容 |
|---|---|---|---|
| `ca/spottedleaf/moonrise/patches/chunk_system/player/RegionizedPlayerChunkLoader.java` | `PlayerChunkLoaderData`，`flushDelayedTicketOps` 前 | 423/428/449 | `deerFolia$snapshotSentChunks`、`deerFolia$resyncClientChunks`、`deerFolia$resendClientChunks` |
| `net/minecraft/commands/Commands.java` | `registerCommands`，`FetchProfileCommand.register` 之后 | 249 | 注册 `AfkNetworkStatsCommand` |
| `net/minecraft/network/Connection.java` | `send(...)`，`packet.onPacketDispatch` 之前 | 457 | 抑制判定 + `onPacketDispatchFinish` 早退 |
| `net/minecraft/network/protocol/game/ClientboundEntityEventPacket.java` | `getEntity` 之后 | 48 | `deerFolia$getEntityId` |
| `net/minecraft/network/protocol/game/ClientboundMoveEntityPacket.java` | `getEntity` 之后 | 64 | `deerFolia$getEntityId` |
| `net/minecraft/network/protocol/game/ClientboundRotateHeadPacket.java` | `getEntity` 之后 | 48 | `deerFolia$getEntityId` |
| `net/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket.java` | `runUpdates` 之前 | 85 | `deerFolia$getChunkKey` |
| `net/minecraft/server/level/ChunkMap.java` | `TrackedEntity`；`broadcastRemoved`；`removePlayer`；`updatePlayer` 的 `addPairing` 后 | 1364/1368/1407/1415/1463 | `deerFolia$getEntityId`、`deerFolia$resyncPlayer`、3 处追踪开始/结束回调 |
| `net/minecraft/server/level/ServerPlayer.java` | `tick()` 顶部（`ScarpetNativeRemovals.tickPending` 早退之后）；`resetLastActionTime()` | 857/3375/3378 | `tickPlayer`；活动上报 + 拆分出 `deerFolia$resetLastActionTimeWithoutAfkActivity` |
| `net/minecraft/server/network/PlayerChunkSender.java` | `sendChunk` | 82/96 | 抽取 `deerFolia$resendChunkPacketOnly`（保留 Anti-Xray） |
| `net/minecraft/server/network/ServerGamePacketListenerImpl.java` | `handlePlayerInput`；`handleMoveVehicle`；`handleMovePlayer`；`onDisconnect`；`handleChat`；`handleChatCommand`；`handleSignedChatCommand` | 567/569/606/1607/2594/2729/2754/2794 | 活动上报、输入包不算活动、断线清理 |
| `net/minecraft/server/players/PlayerList.java` | `placeNewPlayer`，`new ServerGamePacketListenerImpl` 之后 | 377 | `onLogin` 初始化状态 |

**配置键**（`tumble_config/tumble_global_config.toml`，段 `[optimizations.afk_network]`）

`enabled`、`afk_threshold_ticks`、`afk_message_enabled`、`afk_enter_message`、`afk_exit_message`、`count_look_changes_as_activity`、`resync_on_resume`、`stats_enabled`、`suppression_whitelist_categories`、`max_suppressed_bytes_before_category_resync`

**更新核对清单**

1. 上游更新后，先看原端这几个文件是否有改动：
   `git -C DeerFolia log --oneline -- DeerFolia-server/src/main/java/cn/lunadeer/mc/deerfolia/afknetwork DeerFolia-server/minecraft-patches/features/0012-AFK-network-optimization.patch`
2. 对比 `0012` 与 Tumble `0003` 的注入 hunk，重点锚点：
   - `PlayerChunkLoaderData.sentChunks` / `flushDelayedTicketOps`
   - `ChunkMap.TrackedEntity.seenBy`、`serverEntity.removePairing/addPairing`
   - `ServerPlayer.tick()` 顶部是否新增早退（hook 必须在早退之后）
   - `ServerGamePacketListenerImpl` 输入/移动/聊天方法里的 Carpet 代码段
   - `Connection.send` 内 Paper 队列段
3. `deerFolia$*` accessor 签名变化时，同步修改 `AfkNetworkManager` 中 13 处调用。
4. 回归：编译通过 + 挂机阈值触发进入/退出消息 + `/afknetstats` 有数据 + 恢复活动后区块/实体无幽灵。

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
