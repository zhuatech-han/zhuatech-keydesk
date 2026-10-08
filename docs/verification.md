# KeyDesk 1.0.0 验证记录 / Verification

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

## 2026-10-08 实际验收

| 检查 | 结果 |
|---|---|
| Java 21 / Maven / Spotless / package | Docker 构建实际执行，32 项测试通过：20 项 HTTP/JPA 集成，12 项业务规则单元测试；0 失败、错误或跳过 |
| 前端 | npm ci、Prettier、ESLint、8 项测试和 Vite 构建通过 |
| 依赖检查 | 锁定版本经 npm 官方审计，报告 0 项已知漏洞；这不代表不存在未知漏洞 |
| Compose / 全新数据库 | 配置校验、后端与前端镜像构建通过，全新 MySQL 8.4 卷启动，V1/V2 迁移成功，三服务健康 |
| 真实业务与权限 | 133 次 HTTP 检查，7 类流程，6 个测试岗位；正常归还、损坏隔离与独立放行、丢失处置、停用账号代收复核、撤销、拒绝与取消通过 |
| 并发占用 | 两个领用人并发交出同一把钥匙，恰好一次成功，另一请求 409；数据库只保留一个实物占用 |
| 实际页面 | 领用人申请归还、保管员完好核验成功，六步事件持久化；普通领用人只见本人记录；下载的 CSV 与服务端持久化结果逐字一致 |
| 重启 | 41 次重新登录与快照检查通过；业务、目录、事件、非登录审计、CSV 一致 |
| 备份恢复 | 停写逻辑备份及摘要校验通过，恢复到另一全新独立卷；41 次检查与权限重新验证通过，全部快照一致 |
| 发布材料 | 两版 README 互链、8 张真实截图、两个原始二维码及 Logo 字节核验、联系方式、授权与秘密模式扫描通过 |

测试仅在本机独立 Compose 项目及随机私有账号中执行。没有真实客户数据、外部硬件、短信或付费服务。测试账号、密码、Cookie、数据库备份与完整私有状态文件不随源码发布。记录中仅将实例复用、原始品牌素材、已验证行为列为结果。

后端测试在 Docker 的 Java 21 / Maven 3.9 构建阶段执行；本机没有另装 Maven。H2 用于后端集成测试，真实 MySQL 8.4 用于完整部署与业务验收，两者的结果分开记录。数据库时间使用微秒 UTC 精度，以保证授权和批准在到期边界拒绝；页面按浏览器时区显示。

未做高并发负载认证、外部硬件验收、渗透测试、生产部署认证、持续商业运营或真实设施保管测试。本版为源码公开的非商业学习系统。

## Reproduce

See the commands in [README](../README.en.md), [deployment and restore](deployment.md) and the localhost-only `scripts/smoke.py`. Build tests: backend `mvn spotless:check clean verify`; frontend `npm ci`, `npm run format:check`, `npm run lint`, `npm test`, `npm run build`. Run release checks with `python3 scripts/release-check.py`. Docker backend builds execute tests without skip flags.

English summary: 32 backend tests and 8 frontend tests passed. A fresh MySQL deployment passed 133 HTTP checks across seven custody workflows, role restrictions and exclusive-issue concurrency. Actual browser return/inspection and CSV downloads were verified. Restart and isolated backup restoration each passed 41 checks with matching business, identity, event, audit and export snapshots. These results are for a localhost learning instance; they do not certify production capacity, physical operations or external integrations.
