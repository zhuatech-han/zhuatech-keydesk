#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""仅独立localhost学习实例创建TEST夹具和私有状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import concurrent.futures
import http.cookiejar
import json
import os
import secrets
import re
import threading
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path


class Client:
    """会话与实际CSRF；不打印密码、Cookie或返回载荷。知华科技 https://www.zhuatech.cn/。"""

    count = 0
    count_lock = threading.Lock()

    def __init__(self, base):
        self.base = base.rstrip("/")
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )
        self.csrf = None

    def call(self, path, method="GET", body=None, status=200, code=None, raw=False):
        headers = {}
        if method != "GET":
            if self.csrf is None:
                self.csrf = self.call("/auth/csrf")
            headers[self.csrf["header"]] = self.csrf["token"]
        if body is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(
            self.base + "/api" + path,
            method=method,
            headers=headers,
            data=json.dumps(body).encode() if body is not None else None,
        )
        with Client.count_lock:
            Client.count += 1
        try:
            with self.opener.open(req, timeout=40) as r:
                actual, data = r.status, r.read()
        except urllib.error.HTTPError as e:
            actual, data = e.code, e.read()
        if status is not None and actual != status:
            try:
                safe_code = json.loads(data).get("code", "HTTP_ERROR")
            except (ValueError, AttributeError):
                safe_code = "HTTP_ERROR"
            raise AssertionError(
                f"{method} {path}: expected {status}, got {actual}: {safe_code}"
            )
        parsed = data.decode() if raw else json.loads(data) if data else None
        if code is not None:
            assert parsed["code"] == code, f"{path}: unexpected business code"
        return (actual, parsed) if status is None else parsed

    def login(self, name, password):
        me = self.call("/auth/login", "POST", {"username": name, "password": password})
        self.csrf = None
        return me


def stamp(hours):
    return (
        (datetime.now(timezone.utc) + timedelta(hours=hours))
        .isoformat()
        .replace("+00:00", "Z")
    )


def snapshot(admin):
    """保存全部业务、目录和非登录审计，用于重启与恢复比对。知华科技 https://www.zhuatech.cn/。"""
    paths = [
        "/keys",
        "/grants",
        "/loans",
        "/reviews",
        "/admin/users",
        "/admin/roles",
        "/admin/departments",
        "/admin/permissions",
        "/admin/menus",
        "/admin/dictionaries",
        "/admin/settings",
    ]
    result = {p: admin.call(p) for p in paths}
    result["details"] = {
        str(l["id"]): admin.call("/loans/" + str(l["id"])) for l in result["/loans"]
    }
    result["audit"] = [r for r in admin.call("/audit") if r["action"] != "LOGIN"]
    result["csv"] = admin.call("/reports.csv", raw=True)
    return result


def verify(a):
    """以新会话验证状态、权限及全部持久化快照。知华科技 https://www.zhuatech.cn/。"""
    state = json.loads(Path(a.state_input).read_text())
    admin = Client(a.base)
    admin.login(state["adminUsername"], state["adminPassword"])
    assert (
        snapshot(admin) == state["snapshot"]
    ), "Persistent business/directory/event/CSV snapshot changed"
    for username, meta in state["users"].items():
        c = Client(a.base)
        if meta.get("disabled"):
            c.call(
                "/auth/login",
                "POST",
                {"username": username, "password": meta["password"]},
                401,
                "LOGIN_FAILED",
            )
        else:
            c.login(username, meta["password"])
            if meta["roleId"] == 4:
                assert all(l["borrowerId"] == meta["id"] for l in c.call("/loans"))
                c.call("/admin/users", status=403, code="FORBIDDEN")
    print(
        json.dumps(
            {
                "mode": "verify",
                "httpChecks": Client.count,
                "snapshotEqual": True,
                "rolesRechecked": True,
            }
        )
    )


