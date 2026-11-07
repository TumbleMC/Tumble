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

- 配置类位于 `lophine-server/src/main/java/org/tumblemc/tumble/config/modules/<category>/`（Tumble 自有）或 `fun/bm/lophine/config/modules/<category>/`（Lophine 上游），使用 `@ConfigClassInfo` + `@ConfigInfo` 即可，**不存在** `IConfigModule` 之类的接口要实现。
- 配置类字段必须是 `static` 且非 `final`（`WorldValue` 字段除外）；配置类本身只被反射实例化来承载 `@NeedRun` 回调，实例字段不会被读取。
- 模块通过 `ClassLoadUtil` 按包名自动扫描注册（`ConfigManager.initConfigs()` 里的 `luminol` / `lophine` / `tumble` 三个包），无需手动登记。
- **注意**：Tumble 的 `@ConfigInfo` 注解**不支持** `comments` 属性（上游 Lophine 新版支持，但 Tumble 基于旧版）。配置注释写入语言文件；没有 comment 键的配置不会出现在 TOML 中。
- 语言文件位于 `lophine-server/src/main/resources/assets/lophine/lang/{en_us,zh_cn}.json`。Tumble 模块的键格式为 `tumble.<category>.<module_name>[.<directory...>].<config_name>.comment`，类注释键为 `tumble.<category>.<module_name>.comment`（category 使用 `EnumConfigCategory` 的 `baseKeyName`，如 `function`、`optimizations`）；`@ConfigInfo(directory = {...})` 的每一段都要写进键里。
- 语言文件的键按整串字母序排列；插入新键时放到对应位置，不要整体重排文件（避免无关 diff）。
- **重启才生效的配置**（结构生成规则、模板缓存等启动期状态）：给每个字段加 `@DoNotLoad(when = EnumLoadType.RELOAD)`，并在 `@NeedRun(when = EnumRunnableType.ON_LOADED)` 方法里做一次性初始化。这样 reload 不会覆盖内存字段，游戏内改值只写文件并返回 `SAVED_FOR_RESTART`。
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
8. **上游原样与 Tumble 改动的界限**：每个条目必须有一张「上游原样 vs Tumble 改动」表，逐项标注上游原样移植的部分与 Tumble 独有部分（新增开关、新增类、Luminol 配置适配、改写的初始化逻辑、只改注释的注入 hunk）。Tumble 独有的代码要带 `// Tumble` 或 `// Tumble start - ... // Tumble end` 标记；Tumble 独有类放在 `org.tumblemc.tumble.*`。上游更新时只 diff「上游原样」部分，Tumble 独有部分单独回归，不要因为上游没有对应物而误删。

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
| `DeerFolia-server/src/main/java/cn/lunadeer/mc/deerfolia/afknetwork/AfkNetworkManager.java` | 954 | 状态机、抑制分类、脏状态、重同步、统计 |
| `.../afknetwork/AfkSuppressibleCategory.java` | 39 | 5 个抑制类别枚举 |
| `.../afknetwork/AfkNetworkStatsCommand.java` | 38 | `/afknetstats` |
| `.../deerfolia/DeerFoliaConfiguration.java` 122-175 | — | `AfkNetworkOptimization` 配置 + `@PostProcess` 归一化 |

**Tumble 落点**

| 类型 | 路径 |
|---|---|
| minecraft 补丁 | `tumble-server/minecraft-patches/features/0003-DeerFolia-AFK-network-optimization.patch` |
| lophine 补丁（代码） | `tumble-server/lophine-patches/features/0006-DeerFolia-AFK-network-optimization.patch` |
| lophine 补丁（配置 + lang） | `tumble-server/lophine-patches/features/0007-Add-Config-of-DeerFolia-AFK-network-optimization.patch` |
| 代码 | `lophine-server/src/main/java/cn/lunadeer/mc/deerfolia/afknetwork/`：`AfkNetworkManager.java`(958)、`AfkSuppressibleCategory.java`(39)、`AfkNetworkStatsCommand.java`(38) |
| 配置 | `lophine-server/src/main/java/org/tumblemc/tumble/config/modules/optimizations/AfkNetworkConfig.java` |
| lang | `lophine-server/src/main/resources/assets/lophine/lang/{en_us,zh_cn}.json`，前缀 `tumble.optimizations.afk_network.*` |

