# tea-shop — AI 茶饮点单管家（连锁茶饮品牌）

> DSH（deepseek-harness-java）Java Native Plugin 场景案例 P79。
> 连锁茶饮门店演示应用：茶单、门店查询、点单、订单查询、运营统计，全部能力通过 **Java Native 插件**注册为 DSH Agent 工具，前端 AI 管家经 SSE 实时问答。

## 需求与场景

连锁茶饮品牌需要一个「AI 茶饮点单管家」：

- 顾客问茶品价格、规格，AI 直接查茶单回答；
- 点单前 AI 必须复述要素（茶品/单价/杯数/规格/总价/门店）请顾客确认，确认后落单并回报单号；
- 顾客随时查订单进度（待制作/制作中/可取餐/已完成）；
- 店长问运营，AI 汇报总单量、总杯数、营收与预计营收、分茶品分门店分布，并给运营建议。

## 运行地址（演示）

| 服务 | 地址 | 说明 |
|---|---|---|
| 茶饮前端 | http://127.0.0.1:18118 | 运营看板 + 茶单 + 订单 + AI 管家 |
| DSH 平台 | http://127.0.0.1:8090 | Agent 编排与插件运行时 |

## 插件信息

| 项 | 值 |
|---|---|
| pluginId | `tea-copilot` |
| 名称 | AI 茶饮点单管家 |
| runtimeType | `JAVA_NATIVE`（进程内加载） |
| 入口类 | `cn.xiaofuge.t.plugin.TeaPlugin` |
| 基类 | `AbstractHarnessPlugin` + 5 个 `AbstractTool` |

## 工具清单（5 个）

| 工具 | 说明 | 对应 REST |
|---|---|---|
| `plugin__tea-copilot__menu_list` | 茶单（5 款茶品，含第二杯半价与储值优惠） | GET /api/menu |
| `plugin__tea-copilot__shop_list` | 门店列表（名称/特点/评分/在单量） | GET /api/shops |
| `plugin__tea-copilot__order` | 点单（7 参数，先复述确认再调用） | POST /api/order |
| `plugin__tea-copilot__order_info` | 订单查询（茶品/门店/规格/金额/状态） | GET /api/order/info |
| `plugin__tea-copilot__stats` | 运营统计（单量/杯数/营收/分布/建议） | GET /api/stats |

## 预置数据

- **5 款茶品**：茉莉奶绿 ¥16 / 烤黑糖波波 ¥18 / 栀子青提 ¥22 / 桂花乌龙奶茶 ¥17 / 冷萃鸭屎香柠檬茶 ¥21
- **3 家门店**：天晖路店 M01（写字楼商圈，出杯最快，4.9）、大学城店 M02（学生聚集，4.8）、翡翠湾店 M03（社区店，4.7）
- **3 笔种子订单**：T9001 柏女士（烤黑糖波波×2·制作中）、T9002 水先生（鸭屎香柠檬茶·待制作）、T9003 柏女士（茉莉奶绿×3·已完成）

## 业务规则

- **第二杯半价**每天 14:00-16:00，4 杯起生效；会员储值 100 送 15；
- 点单必填：下单人/取餐电话/茶品/杯数；shopId 可选（默认天晖路店），spec 糖冰规格、pickupTime 取餐时间可选；
- 订单状态流：待制作 → 制作中 → 可取餐 → 已完成；营收只计已完成；
- 红线：咖啡因敏感推荐纯茶系；热饮只有中杯；价格与优惠只转述工具返回。

## 体验流程

1. 打开 http://127.0.0.1:18118 —— 运营看板（总单量/制作中/待制作/营收）、5 款茶品卡片、最新订单；
2. 右侧 AI 管家逐条试：
   - 「有哪些茶饮？烤黑糖波波多少钱？」→ `menu_list`
   - 「有哪些门店？哪家出杯快？」→ `shop_list`
   - 「帮我点 4 杯栀子青提，大学城店，18:00 取，甘女士 13800770000」→ 先复述确认 → `order` 报单号与半价
   - 「查一下订单 T9002」→ `order_info`
   - 「今天运营情况怎么样？」→ `stats`
3. DSH 控制台话术：`激活 tea-copilot 插件后，问茶单价格或直接点单。`

## 构建与启动

```bash
# 1. 构建（JDK 17）
cd tea-shop && mvn clean package

# 2. 启动应用（端口 18118）
SERVER_PORT=18118 java -jar t-app/target/t-app-1.0.0-SNAPSHOT.jar

# 3. 安装插件到 DSH（先拷 jar 再装再激活）
cp t-plugin/target/t-plugin-1.0.0-SNAPSHOT.jar ~/.dsh/standalone/plugins/tea-copilot.jar
curl -X POST http://127.0.0.1:8090/api/harness/plugins/install -H 'Content-Type: application/json' \
  -d '{"pluginId":"tea-copilot","displayName":"AI 茶饮点单管家","pluginVersion":"1.0.0","runtimeType":"JAVA_NATIVE","sourcePath":"'"$HOME"'/.dsh/standalone/plugins/tea-copilot.jar","entrypoint":"cn.xiaofuge.t.plugin.TeaPlugin"}'
curl -X POST http://127.0.0.1:8090/api/harness/plugins/activate -H 'Content-Type: application/json' -d '{"pluginId":"tea-copilot"}'

# 4. DSH standalone（如未启动）
cd ~/.dsh/standalone && java -Dspring.profiles.active=standalone -Dserver.port=8090 \
  -jar ~/.dsh/skills/dsh-java-plugin-skills/runtime/deepseek-harness-java-app.jar
```

## 工程结构

```
tea-shop/
├── pom.xml                # 聚合工程 tea-shop
├── t-app/                 # Spring Boot 应用（18118）
│   └── src/main/java/cn/xiaofuge/t/app/
│       ├── TeaApplication.java
│       ├── TStore.java        # 茶单/门店/订单内存数据中心
│       ├── TController.java   # REST 5 端点（cups 数值双格式容错）
│       └── AssistantController.java  # /api/assistant/stream SSE 透传 DSH
└── t-plugin/              # Java Native 插件
    └── src/main/
        ├── java/cn/xiaofuge/t/plugin/TeaPlugin.java  # 5 工具 + 系统提示词 + PRE_TOOL_USE hook
        └── resources/META-INF/
            ├── plugin.yaml
            └── services/cn.xiaofuge.deepseek.harness.domain.spi.JavaHarnessPlugin
```

## 坑位记录

- **插件激活端点是全局的**：`POST /api/harness/plugins/activate`（body 带 pluginId）；
- **数值参数双格式容错**：cups 用 `instanceof Number` + 字符串兜底双解析；
- **复制模板后清理**：`.git`、`docs/`、`README.md` 必须先删，避免 git 污染与内容残留。

## 端到端验证记录（2026-09-25）

| # | 问题 | 触发工具 | 结果 |
|---|---|---|---|
| T1 | 有哪些茶饮？烤黑糖波波多少钱？ | menu_list | ✅ 5 款茶品 + 18 元 |
| T2 | 有哪些门店？哪家出杯快？ | shop_list | ✅ 3 家门店特点/评分 |
| T3 | 点单：栀子青提 4 杯 大学城店 | order | ✅ T9004，¥66（4 杯第二杯半价 22×3+11），cups 容错生效 |
| T4 | 查订单 T9002 | order_info | ✅ 水先生 鸭屎香柠檬茶 大学城店 |
| T5 | 运营情况 | stats | ✅ 总单量 4 / 总杯数 10 / 预计营收 ¥123 |
