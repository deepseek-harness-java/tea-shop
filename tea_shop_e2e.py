#!/usr/bin/env python3
"""tea-shop E2E：通过业务应用 SSE 代理调用 DSH Agent，验证工具全链路。"""
import json, subprocess, sys

AGENT = "tea-copilot"
URL = "http://127.0.0.1:18118/api/assistant/stream"

CASES = [
    ("T1 饮品菜单", "奶茶店有哪些饮品？多少钱？简洁回答", ["茉莉奶绿", "烤黑糖波波"]),
    ("T2 点单", "我是顾客江小姐，电话13600007777，帮我点一杯烤黑糖波波，三分糖去冰，直接下单告诉我订单信息", ["烤黑糖波波", "下单成功"]),
    ("T3 订单查询", "奶茶店现在有哪些订单？简洁回答", ["订单"]),
    ("T4 储值活动", "奶茶店的会员储值活动是怎样的？储值 100 送多少？简洁回答", ["储值", "15"]),
    ("T5 销售统计", "奶茶店今天的销售情况怎么样？简洁回答", ["营收", "烤黑糖波波"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== tea-shop E2E: {passed}/{len(cases)} PASS =====")

if __name__ == "__main__":
    main()