> 移植时代码包名保留 `cn.lunadeer.mc.deerfolia.afknetwork`，目的是与上游逐行 diff；改名需同步 Manager 内 11 处 `AfkNetworkConfig` 读取与 MC 注入点里的 16 处全限定调用。

**上游原样 vs Tumble 改动**

| 部分 | 归属 | 说明 |
|---|---|---|
| `AfkNetworkManager` 状态机、抑制分类、脏状态、重同步、统计主体 | 上游原样 | 与上游的差异仅在配置读取与消息开关，见下行 |
| `AfkSuppressibleCategory`、`AfkNetworkStatsCommand` | 上游原样 | 与上游逐行一致（diff 0 行） |
| MC 12 个文件的注入 hunk | 上游原样 | 逻辑同上游 `0012`，仅行尾追加 `// Tumble` 注释 |
| 配置读取改 `AfkNetworkConfig.*` | **Tumble 独有** | `AfkNetworkManager` 内 11 处，均带 `//Tumble config.`；替换上游 `DeerFoliaConfiguration.afkNetworkOptimization`，另有 3 行上游调用保留为注释（仅作 diff 参照） |
| `afk_message_enabled` 开关 | **Tumble 独有** | 上游只有「空消息 = 关闭」语义；Tumble 增加独立开关同时控制进入/退出消息 |
| 默认进出消息文案 | **Tumble 独有** | 由 `DeerFolia` 改为 `Tumble`（`AfkNetworkConfig` 默认值 + lang 注释） |
| `AfkNetworkConfig` + `@NeedRun(ON_LOADED)` 归一化 | **Tumble 独有** | 替代上游 `@PostProcess normalizeAfkNetworkOptimization()`；白名单未知类别在 `refreshConfiguration()` 中静默忽略（上游会 warn），消息与阈值钳制保持一致 |

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
3. `deerFolia$*` accessor 签名变化时，同步修改 `AfkNetworkManager` 中 11 处 `AfkNetworkConfig` 读取与 16 处 MC 侧全限定调用；Tumble 独有部分（`afk_message_enabled`、消息文案、配置模块）随配置一并回归。
4. 回归：编译通过 + 挂机阈值触发进入/退出消息 + `/afknetstats` 有数据 + 恢复活动后区块/实体无幽灵。

### DeerFoliaPlus：Bedrock-style Stronghold Generation（基岩版风格要塞生成）

**来源**

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/LunaDeerMC/DeerFoliaPlus |
| 分支 | `ver/26.3.x` |
| 移植时 commit | `0cf3b7b`（2026-11-07） |
| 功能说明 | `README.md` 第 8 行、配置表 37-43 行 |
| 上游补丁 | `DeerFoliaPlus-server/minecraft-patches/features/0004-Bedrock-style-stronghold-generation.patch` |
| 上游配置 | `DeerFoliaPlus-server/src/main/java/cn/lunadeer/mc/deerfoliaplus/configurations/DeerFoliaPlusConfiguration.java` 57-67 行 |
| 上游测试 | `DeerFoliaPlus-server/src/test/java/cn/lunadeer/mc/deerfoliaplus/upgrade/StrongholdPlacementUpgradeTest.java` |

**行为与开关**

| `enabled` | `keep_vanilla_rings` | 结果 |
|---|---|---|
| false | 任意 | 纯原版 128 环 |
| true | false | 全部随机（替换原版 strongholds 放置） |
| true | true | 环内原版 128 + 环外随机（混合模式） |

**上游原样 vs Tumble 改动**

