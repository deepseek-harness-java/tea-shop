package cn.xiaofuge.t.app;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 茶饮门店 REST 接口。
 * 提供：茶单 / 门店列表 / 点单 / 订单查询 / 运营统计。
 */
@RestController
@RequestMapping("/api")
public class TController {

    private final TStore store;

    public TController(TStore store) {
        this.store = store;
    }

    /** 茶单 */
    @GetMapping("/menu")
    public Map<String, Object> menu() {
        return store.menuList();
    }

    /** 门店列表 */
    @GetMapping("/shops")
    public Map<String, Object> shops() {
        return store.shopList();
    }

    /** 点单 */
    @PostMapping("/order")
    public Map<String, Object> order(@RequestBody Map<String, Object> body) {
        Integer cups = null;
        Object d = body.get("cups");
        if (d instanceof Number n) cups = n.intValue();
        else if (d != null) {
            try { cups = Integer.valueOf(String.valueOf(d).trim()); } catch (NumberFormatException ignored) { }
        }
        return store.order(str(body, "customer"), str(body, "phone"), str(body, "drink"),
                str(body, "shopId"), str(body, "spec"), str(body, "pickupTime"), cups);
    }

    private String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    /** 订单查询 */
    @GetMapping("/order/info")
    public Map<String, Object> orderInfo(@RequestParam(required = false) String orderId) {
        return store.orderInfo(orderId == null ? "" : orderId);
    }

    /** 运营统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.stats();
    }
}
