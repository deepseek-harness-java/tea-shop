package cn.xiaofuge.t.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 茶饮点单管家插件：把 tea-shop REST API 注册为 DSH Agent 工具 */
public class TeaPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "tea-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public TeaPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new MenuListTool(),
                new ShopListTool(),
                new OrderTool(),
                new OrderInfoTool(),
                new StatsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("tea-capabilities", 20, """
                ## AI 茶饮点单管家（连锁茶饮品牌 · 2026-09-25）
                - 查茶单 → menu_list（5 款茶品单价与规格：茉莉奶绿16/烤黑糖波波18/栀子青提22/桂花乌龙奶茶17/冷萃鸭屎香柠檬茶21；
                  第二杯半价每天 14-16 点，4 杯起生效；储值 100 送 15）
                - 查门店 → shop_list（3 家门店特点/评分/在单量）
                - 点单 → order（customer/phone/drink/cups 必填，shopId 可选默认天晖路店，spec 糖冰规格，pickupTime 取餐时间；
                  必须先复述茶品、杯数、规格、总价、门店请用户确认后才能调用；成功报单号）
                - 订单查询 → order_info（orderId：T9001 格式；茶品/门店/规格/金额/状态）
                - 问运营 → stats（总单量/制作中/待制作/总杯数/营收与预计营收/分茶品分门店分布/运营建议）
                - 回答要求：
                  1) 点单前必须复述要素（茶品/单价/杯数/规格/总价/门店）请用户确认
                  2) 点单结果必报单号与取餐时间
                  3) 提醒：咖啡因敏感可选纯茶系；糖度冰量默认大杯正常糖正常冰可自选；热饮杯型只有中杯
                  4) 价格与优惠只转述工具返回，禁止编造折扣
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: tea tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("TEA_APP_BASE_URL", "http://127.0.0.1:18118")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class MenuListTool extends AbstractTool {
        @Override public String name() { return "menu_list"; }
        @Override public String description() {
            return "茶单：5 款茶品的单价与规格（奶茶/果茶/柠檬茶），含第二杯半价与储值优惠。"
                    + "报价、点单前必查。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/menu", args));
        }
    }

    private class ShopListTool extends AbstractTool {
        @Override public String name() { return "shop_list"; }
        @Override public String description() {
            return "门店列表：名称/特点/评分/在单量。用户挑门店、问哪家快时调用。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/shops", args));
        }
    }

    private class OrderTool extends AbstractTool {
        @Override public String name() { return "order"; }
        @Override public String description() {
            return "茶饮点单：customer（下单人）/phone（取餐电话）/drink（茶品）/cups（杯数）必填，"
                    + "shopId（门店 M01-M03）可选默认 M01，spec（糖度冰量杯型）可选，pickupTime（取餐时间）可选。"
                    + "必须先复述茶品、杯数、规格、总价、门店经用户确认后才能调用。成功返回单号。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("customer", stringSchema("下单人姓名"))
                    .prop("phone", stringSchema("取餐电话"))
                    .prop("drink", stringSchema("茶品：茉莉奶绿 / 烤黑糖波波 / 栀子青提 / 桂花乌龙奶茶 / 冷萃鸭屎香柠檬茶"))
                    .prop("cups", stringSchema("杯数，正整数"))
                    .prop("shopId", stringSchema("门店编号 M01-M03，可选，默认 M01 天晖路店"))
                    .prop("spec", stringSchema("糖度冰量杯型，如：大杯/五分糖/去冰，可选"))
                    .prop("pickupTime", stringSchema("取餐时间，如 17:30，可选默认尽快"))
                    .required("customer", "phone", "drink", "cups")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"customer\":\"" + json(str(args, "customer"))
                    + "\",\"phone\":\"" + json(str(args, "phone"))
                    + "\",\"drink\":\"" + json(str(args, "drink"))
                    + "\",\"cups\":\"" + json(str(args, "cups"))
                    + "\",\"shopId\":\"" + json(str(args, "shopId"))
                    + "\",\"spec\":\"" + json(str(args, "spec"))
                    + "\",\"pickupTime\":\"" + json(str(args, "pickupTime")) + "\"}";
            return ok(post("/api/order", body, args));
        }
    }

    private class OrderInfoTool extends AbstractTool {
        @Override public String name() { return "order_info"; }
        @Override public String description() {
            return "订单查询：orderId 必填（T9001 格式）。返回茶品/门店/规格/金额/状态（待制作、制作中、可取餐、已完成）。"
                    + "何时必须调用：用户问订单、问做好没有。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("orderId", stringSchema("订单号，如 T9001"))
                    .required("orderId")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/order/info?orderId=" + java.net.URLEncoder.encode(str(args, "orderId"), StandardCharsets.UTF_8), args));
        }
    }

    private class StatsTool extends AbstractTool {
        @Override public String name() { return "stats"; }
        @Override public String description() {
            return "运营统计：总单量/制作中/待制作/总杯数/营收与预计营收/分茶品分门店分布/运营建议。"
                    + "何时必须调用：问今天运营、问单量与营收。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stats", args));
        }
    }
}