| 部分 | 归属 | 说明 |
|---|---|---|
| DeerFoliaPlus `0004-Bedrock-style-stronghold-generation.patch`（只改 `ChunkGeneratorStructureState.injectSpigot`） | 上游原样 | `enabled` 时把原版 strongholds 替换成 `RandomSpreadStructurePlacement(spacing, separation, LINEAR, 10387319)`，即纯全随机 |
| 上游配置 `BedrockStrongholdGeneration`（`enabled`/`spacing`/`separation`，默认 false/48/12） | 上游原样 | 见来源表 57-67 行 |
| `keep_vanilla_rings` 开关 + 混合模式分支 | **Tumble 独有** | 上游没有「环内保留、环外随机」能力；Tumble 在 minecraft `0005` 里加 `enabled && keep_vanilla_rings` 时保留原版环集合（`newHolder = structureSet`） |
| `org.tumblemc.tumble.worldgen.BedrockOuterStrongholdPlacement`（101 行） | **Tumble 独有** | `RandomSpreadStructurePlacement` 子类，`isPlacementChunk`/`applyAdditionalChunkRestrictions` 排除原版环区（R=1535 chunks） |
| `BedrockStrongholdConfig` + lang + `@DoNotLoad(RELOAD)` | **Tumble 独有** | Luminol 配置模块替代上游 YAML；4 键；启动期读取，改后重启生效 |
| 补丁拆分 | **Tumble 独有** | 上游 1 个补丁；Tumble 拆成 minecraft `0004`（全随机）/ `0005`（混合）+ lophine `0008`（placement 类）/ `0009`（配置 + lang） |

**Tumble 落点**

| 类型 | 路径 |
|---|---|
| minecraft 补丁（全随机） | `tumble-server/minecraft-patches/features/0004-Add-DeerFolia-Bedrock-Style-Stronghold-Generation.patch` |
| minecraft 补丁（混合模式） | `tumble-server/minecraft-patches/features/0005-Add-Keep-Vanilla-Stronghold-Rings-when-DeerFolia-Bed.patch` |
| 自定义 placement 类 | `tumble-server/lophine-patches/features/0008-Add-Keep-Vanilla-Stronghold-Rings-when-DeerFolia-Bed.patch` |
| 配置 + lang | `tumble-server/lophine-patches/features/0009-Add-DeerFolia-Bedrock-Style-Stronghold-Generation-an.patch` |
| 类文件 | `lophine-server/src/main/java/org/tumblemc/tumble/worldgen/BedrockOuterStrongholdPlacement.java` |
| 配置类 | `lophine-server/src/main/java/org/tumblemc/tumble/config/modules/function/BedrockStrongholdConfig.java` |
| lang | `lophine-server/src/main/resources/assets/lophine/lang/{en_us,zh_cn}.json`，前缀 `tumble.function.bedrock_stronghold.*` |

**注入点**（`tumble-server/src/minecraft/java/`，行号为 2026-11-07 状态）

| 文件 | 方法 / 锚点 | 行 | 注入内容 |
|---|---|---|---|
| `net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java` | `injectSpigot`：strongholds 判断分支（Tumble start 在 84 行，上游 DeerFoliaPlus 块在 88-109 行） | 84-109 | `enabled && !keep_vanilla_rings` 时替换为 RandomSpread（salt `10387319`）；`keep_vanilla_rings` 时保留 registry holder |
| 同上 | `injectSpigot`：stream 收集之后 | 145-164 | `enabled && keep_vanilla_rings` 时追加 `Holder.direct(BedrockOuterStrongholdPlacement)`；返回 `List.copyOf` |

**自定义 placement 依赖（上游更新时需要核对的内部 API）**

- `RandomSpreadStructurePlacement(int spacing, int separation, RandomSpreadType, int salt)`
- `protected boolean isPlacementChunk(ChunkGeneratorStructureState, int, int)`
- `public boolean applyAdditionalChunkRestrictions(int, int, long, @Nullable Integer)`
- `ConcentricRingsStructurePlacement#distance()/spread()/count()`
- `ChunkGeneratorStructureState#generateRingPositions` 的环增长公式（类内 `computeRingZoneRadius` 复制了该算法）
- `Holder.direct` / `Holder.is(ResourceKey)` 语义（direct holder 无 key，`is(ResourceKey)` 恒 false）

