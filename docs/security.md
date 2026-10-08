# KeyDesk 安全与边界 / Security

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

- BCrypt12轮，12–72 UTF-8字节密码；Cookie HttpOnly/SameSiteStrict；写操作CSRF。登录连续失败8次/5分钟节流；用户、角色和部门每请求实时读取，停用或密码重置立即拒绝旧会话。
- 审批/签发授权/交出/归还核验不能由领用人本人操作；代收与隔离放行需要另一复核人；丢失处置不能由领用人或上报人自行批准。UI隐藏按钮不能替代后端门禁。
- 所有业务写入与后台目录修改在同一实例行锁内串行，事务回滚避免局部修改，状态版本拒绝重放。API不接受目标状态、不直接删除业务历史。
- 数据范围由后端检查；领用人无权读取其他人的领还详情，密码散列和其他钥匙当前占用记录ID不返回。账号目录为管理员专用。
- 固定JPQL与绑定参数，CSV公式防护，Nginx限制请求128KB。不提供附件上传、外部URL抓取、脚本执行或硬件控制接口。
- 应用日志不输出密码、会话或完整业务载荷；审计只记录身份、动作、对象和时间。业务事件会保留人工填写的办理说明，勿填真实敏感数据用于公开展示。
- 审计不是防篡改公证。软件无法判定人是否拿到或归还了实物，也无法消除恶意账号、数据库管理员或现场串通的风险。
- 本机默认HTTP和非SecureCookie仅用于学习；外部部署须HTTPS、网络隔离、可信数据库TLS及独立审查。未作完整渗透、生产负载或硬件测试。

发现漏洞请私下发送脱敏复现到han@zhuatech.cn或jack@zhuatech.cn，不公开密钥、真实位置、客户资料或可利用的生产细节。第三方依赖问题按其官方渠道和当前公告处理。

English: manual physical assertions remain the responsibility of staff. Transactional scopes, version checks, independent actors, CSRF and current identity checks protect application workflows; they do not prove physical custody or provide tamper-proof audit. Keep configuration and backups private. Report vulnerabilities privately with sanitized reproduction details.
