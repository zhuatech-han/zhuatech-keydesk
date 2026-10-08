# KeyDesk 操作手册 / Operations

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

1. 管理员建立部门和独立个人账号，选择审批员、保管员、领用人、审计员角色；密码由私有渠道交付，不公开共享。
2. 保管员“钥匙台账”新增实际单件编号、名称、分类和柜位。编号与部门固定，后续修改描述不改变历史归属。
3. 审批员在“领用授权”为另一领用账号选择钥匙和有效期，填写依据；不能自己给自己授权。同一人同一钥匙有效授权不重叠。
4. 领用人“领还办理”选择自己的有效授权，填写用途和归还时间。期限必须未来、不超过系统最长领用小时数且在授权结束之前。
5. 另一审批账号检查后批准或拒绝；批准有有限交出有效期。保管员现场交出实物后记录“交出钥匙”；系统立即占用，领用人确认已领到。
6. 领用人携实物归还并申请；另一保管员核对编号和完好情况后办理“核验收回”。申请归还本身不会让钥匙变为可用。
7. 损坏归还后隔离：保管员填写检查或维修说明，另一审批账号核验放行或拒绝。拒绝仍隔离，可以重新检查申请。
8. 账号停用或无法本人确认：保管员仅在实物确已收回后“代收实物”，钥匙隔离，另一审批账号“独立核验代收”结案。
9. 丢失不得伪造归还：上报后保持隔离与占用；另一审批账号记录实际外部处置凭据、换锁或风险处理结果，原钥匙退役。软件不执行换锁，也不判断现场风险。
10. 逾期报表统计已交出、待确认或待归还核验且超过期限的记录。已代收待复核不算实物逾期，仍受隔离门禁。导出仅当前账号范围，文件由你妥善保存。

刷新会重新读取台账。版本冲突和未知网络结果须先刷新核对，不能盲目重试。配置权限仍需满足独立账号约束。事件按办理顺序追加，时间UTC存储、浏览器时区展示。

English: create separate staff accounts, register physical keys, grant another named person explicit validity, request with a bounded due time, obtain independent approval, record actual issue and acknowledge receipt. A return request is followed by another custodian's inspection. Damaged items and staff-recovered items remain quarantined pending independent review. Loss resolution retires the item and requires real external evidence. Software never performs physical recovery or lock replacement.