**配置键**（`tumble_config/tumble_global_config.toml`，段 `[function.bedrock_stronghold]`）

`enabled`、`keep_vanilla_rings`、`spacing`、`separation`

**更新核对清单**

1. 上游改动检查：
   `git -C DeerFoliaPlus log --oneline -- DeerFoliaPlus-server/minecraft-patches/features/0004-Bedrock-style-stronghold-generation.patch DeerFoliaPlus-server/src/main/java/cn/lunadeer/mc/deerfoliaplus/configurations/DeerFoliaPlusConfiguration.java DeerFoliaPlus-server/src/test/java/cn/lunadeer/mc/deerfoliaplus/upgrade/StrongholdPlacementUpgradeTest.java`
2. 对比 DeerFoliaPlus `0004` 与 Tumble `0004` / `0005` 的 hunk，重点锚点：`injectSpigot` 里的 Spigot 种子 switch、Paper `KeyedRandomSpreadStructurePlacement` 分支、`StructureSet` 构造器。
3. `BedrockOuterStrongholdPlacement` 依赖的四组内部 API（见上）任一签名变化 → 编译期报错，按新签名改。
4. 环半径公式:若 `generateRingPositions` 的 `distance/spread/count` 或 jitter/biome 搜索半径变化,同步 `computeRingZoneRadius`;vanilla 32/3/128 当前得出 `R=1535 chunks`。
5. 回归:
   - 三种开关组合各自的分布(全随机/混合/纯原版);
   - `/locate structure minecraft:stronghold` 环内命中原版环、环外命中随机;
   - 环边界无重叠、环内随机数为 0;
   - Spigot `stronghold-seed` 仍只影响环;Lophine Secure Seed 下随机分布走 crypto random;
   - datapack 把 strongholds 改成非 `concentric_rings` 时混合模式不追加外层集合。
6. Tumble 独有部分（`0005`、`0008`、`0009`、`BedrockOuterStrongholdPlacement`、`keep_vanilla_rings`）在上游没有对应物，上游更新后必须单独回归：全随机 / 混合 / 纯原版三种组合 + `/locate` 环边界。

### DeerFoliaPlus：Vanilla Structure Generation Fixes（原版结构生成修复）

**来源**

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/LunaDeerMC/DeerFoliaPlus |
| 分支 | `ver/26.3.x` |
| 移植时 commit | `0cf3b7b`（2026-11-07） |
| 设计文档 | `docs/structure-generation-fixes.md`（207 行，含问题编号与模板对照） |
| 功能说明 | `README.md` 第 13 行、配置表 79-88 行 |
| 上游补丁 | `DeerFoliaPlus-server/minecraft-patches/features/0009-Configurable-vanilla-structure-generation-repairs.patch`（4 个 MC 文件，5 个 hunk） |
| 上游配置（YAML） | `DeerFoliaPlus-server/src/main/java/cn/lunadeer/mc/deerfoliaplus/configurations/StructureGenerationFixesConfiguration.java`（254 行，含 `validate()` 预校验） |
| 上游装配 | 同目录 `DeerFoliaPlusConfiguration.java` 19-25 行（`validate` + `initialize`）、31 行字段 |
| 上游测试 | `DeerFoliaPlus-server/src/test/java/cn/lunadeer/mc/deerfoliaplus/worldgen/` 8 个测试类 + `upgrade/StrongholdPlacementUpgradeTest.java` |

**行为**

- 总开关 `enabled`（默认 true）+ 44 个独立修复开关；关闭总开关时全部修复不生效。
- 修复在结构模板加载时重写 NBT（`StructureGenerationFixes.openTemplate`），只对原版 `minecraft:core` 包、且与内置基线哈希一致的模板/资源生效；datapack 覆盖模板、或模板/依赖资源被改动时原样返回。
- 启动时快照一次开关；模板被 MC 缓存，因此**必须重启生效**，只影响之后生成的区块，不改写已有世界。

