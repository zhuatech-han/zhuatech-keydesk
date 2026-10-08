#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""检查发布文件、相对链接、原始品牌素材及禁入项；不输出秘密。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HASHES = {
    "frontend/public/brand/logo.jpg": "90bf87ff294f5ee8cdaef4b10155139173d97d93a88d7ea9679b9721ba05c962",
    "docs/images/wechat-zhuatech.png": "a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1",
    "docs/images/wechat-zhuatech2.png": "98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215",
}


def main():
    for file in [
        "README.md",
        "README.en.md",
        "LICENSE",
        "NOTICE",
        ".env.example",
        "compose.yaml",
        "docs/api.md",
        "docs/operations.md",
        "docs/security.md",
        "docs/deployment.md",
    ]:
        assert (ROOT / file).is_file(), f"Missing required file: {file}"
    for name in ["README.md", "README.en.md"]:
        s = (ROOT / name).read_text()
        assert s.startswith("[中文](README.md) | [English](README.en.md)")
        assert "https://www.zhuatech.cn/" in s
        for dest in re.findall(r'\]\(([^)]+)\)|src="([^"]+)"', s):
            target = next(d for d in dest if d)
            if not re.match(r"^(https?://|mailto:|#)", target):
                assert (
                    ROOT / target.split("#")[0]
                ).is_file(), f"Invalid README link: {target}"
    cn = (ROOT / "README.md").read_text()
    en = (ROOT / "README.en.md").read_text()
    for value in [
        "wechat-zhuatech.png",
        "wechat-zhuatech2.png",
        "zhuatech2",
        "上海如静知华信息科技有限公司",
    ]:
        assert value in cn
    assert "wechat-" not in en
    for v in ["han@zhuatech.cn", "jack@zhuatech.cn", "https://wa.me/8617521234993"]:
        assert v in en
    assert "未经书面授权不得商用" in (ROOT / "LICENSE").read_text()
    for file, digest in HASHES.items():
        assert (
            hashlib.sha256((ROOT / file).read_bytes()).hexdigest() == digest
        ), f"Original brand bytes changed: {file}"
    tracked = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT,
        text=True,
    ).splitlines()
    bad = re.compile(
        r"BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY|github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9]{30,}|AKIA[A-Z0-9]{16}"
    )
    for file in tracked:
        assert file != ".env" and not file.startswith(
            (".venv/", "node_modules/", "frontend/node_modules/", "backend/target/")
        )
        assert not file.endswith((".sql.zip", "-backup.zip", "-quality-state.json"))
        if (
            file.endswith(
                (".java", ".js", ".vue", ".py", ".md", ".yml", ".yaml", ".xml", ".conf")
            )
            or file == ".env.example"
            or file.endswith("Dockerfile")
        ):
            text = (ROOT / file).read_text(errors="strict")
            assert not bad.search(text), f"Sensitive pattern in: {file}"
            if file.endswith(("Dockerfile", ".yml", ".yaml", ".xml")):
                assert not re.search(
                    r"-DskipTests|-Dmaven.test.skip=true", text
                ), f"Test skip flag: {file}"
    print(
        f"Passed: {len(tracked)} publishable files, README links, original assets, contacts, license and secret-pattern checks"
    )


if __name__ == "__main__":
    main()
