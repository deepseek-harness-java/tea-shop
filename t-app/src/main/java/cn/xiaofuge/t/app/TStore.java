package cn.xiaofuge.t.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** 茶饮门店数据中心：茶单/门店/订单/统计 */
@Component
public class TStore {

    /** 茶品：单价(元)/规格/说明 */
    static final Map<String, Object[]> DRINKS = new LinkedHashMap<>();
    static {
        DRINKS.put("茉莉奶绿", new Object[]{16.0, "500ml", "茉莉绿茶+鲜奶，低甜更清爽"});
        DRINKS.put("烤黑糖波波", new Object[]{18.0, "500ml", "现熬黑糖珍珠，去冰少糖是招牌喝法"});
        DRINKS.put("栀子青提", new Object[]{22.0, "700ml", "手剥青提+栀子花茶底，果香浓"});
        DRINKS.put("桂花乌龙奶茶", new Object[]{17.0, "500ml", "秋桂花香+乌龙茶底"});
        DRINKS.put("冷萃鸭屎香柠檬茶", new Object[]{21.0, "700ml", "冷萃单丛+香水柠檬，回甘强"});
    }

    /** 门店：编号/名称/特点/评分 */
    static final Map<String, Object[]> SHOPS = new LinkedHashMap<>();
    static {
        SHOPS.put("M01", new Object[]{"天晖路店", "写字楼商圈，出杯最快", 4.9});
        SHOPS.put("M02", new Object[]{"大学城店", "学生聚集，活动多", 4.8});
        SHOPS.put("M03", new Object[]{"翡翠湾店", "社区店，代收外卖方便", 4.7});
    }

    public static class Order {
        public String id; public String customer; public String phone;
        public String drink; public String shop; public String spec;
        public String pickupTime; public int cups;
        public double total; public String status; // 待制作 / 制作中 / 可取餐 / 已完成
    }

    public final List<Order> orders = new ArrayList<>();
    private int orderSeq = 9001;

    public TStore() { seed(); }

    private void seed() {
        orders.add(o("柏女士", "13800888888", "烤黑糖波波", "M01", "大杯/五分糖/去冰", "17:30", 2, "制作中"));
        orders.add(o("水先生", "13800999999", "冷萃鸭屎香柠檬茶", "M02", "大杯/正常糖/少冰", "18:00", 1, "待制作"));
        orders.add(o("柏女士", "13800888888", "茉莉奶绿", "M03", "中杯/三分糖/热", "16:00", 3, "已完成"));
    }

    private Order o(String customer, String phone, String drink, String shopId, String spec, String pickupTime, int cups, String status) {
        Order x = new Order(); x.id = "T" + orderSeq++; x.customer = customer; x.phone = phone;
        x.drink = drink; x.shop = String.valueOf(SHOPS.get(shopId)[0]);
        x.spec = spec; x.pickupTime = pickupTime; x.cups = cups;
        Object[] p = DRINKS.get(drink);
        x.total = p != null ? (Double) p[0] * cups : 0;
        x.status = status; return x;
    }