**上游源码（7 个类 + 基线）**

| 文件（`DeerFoliaPlus-server/src/main/java/cn/lunadeer/mc/deerfoliaplus/worldgen/`） | 上游行数 | Tumble 行数 | 作用 |
|---|---|---|---|
| `StructureGenerationFixes.java` | 266 | 259 | 开关快照、模板/资源基线守卫、Catalog 载入 |
| `StructureRepair.java` | 49 | 49 | 44 个修复枚举 |
| `StructureTemplateRepairs.java` | 211 | 211 | 结构模板 NBT 修复主体 |
| `TreeStructureRepairs.java` | 283 | 283 | 营地/帐篷树木修复 |
| `VillagePileRepairs.java` | 70 | 70 | 浅层世界堆放装饰 |
| `VillageStructureRepairs.java` | 136 | 136 | 村庄地基/道路连接空间 |
| `AncientCityCampRepairs.java` | 70 | 70 | 远古城市营地变体 |
| `src/main/resources/deerfoliaplus/structure-generation-fixes-baseline.json` | 1875 | 1875 | 原版模板/资源哈希基线（两端口 sha256 一致） |
| `configurations/StructureGenerationFixesConfiguration.java` | 254 | 未移植（改为 Luminol 配置模块） | YAML 配置 + 预校验 |

**Tumble 落点**

| 类型 | 路径 |
|---|---|
| minecraft 补丁 | `tumble-server/minecraft-patches/features/0006-Add-DeerFolia-Configurable-vanilla-structure-generat.patch` |
| lophine 补丁（代码 + 基线） | `tumble-server/lophine-patches/features/0010-Add-DeerFolia-Configurable-vanilla-structure-generat.patch` |
| lophine 补丁（配置 + lang） | `tumble-server/lophine-patches/features/0011-Add-DeerFolia-vanilla-structure-generation-fixes-Con.patch` |
| 代码 | `lophine-server/src/main/java/cn/lunadeer/mc/deerfoliaplus/worldgen/`（7 个文件） |
| 基线资源 | `lophine-server/src/main/resources/deerfoliaplus/structure-generation-fixes-baseline.json`（sha256 `580921b3…`） |
| 配置类 | `lophine-server/src/main/java/org/tumblemc/tumble/config/modules/function/StructureGenerationFixesConfig.java`（209 行） |
| lang | `lophine-server/src/main/resources/assets/lophine/lang/{en_us,zh_cn}.json`，前缀 `tumble.function.structure_generation_fixes.*`（46 键 = 类注释 + `enabled` + 44 分项） |

**上游原样 vs Tumble 改动**

| 部分 | 归属 | 说明 |
|---|---|---|
| 6 个 worldgen 类（除 `StructureGenerationFixes`） | 上游原样 | 与上游逐行一致（diff 0 行） |
| `StructureGenerationFixes` 的 `openTemplate`、Catalog、基线校验 | 上游原样 | 逻辑未改 |
| 基线 JSON | 上游原样 | 内容与上游 sha256 一致 |
| MC 4 个文件的 5 个注入 hunk | 上游原样 + 注释 | 与上游 `0009` 的 hunk 一一对应，仅行尾追加 `// Tumble` |
| `initialize()` / `snapshot()` 改读静态配置字段 | **Tumble 独有** | 上游从 YAML 配置对象读；Tumble 直接读 `StructureGenerationFixesConfig`，标记 `// Tumble start/end` |
| `StructureGenerationFixesConfig` 配置模块 | **Tumble 独有** | Luminol `@ConfigInfo(directory = {...})` 子表；45 键（`enabled` + 44 分项）；全部 `@DoNotLoad(RELOAD)`；`@NeedRun(ON_LOADED)` 调 `initialize()` |
| 启动装配位置 | **Tumble 独有** | `ConfigManager.loadConfigFiles()`（`DedicatedServer` 242 行，`initServer` 内、世界生成前）代替上游 `DeerFoliaPlusConfiguration.load()` |
| 删除的上游逻辑 | **Tumble 独有** | YAML `validate()`（Luminol TOML 自带类型校验 + `allowAutoReset`）、`initialized` 标志与 reload 变化告警（`DoNotLoad` 已保证 reload 不写字段） |
| 上游测试（worldgen 8 个 + upgrade 1 个） | 未移植 | Tumble 无 `lophine-server/src/test`，只移植生产代码 |

