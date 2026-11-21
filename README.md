# 卡拉彼丘 QQ 机器人插件（Celestia）

在 QQ 群聊中直接发送指令，查询《卡拉彼丘》的战绩、排行榜、游戏资料与福利信息，全部以图片返回。

* **框架**：[Kokona](https://github.com/szzzz666/Kokona)（Java 21 / OneBot 11）
* **数据来源**：Dakanu（战绩）+  biligame 卡拉彼丘 WIKI（资料）
* **触发方式**：群聊直接发送，无需 @机器人

---

## 快速开始

```bash
./build.sh          # 输出 build/Celestia.jar
```

把 jar 放进 Kokona 的 `plugins/` 目录并重启即可。需要 JDK 21+，**无需安装任何字体或图片库**（相关依赖已内置）。

首次运行会在 `plugins/Celestia/` 生成配置文件与素材缓存。

---

## 指令一览

### 基础指令

| 指令 | 参数 | 说明 | 示例 |
| :--- | :--- | :--- | :--- |
| `帮助` | — | 全部指令一览（静态图） | `帮助` |
| `菜单` | — | 逐层浏览的功能菜单 | `菜单` |
| `看看` | 昵称 / UID | 玩家档案概览 | `看看米雪儿` |
| `看看对局` | 昵称 + 对局号 | 单场对局详情 | `看看对局米雪儿114514` |
| `排行` | [页数] | 全服段位排行榜 | `排行2` |
| `兑换码` | — | 可用兑换码（自动过滤失效） | `兑换码` |
| `BGM` | [序号/曲名] | 曲目列表与点播 | `BGM` / `BGM 5` |
| `wiki` | 关键词 | 查询 WIKI 任意页面 | `wiki 信用分` |
| `壁纸` | [序号/随机] | 缩略图列表 / 取原图 / 随机 | `壁纸` · `壁纸 3` · `壁纸 随机` |
| `随机壁纸` | — | 随机发送一张 1920px 原图 | `随机壁纸` |

### 页面直达指令

菜单能到达的页面都有对应指令，知道要查什么时不必逐层点选。

**图鉴页**（参数为页码，每页 36 个条目）：

| 指令 | 条目数 | 分页 |
| :--- | ---: | ---: |
| `勋章` | 352 | 10 |
| `喷漆` | 670 | 19 |
| `头像框` | 71 | 2 |
| `基板` / `个性名片` / `核心` | — | — |

```
勋章       → 第 1 页
勋章 2     → 第 2 页
```

**文字页**（参数为章节序号）：

| 指令 | 说明 |
| :--- | :--- |
| `地图` `角色` `武器` | 图鉴与地图 |
| `角色定位` `战斗模式` `武器数据` | 规则与数值 |
| `信用分` `活动` `赛季` | 玩法与福利 |
| `表情包` | 官方表情包合集 |

```
地图       → 列出章节
地图 2     → 查看第 2 章
```

> `四格漫画`、`联动`、`相关周边` 没有独立指令，请从 `菜单 → 游戏延伸` 进入。

---

## 指令与帮助图的一致性

`config.yml` 中有 26 条指令配置，帮助图（`帮助`）逐条列出这 26 条，一一对应。

**别名会完整显示**。16 条指令配了别名，帮助图上以灰色小字跟在主名后：

```
兑换码  cdk / 礼包码
武器数据  ttk / dps
个性名片  名片
```

**别名从配置动态读取**，不是写死在图片里。改完 `config.yml` 重启后，
帮助图会显示新的别名——可以加 `gift`、`兑换` 这样的自定义别名，图上立刻能看到。

---

## 两个入口

| 指令 | 作用 | 场景 |
| :--- | :--- | :--- |
| `帮助` | 静态的指令一览 | 想知道有哪些指令 |
| `菜单` | 可导航的功能菜单 | 不知道内容在哪，需要翻找 |

```
帮助  → 一张图列出所有指令（看完即止）
菜单  → 分类 → 页面 → 章节 → 内容
```

菜单支持四级钻取，每层都带面包屑（`功能菜单 › 装饰图鉴 › 勋章`）：

| 输入 | 行为 |
| :--- | :--- |
| 序号 | 进入下一层 |
| `返回` / `0` | 回到上一层 |
| `取消` / `退出` | 关闭菜单 |

---

## 交互细节

**空格可省略** —— `看看米雪儿` 与 `看看 米雪儿` 等价。框架按空白切分命令名，插件在事件阶段补回分隔空格。

**昵称可含空格** —— `看看 浅海梦是坏女人喵` 会把指令后的内容整体当作昵称。

**对局短号** —— 对局号支持只写尾数，自动匹配最近的对局（`看看对局米雪儿774287`）。

**同名玩家选择** —— 匹配到多个同名玩家（如两个「心心子」）时列出候选，回复序号选择：

```
找到 2 个与「心心子」匹配的引航者，请回复序号：
1. 心心子  ID 6661532  Lv.320
2. 心心子  ID 6661504
回复 1 - 2 选择，回复「取消」放弃（2 分钟内有效）。
```

判定规则：只有 **多个精确匹配** 时才要求选择；若只有一个精确匹配（如「心夏」有 8 个候选但仅 1 个精确），直接出图。

---

## 数字交互规则

所有带序号的图都支持**直接回复数字**，不必重复指令前缀：

| 你看到的图 | 回复 | 效果 |
| :--- | :--- | :--- |
| 功能菜单 | `2` | 进入第 2 个分类 |
| BGM 曲目列表 | `165` | 点播第 165 首 |
| 壁纸网格 | `3` | 取第 3 张原图 |
| 图鉴分页 | `2` | 翻到第 2 页 |
| 同名玩家候选 | `1` | 选择第 1 个玩家 |

带命令的写法同样有效：`BGM 5`、`壁纸 3`、`勋章 2`。

### 数字归属的优先级

同一时刻可能有多个交互在等待数字，按此顺序判定：

```
1. 同名玩家选择   用户刚查了玩家，正等着挑人
2. 菜单会话       用户正在逐层浏览
3. 卡片上下文     用户刚看了 BGM / 壁纸 / 图鉴
```

越靠前的优先级越高。例如菜单打开时，数字属于菜单，不会被之前的壁纸上下文抢走。

### 上下文生命周期

- 发新指令时自动清除（注意力已转移，旧的列表不再响应数字）
- 3 分钟无操作自动失效
- 菜单里回复「取消」时一并清除

### 翻页

壁纸每页 15 张，共 4 页：

```
壁纸 第2页      → 跳到第 2 页
壁纸 下一页     → 下一页
壁纸 上一页     → 上一页
```

---

## 配置

配置文件位于 `plugins/Celestia/config.yml`，共 33 项。

### 指令名

所有指令名均可修改，例如把 `看看` 改成 `查`：

```yaml
command:
  help: ["帮助", "卡丘帮助"]
  menu: ["菜单", "功能"]
  profile: ["看看"]
  match: ["看看对局"]
  rank: ["排行", "排行榜"]
  wiki: ["wiki", "维基"]
  redeem: ["兑换码", "cdk", "礼包码"]
  bgm: ["BGM", "bgm", "音乐"]
  medal: ["勋章"]
  spray: ["喷漆"]
  avatarFrame: ["头像框"]
  # …其余页面指令同理
```

### 出图品牌

```yaml
image:
  brand: "卡丘战绩档案"     # 图片左上角标题
  watermark: "Celestia"    # 右下角署名，留空不显示
```

标题自动拼接副标题（`{brand} - 玩家概览`）。品牌名过长时自动截断，避免与右上角时间戳重叠。

### 其他

| 配置项 | 默认 | 说明 |
| :--- | :--- | :--- |
| `rank.page-size` | 20 | 排行榜每页人数（上限 100） |
| `rank.max-page` | 100 | 排行榜最大页数 |
| `cache.ttl-seconds` | 60 | 查询结果缓存（秒），`<=0` 回退 60 |
| `bgm.send-mode` | record | `record`（语音）/ `link`（直链） |
| `font.install-missing` | false | 缺中文字体时自动安装（仅 Linux，需 root） |

---

## 出图

所有图片共用一套视觉规范（[Theme.java](src/main/java/top/szzz666/KokonaCelestia/render/Theme.java)）：

* **底色**：粉紫 → 淡蓝斜向渐变
* **卡片**：半透明白 + 高光描边 + 柔和投影
* **分组条**：`#1A1B20` 深色圆角条 + 橙色色标
* **版式**：1000px 定宽，高度按内容自适应

| 卡片 | 用途 | 序号 |
| :--- | :--- | :--- |
| [PlayerCard](src/main/java/top/szzz666/KokonaCelestia/render/PlayerCard.java) | 玩家概览 | — |
| [MatchCard](src/main/java/top/szzz666/KokonaCelestia/render/MatchCard.java) | 对局详情 | — |
| [RankCard](src/main/java/top/szzz666/KokonaCelestia/render/RankCard.java) | 排行榜 | — |
| [MenuCard](src/main/java/top/szzz666/KokonaCelestia/render/MenuCard.java) | 多级菜单 | ✅ 可回复 |
| [GalleryCard](src/main/java/top/szzz666/KokonaCelestia/render/GalleryCard.java) | 图鉴网格 | ❌ |
| [BgmCard](src/main/java/top/szzz666/KokonaCelestia/render/BgmCard.java) | BGM 曲目 | ✅ 可点播 |
| [RedeemCard](src/main/java/top/szzz666/KokonaCelestia/render/RedeemCard.java) | 兑换码 | ❌ |
| [WikiCard](src/main/java/top/szzz666/KokonaCelestia/render/WikiCard.java) | WIKI 内容 | ❌ |
| [HelpCard](src/main/java/top/szzz666/KokonaCelestia/render/HelpCard.java) | 指令一览 | — |

**序号只在真的能回复点选时才显示。** 图鉴条目动辄数百个，回复数字只会切换分页，标序号只会误导。

### 预览

`docs/preview/` 下有实际出图样例：

| 功能 | 预览 |
| :--- | :--- |
| 玩家概览 | [player.png](docs/preview/player.png) |
| 对局详情 | [match.png](docs/preview/match.png) |
| 排行榜 | [rank.png](docs/preview/rank.png) |
| 指令一览 | [help.png](docs/preview/help.png) |
| 功能菜单 | [menu-root.png](docs/preview/menu-root.png) |
| 菜单章节 | [menu-medals.png](docs/preview/menu-medals.png) |
| 章节分页 | [menu-paged.png](docs/preview/menu-paged.png) |
| 图鉴网格 | [gallery-medals.png](docs/preview/gallery-medals.png) / [gallery-frames.png](docs/preview/gallery-frames.png) |
| 兑换码 | [redeem.png](docs/preview/redeem.png) |
| BGM 列表 | [bgm-list.png](docs/preview/bgm-list.png) |
| WIKI 内容 | [wiki-信用分.png](docs/preview/wiki-信用分.png) / [wiki-地图.png](docs/preview/wiki-地图.png) |

---

## 实现说明

### 模块结构

```
src/main/java/top/szzz666/KokonaCelestia/
├── KokonaCelestiaMain.java      入口：注册 25 条指令
├── api/
│   ├── DakanuClient.java        战绩接口 + 60 秒缓存
│   ├── WikiClient.java          WIKI 接口、章节切分、文本提取
│   ├── WikiGallery.java         图鉴条目解析（名称 + 图片）
│   └── WikiBgm.java             BGM 曲目与音频直链
├── command/
│   ├── Commands.java            基础指令实现
│   ├── WikiCommands.java        WIKI / 兑换码 / BGM
│   ├── WikiDirect.java          页面直达指令
│   ├── MenuCommands.java        菜单导航
│   ├── MenuSession.java         菜单会话（导航栈）
│   ├── MenuTree.java            菜单结构定义
│   └── PendingSelection.java    同名玩家候选
├── config/MyConfig.java         配置（32 项）
├── listener/QQListeners.java    事件拦截：空格补全、菜单、选择
├── model/PlayerProfile.java     战绩数据模型
├── render/                      出图（Theme / Card / Assets / Fonts）
└── util/
    ├── Http.java                裸 TLS 客户端（IP 直连 + 空 SNI + Host 头）
    └── Reply.java               消息发送封装
```

### 几个关键技术点

**1. TLS 阻断绕过**

Dakanu 存在 TLS 握手阻断，且网关校验 Host 头（缺失返回 418）。`HttpsURLConnection` 会忽略手工设置的 Host 头，因此 [Http.java](src/main/java/top/szzz666/KokonaCelestia/util/Http.java) 基于 `SSLSocket` 手写 HTTP/1.1：连接目标为 IP、SNI 置空、Host 头显式写入域名、证书校验关闭，并按 `Content-Length` 精确读取以支持连接复用。

**2. WebP 解码内置**

头像、立绘、地图、段位徽章均为 WebP，JDK 自带 ImageIO 不支持。插件把 TwelveMonkeys 的 5 个 jar 打包进 `resources/webp/`，运行时解压并用独立 `URLClassLoader` 注册进 `IIORegistry`，**使用者无需安装依赖**。

**3. 中文字体内置**

多数 Linux 服务器没有中文字体，逻辑字体会把汉字画成方块。插件自带思源黑体（完整 CJK 基本区，压缩后约 4.3MB），粗体由 Java2D 合成以省掉第二份字体。这同时保证了**跨平台出图完全一致**——若改用系统字体，macOS 的 PingFang 与 Linux 的 Noto 字形字宽都不同。

**4. WIKI 抓取要点**

* **必须带 `Referer`**：站点启用 EdgeOne 防护，缺少该头会返回 HTTP 567
* **取 `prop=text` 而非 wikitext**：大量页面的数据来自 Lua 模块、Widget 或 SMW 查询，wikitext 里只有模板调用
* **必须 `redirects=1`**：WIKI 有大量重定向页（「好友亲密度」→「好友」）
* **表格先扁平化**：`<td>` 之间常有换行，直接转文本会把一行表格拆散

**5. 出图性能**

| 环节 | 优化前 | 优化后 | 做法 |
| :--- | ---: | ---: | :--- |
| 布局测量 | 28 s | 0 ms | 去掉「先画一遍再量高」，改为各视图精确计算 |
| 图片获取 | 25 s | 3-4 s | 磁盘缓存 + 16 并发 + 连接复用 |

磁盘缓存位于 `plugins/Celestia/assets-cache/`，素材内容固定、路径即标识，只下载一次，进程重启后复用。

| 场景 | 耗时 |
| :--- | ---: |
| 全新部署（缓存为空） | 4.7 s |
| 进程重启后（缓存命中） | 0.4 s |
| 同一玩家再次查询 | 0.11 s |

**6. 空格补全**

框架的 `CommandManage.dispatch` 用 `content.split("\\s+")` 取第一个词作为命令名，所以 `看看米雪儿` 会匹配失败。插件在 `QQListeners` 中拦截事件（早于命令分发），命中已知前缀后插入分隔空格，后续仍由框架统一执行（权限校验不变）。

注意：框架只对**插件主类实例**扫描 `@QQEventHandler`，因此独立监听器类需在 `onEnable()` 中手动 `QQEventManage.registerListener(...)`。

---

---

## 壁纸功能

```
壁纸          → 缩略图网格（9 张/页，带序号）
壁纸 3        → 第 3 张的 1920px 原图
壁纸 随机     → 随机一张
随机壁纸      → 同上（独立指令）
```

原图直接以 URL 交给 OneBot 的 `image` 消息，由客户端拉取，不占机器人带宽与磁盘。

数据来自 WIKI 的壁纸页，共 52 张（活动壁纸 48 · 移动端过场图 4），
均为 1920×1080。页面给出的是 889px 缩略图，原图地址由去掉 `/thumb/` 与尺寸后缀还原：

```
.../thumb/2/2b/<hash>.png/889px-<名称>.png
  → .../2/2b/<hash>.png
```

---

## BGM 点播

曲目直链来自 WIKI（165 首，`patchwiki.biligame.com`），直接交给 OneBot 的 `record` 消息，由客户端拉取，不占机器人带宽。

```yaml
bgm:
  send-mode: "record"   # record（语音）或 link（直链）
```

**为什么有两种模式**：QQ 语音对时长与格式有硬限制（多数实现限制 60 秒内，部分客户端要求 silk/amr），而 WIKI 曲目普遍 2-5 分钟、约 5MB，直接发语音可能被拒绝或降级成文件。若你的 OneBot 支持长音频用 `record`，否则设 `link`。

---

## 本地调试

不启动 QQ 也能验证出图：

```bash
JAVA21=/path/to/jdk-21
LIBS=$(find lib -name '*.jar' | tr '\n' ':')
"$JAVA21/bin/javac" -d build/tools -cp "build/classes:$LIBS" tools/RenderProbe.java
"$JAVA21/bin/java" -cp "build/tools:build/classes:$LIBS" RenderProbe /tmp/out all
```

`tools/` 下另有几个探针：`CommandProbe`（指令解析）、`FontProbe`（字体）、`CacheProbe`（缓存 TTL）、`PerfProbe`（分段耗时）。

---

## 图鉴与长内容

### 图鉴网格

装饰与素材类页面自动渲染成图标网格（36 个/页）：

| 指令 | 条目数 | 分页 |
| :--- | ---: | ---: |
| `勋章` | 404 | 12 |
| `喷漆` | 670 | 19 |
| `表情包` | 300 | 9 |
| `头像框` | 71 | 2 |

两种页面结构都能解析：

- `div.gallerygrid-item` + `span.badge`（装饰图鉴）
- `div.thumb` + `div.gallerytext > big`（表情包）

### 单条目大图

图鉴条目数以百计，逐个编号会与分页冲突，因此**用名称定位**：

```
勋章 闪耀鎏金    → 直接发这张大图
勋章 鎏金        → 模糊匹配
勋章 2           → 数字当页码
```

### 超长章节分段

章节超过 200 行时自动分段，用第二个参数指定：

```
勋章 2           → 提示共几段
勋章 2 1         → 第 2 章第 1 段
```

---

## 已知限制

- **WIKI 是第三方数据源**，靠志愿者维护，可能滞后。每张图页脚都标注来源与原文链接。
- **素材缓存无自动清理**，删除 `assets-cache/` 即可重置。
- **BGM 语音受 QQ 限制**，超过 60 秒的曲目建议用 `bgm.send-mode: link`。

---

## 许可与致谢

* 数据来自 [Dakanu](https://www.dakanu.com/) 与 [biligame 卡拉彼丘 WIKI](https://wiki.biligame.com/klbq)，版权归原作者
* 内置字体为 [思源黑体 / Noto Sans SC](https://github.com/notofonts/noto-cjk)（SIL OFL 1.1）
* 本插件为第三方工具，与游戏开发商及发行商无隶属关系，数据以游戏内为准