    /** 茶单 */
    public Map<String, Object> menuList() {
        List<Map<String, Object>> list = new ArrayList<>();
        DRINKS.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("drink", k); m.put("price", v[0]); m.put("spec", v[1]); m.put("desc", v[2]); list.add(m); });
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", list.size()); r.put("drinks", list);
        r.put("note", "第二杯半价每天 14:00-16:00；会员储值 100 送 15");
        return r;
    }

    /** 门店列表 */
    public Map<String, Object> shopList() {
        List<Map<String, Object>> list = SHOPS.entrySet().stream()
                .map(e -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", e.getKey()); m.put("name", e.getValue()[0]);
                    m.put("feature", e.getValue()[1]); m.put("rating", e.getValue()[2]);
                    m.put("activeOrders", orders.stream().filter(o -> o.shop.equals(e.getValue()[0])
                            && !"已完成".equals(o.status)).count());
                    return m; })
                .collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("shops", list);
        return r;
    }

    /** 点单 */
    public synchronized Map<String, Object> order(String customer, String phone, String drink, String shopId, String spec, String pickupTime, Integer cups) {
        if (customer == null || customer.isBlank())
            return Map.of("ok", false, "msg", "请提供下单人姓名");
        Object[] p = DRINKS.get(drink);
        if (p == null) return Map.of("ok", false, "msg", "茶品 " + drink + " 不在茶单，可选：" + String.join("/", DRINKS.keySet()));
        if (phone == null || phone.isBlank())
            return Map.of("ok", false, "msg", "请提供取餐电话");
        if (cups == null || cups < 1)
            return Map.of("ok", false, "msg", "请提供杯数（至少 1 杯）");
        String sName;
        if (shopId == null || shopId.isBlank()) {
            sName = String.valueOf(SHOPS.get("M01")[0]); // 默认天晖路店
        } else {
            var sEntry = SHOPS.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(shopId)).findFirst().orElse(null);
            if (sEntry == null) return Map.of("ok", false, "msg", "门店 " + shopId + " 不存在，可选：" + String.join("/", SHOPS.keySet()));
            sName = String.valueOf(sEntry.getValue()[0]);
        }
        double price = (Double) p[0];
        double total = price * cups;
        if (cups >= 4) total = price * (cups - cups / 2) + price * (cups / 2) * 0.5; // 4 杯起第二杯半价粗略按偶数生效
        Order x = new Order(); x.id = "T" + orderSeq++; x.customer = customer; x.phone = phone;
        x.drink = drink; x.shop = sName; x.spec = spec == null || spec.isBlank() ? "大杯/正常糖/正常冰" : spec;
        x.pickupTime = pickupTime == null || pickupTime.isBlank() ? "尽快" : pickupTime; x.cups = cups;
        x.total = total; x.status = "待制作";
        orders.add(0, x);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", customer);
        r.put("drink", drink); r.put("shop", sName); r.put("spec", x.spec);
        r.put("pickupTime", x.pickupTime); r.put("cups", cups); r.put("total", total);
        if (cups >= 4) r.put("discount", "已享第二杯半价（4 杯起）");
        r.put("msg", "点单成功！单号 " + x.id + "，" + drink + " × " + cups + " 杯（合计 ¥" + total + "），门店 " + sName + "，取餐时间 " + x.pickupTime);
        return r;
    }

    /** 订单查询 */
    public Map<String, Object> orderInfo(String orderId) {
        Order x = orders.stream().filter(o -> o.id.equalsIgnoreCase(orderId)).findFirst().orElse(null);
        if (x == null) return Map.of("ok", false, "msg", "订单 " + orderId + " 不存在，当前共 " + orders.size() + " 单");
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", x.customer);
        r.put("drink", x.drink); r.put("shop", x.shop); r.put("spec", x.spec);
        r.put("pickupTime", x.pickupTime); r.put("cups", x.cups); r.put("total", x.total); r.put("status", x.status);
        if ("可取餐".equals(x.status)) r.put("msg", "茶已备好，报取餐电话即可取");
        return r;
    }

    /** 运营统计 */
    public Map<String, Object> stats() {
        Map<String, Object> byDrink = new LinkedHashMap<String, Object>();
        for (String s : DRINKS.keySet()) {
            long n = orders.stream().filter(o -> s.equals(o.drink)).count();
            if (n > 0) byDrink.put(s, n + " 单");
        }
        Map<String, Object> byShop = new LinkedHashMap<String, Object>();
        for (var e : SHOPS.entrySet()) {
            long n = orders.stream().filter(o -> o.shop.equals(e.getValue()[0])).count();
            byShop.put(String.valueOf(e.getValue()[0]), n + " 单");
        }
        int cups = orders.stream().mapToInt(o -> o.cups).sum();
        double revenue = orders.stream().filter(o -> "已完成".equals(o.status)).mapToDouble(o -> o.total).sum();
        double expected = orders.stream().filter(o -> !"已完成".equals(o.status)).mapToDouble(o -> o.total).sum();
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("totalOrders", orders.size());
        r.put("making", orders.stream().filter(o -> "制作中".equals(o.status)).count());
        r.put("pending", orders.stream().filter(o -> "待制作".equals(o.status)).count());
        r.put("done", orders.stream().filter(o -> "已完成".equals(o.status)).count());
        r.put("totalCups", cups);
        r.put("revenue", revenue);
        r.put("expectedRevenue", expected);
        r.put("byDrink", byDrink);
        r.put("byShop", byShop);
        r.put("advice", "下午茶时段（14-16 点）是高峰可提前备料；第二杯半价拉新效果好；储值送礼提升复购；柠檬茶夏季翻倍备货");
        return r;
    }
}