**注入点**（`tumble-server/src/minecraft/java/`，行号为 2026-11-07 状态）

| 文件 | 方法 / 锚点 | 行 | 注入内容 |
|---|---|---|---|
| `net/minecraft/world/level/levelgen/feature/BlockPileFeature.java` | `place()`：`origin.getY() < level.getMinY() + ...` | 29 | `VillagePileRepairs.minimumClearance(this)` |
| `net/minecraft/world/level/levelgen/structure/pools/FeaturePoolElement.java` | `place(..., StructureTemplateManager, ...)` 的 `return` | 82 | `VillagePileRepairs.place(...)`，改用 `resourceManagerSource.deerFoliaPlusResourceManager()` |
| `net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java` | `tryPlacingChildren`：`targetElement` 选择 | 378 | `AncientCityCampRepairs.select(...)` |
| 同上 | 地基/上下文形状判定 | 450-463 | `VillageStructureRepairs.candidateSpace(...)` + `contextFree` 回填 |
| `net/minecraft/world/level/levelgen/structure/templatesystem/loader/ResourceManagerTemplateSource.java` | `load(Identifier)` | 32-36 / 41 | 新增 `deerFoliaPlusResourceManager()`；`load` 改走 `StructureGenerationFixes.openTemplate(...)` |

**配置键**（`tumble_config/tumble_global_config.toml`，段 `[function.structure_generation_fixes]`）

`enabled` + 6 组 44 个开关：`villages.*`（15）、`trail_ruins.*`（6）、`trial_chambers.*`（3）、`bastions.*`（1）、`ancient_cities.*`（4）、`abandoned_camps.campsite_pale_garden2`（1）、`abandoned_camps.trees.*`（14）。全部 `@DoNotLoad(RELOAD)`，改后重启生效。

**更新核对清单**

1. 上游改动检查：
   `git -C DeerFoliaPlus log --oneline -- DeerFoliaPlus-server/src/main/java/cn/lunadeer/mc/deerfoliaplus/worldgen DeerFoliaPlus-server/src/main/resources/deerfoliaplus DeerFoliaPlus-server/minecraft-patches/features/0009-Configurable-vanilla-structure-generation-repairs.patch`
2. 逐文件 diff 7 个 worldgen 类：只有 `StructureGenerationFixes.java` 允许有 Tumble 差异（import、`initialize`/`snapshot`、`// Tumble` 标记），其余必须 0 行差异，否则按上游合入。
3. 对比上游 `0009` 与 Tumble `0006` 的 hunk，重点锚点：`BlockPileFeature.place`、`FeaturePoolElement.place`、`JigsawPlacement` 的 `candidateFree`/`contextFree`、`ResourceManagerTemplateSource.load`；上游方法签名（`StructureTemplateManager`、`resourceManagerSource`、`VoxelShape`/`Shapes.joinIsNotEmpty`）变化会直接在编译期报错。
4. 基线资源：与上游 sha256 必须一致；MC 数据版本升级或 vanilla 模板变动后要按上游 `StructureGenerationFixture`/`VanillaRepairResources` 重新生成，否则修复会静默不生效。
5. 依赖的 `net.minecraft` API：`NbtIo.readCompressed`/`writeCompressed`、`NbtAccounter.create`、`Resource#sourcePackId`/`knownPackInfo`、`Identifier.parse`；任一变更需同步 `StructureGenerationFixes`。
6. 回归：总开关关闭时原版模板不变；单项关闭时对应结构保持原版；全开时新建世界结构生成无报错；datapack 覆盖模板时回退原版；改配置后必须重启才生效。

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