def create(a):
    """真实HTTP/JPA测试，不通过页面状态推断操作成功。知华科技 https://www.zhuatech.cn/。"""
    target = Path(a.state_output).resolve()
    if target.exists():
        raise SystemExit("Refuse to overwrite private state")
    env = dict(
        l.split("=", 1)
        for l in Path(a.env_file).read_text().splitlines()
        if "=" in l and not l.startswith("#")
    )
    admin = Client(a.base)
    admin.login(env.get("ADMIN_USERNAME", "admin"), env["ADMIN_PASSWORD"])
    suffix = secrets.token_hex(4)
    pw = "Aa9" + secrets.token_hex(20)
    users = {}
    clients = {}

    def user(label, role, dept=1):
        name = f"test-{label}-{suffix}"
        row = admin.call(
            "/admin/users",
            "POST",
            {
                "username": name,
                "displayName": f"TEST {label}",
                "password": pw,
                "roleId": role,
                "departmentId": dept,
                "enabled": True,
            },
        )
        users[name] = {
            "id": row["id"],
            "roleId": role,
            "departmentId": dept,
            "password": pw,
        }
        c = Client(a.base)
        c.login(name, pw)
        clients[label] = c
        return row["id"]

    bo = user("borrower", 4)
    bo2 = user("borrower2", 4)
    ap = user("approver", 2)
    cu = user("custodian", 3)
    user("auditor", 5)
    dept = admin.call(
        "/admin/departments",
        "POST",
        {"name": "TEST isolated department", "zone": "UTC", "enabled": True},
    )
    user("outsider", 3, dept["id"])
    approver = clients["approver"]
    custodian = clients["custodian"]
    borrower = clients["borrower"]
    borrower2 = clients["borrower2"]

    def key(label):
        return custodian.call(
            "/keys",
            "POST",
            {
                "code": f"TEST_{suffix}_{re.sub(r'[^A-Za-z0-9_-]', '_', label)}".upper(),
                "name": "TEST " + label,
                "departmentId": 1,
                "category": "FACILITY",
                "cabinet": "TEST cabinet / 01",
                "note": "Synthetic verification fixture",
            },
        )

    def grant(k, b):
        return approver.call(
            "/grants",
            "POST",
            {
                "keyId": k["id"],
                "borrowerId": b,
                "validFrom": stamp(-1),
                "validUntil": stamp(48),
                "reason": "TEST facility task",
            },
        )

    def request(c, g, purpose="TEST facility inspection"):
        return c.call(
            "/loans",
            "POST",
            {"grantId": g["id"], "dueAt": stamp(12), "purpose": purpose},
        )

    def act(c, l, action, inspection="GOOD"):
        return c.call(
            f'/loans/{l["id"]}/{action}',
            "POST",
            {
                "version": l["version"],
                "note": "TEST physical tag checked / external evidence 001",
                "inspection": inspection,
            },
        )

    # Scoped read and permission checks before operational flows.
    clients["outsider"].call("/keys", status=200)
    borrower.call("/admin/users", status=403, code="FORBIDDEN")
    clients["auditor"].call("/keys", "POST", {}, 403, "FORBIDDEN")
    k = key("Complete return")
    g = grant(k, bo)
    l = request(borrower, g, " =TEST CSV formula prefix")
    clients["outsider"].call("/loans/" + str(l["id"]), status=403, code="OUT_OF_SCOPE")
    borrower2.call("/loans/" + str(l["id"]), status=403, code="OUT_OF_SCOPE")
    borrower.call(
        "/loans",
        "POST",
        {"grantId": g["id"], "dueAt": stamp(10), "purpose": "TEST duplicate"},
        409,
        "OPEN_REQUEST_EXISTS",
    )
    l = act(approver, l, "approve")
    l = act(custodian, l, "issue")
    custodian.call(
        f'/loans/{l["id"]}/issue',
        "POST",
        {"version": l["version"] - 1, "note": "TEST replay"},
        409,
        "VERSION_CONFLICT",
    )
    l = act(borrower, l, "accept")
    approver.call(
        "/grants/" + str(g["id"]) + "/revoke", "POST", {"version": g["version"]}
    )
    l = act(borrower, l, "return")
    l = act(custodian, l, "receive")
    assert l["state"] == "RETURNED"
    assert len(borrower.call("/loans/" + str(l["id"]))["events"]) == 6
    assert "'=TEST CSV" in admin.call("/reports.csv", raw=True)
    # Damaged return and independent quarantine release.
    k = key("Damaged & repaired")
    g = grant(k, bo)
    l = request(borrower, g)
    l = act(approver, l, "approve")
    l = act(custodian, l, "issue")
    l = act(borrower, l, "return")
    l = act(custodian, l, "receive", "DAMAGED")
    r = custodian.call(
        "/reviews",
        "POST",
        {"keyId": k["id"], "reason": "TEST repaired and tag verified"},
    )
    current_key = next(x for x in custodian.call("/keys") if x["id"] == k["id"])
    custodian.call(
        "/keys/" + str(k["id"]) + "/retire",
        "POST",
        {"version": current_key["version"]},
        409,
        "KEY_OCCUPIED",
    )
    custodian.call(
        "/reviews/" + str(r["id"]) + "/decision",
        "POST",
        {"version": 1, "approved": True, "decision": "TEST"},
        403,
        "FORBIDDEN",
    )
    approver.call(
        "/reviews/" + str(r["id"]) + "/decision",
        "POST",
        {
            "version": 1,
            "approved": True,
            "decision": "TEST independent physical inspection",
        },
    )
    # Loss is isolated, then independently resolved to retirement.
    k = key("Loss resolution")
    g = grant(k, bo)
    l = request(borrower, g)
    l = act(approver, l, "approve")
    l = act(custodian, l, "issue")
    l = act(borrower, l, "loss")
    l = act(approver, l, "resolve")
    assert l["state"] == "RESOLVED"
    # Disabled borrower: staff can physically recover without impersonation.
    disabled = user("disabled-borrower", 4)
    dc = clients["disabled-borrower"]
    k = key("Recovered disabled account")
    g = grant(k, disabled)
    l = request(dc, g)
    l = act(approver, l, "approve")
    l = act(custodian, l, "issue")
    row = next(x for x in admin.call("/admin/users") if x["id"] == disabled)
    row["enabled"] = False
    row["password"] = ""
    admin.call("/admin/users/" + str(disabled), "PUT", row)
    users[row["username"]]["disabled"] = True
    dc.call("/loans", status=401, code="UNAUTHENTICATED")
    l = act(custodian, l, "recover")
    l = act(approver, l, "confirm_recovery")
    assert l["state"] == "RETURNED"
    # Two separate sessions concurrently issue two approved requests for one key.
    k = key("Concurrent single occupancy")
    g1 = grant(k, bo)
    g2 = grant(k, bo2)
    l1 = act(approver, request(borrower, g1), "approve")
    l2 = act(approver, request(borrower2, g2), "approve")

    def race(l):
        c = Client(a.base)
        c.login(next(u for u, m in users.items() if m["id"] == cu), pw)
        return c.call(
            f'/loans/{l["id"]}/issue',
            "POST",
            {"version": l["version"], "note": "TEST concurrent physical issue"},
            status=None,
        )

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(race, [l1, l2]))
    assert sorted(r[0] for r in results) == [200, 409]
    won = next(r[1] for r in results if r[0] == 200)
    lost = l1 if won["id"] == l2["id"] else l2
    assert next(r[1] for r in results if r[0] == 409)["code"] == "KEY_UNAVAILABLE"
    act(borrower if lost["borrowerId"] == bo else borrower2, lost, "cancel")
    won = act(borrower if won["borrowerId"] == bo else borrower2, won, "accept")
    # Revoke before issue blocks it; approval alone never occupies a key.
    k = key("Revoked approval")
    g = grant(k, bo)
    l = act(approver, request(borrower, g), "approve")
    approver.call("/grants/" + str(g["id"]) + "/revoke", "POST", {"version": 1})
    custodian.call(
        f'/loans/{l["id"]}/issue',
        "POST",
        {"version": l["version"], "note": "TEST"},
        409,
        "GRANT_INACTIVE",
    )
    act(borrower, l, "cancel")
    # Rejection and editable master fields remain actual APIs.
    k = key("Declined request")
    g = grant(k, bo)
    l = request(borrower, g)
    act(approver, l, "reject")
    k["name"] = "TEST Declined request (edited)"
    custodian.call("/keys/" + str(k["id"]), "PUT", k)
    admin.call("/reports")
    admin.call("/admin/menus")
    admin.call("/admin/settings")
    clients["auditor"].call("/reports")
    assert "passwordHash" not in json.dumps(admin.call("/admin/users"))
    state = {
        "product": "KeyDesk",
        "adminUsername": env.get("ADMIN_USERNAME", "admin"),
        "adminPassword": env["ADMIN_PASSWORD"],
        "users": users,
        "snapshot": snapshot(admin),
        "concurrency": [r[0] for r in results],
    }
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)
    print(
        json.dumps(
            {
                "mode": "create",
                "httpChecks": Client.count,
                "businessFlows": 7,
                "singleOccupancyRace": True,
                "roles": 6,
                "privateStateWritten": True,
            }
        )
    )


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base", required=True)
    p.add_argument("--env-file")
    p.add_argument("--state-output")
    p.add_argument("--state-input")
    a = p.parse_args()
    if urllib.parse.urlsplit(a.base).hostname not in ["127.0.0.1", "localhost", "::1"]:
        p.error("Only your designated isolated localhost test instance is supported")
    if a.state_input:
        verify(a)
    elif a.env_file and a.state_output:
        create(a)
    else:
        p.error("Provide --env-file and --state-output, or --state-input")


if __name__ == "__main__":
    main()
