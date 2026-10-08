# KeyDesk API

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

同源Cookie会话。GET `/api/auth/csrf`获得动态请求头与令牌，所有写入携带该头。POST `/api/auth/login`传`username,password`；GET `/api/auth/me`获得当前权限、范围和菜单；POST `/api/auth/logout`退出；POST `/api/auth/password`传`oldPassword,newPassword`修改并撤销会话。密码不写日志、不存浏览器持久存储。

| 路径 | 方法 | 输入与行为 |
|---|---|---|
| `/api/keys` | GET/POST | 范围台账；新增`code,name,departmentId,category,cabinet,note` |
| `/api/keys/{id}` | PUT | 原编号/部门与`version`；只编辑描述 |
| `/api/keys/{id}/retire` | POST | `version`，无未结案记录才退役 |
| `/api/grants` | GET/POST | 新增`keyId,borrowerId,validFrom,validUntil,reason`；时间ISO8601 UTC |
| `/api/grants/{id}/revoke` | POST | `version`；后续批准与交出失效 |
| `/api/loans` | GET/POST | 新增`grantId,dueAt,purpose`，必须本人 |
| `/api/loans/{id}` | GET | 领还记录与事件 |
| `/api/loans/{id}/{action}` | POST | `version,note`；`receive,recover`另需`inspection:GOOD/DAMAGED` |
| `/api/reviews` | GET/POST | 隔离检查申请`keyId,reason` |
| `/api/reviews/{id}/decision` | POST | `version,approved,decision`，独立账号 |
| `/api/options` | GET | 范围内表单目录，不返回密码或登录名 |
| `/api/reports` / `/api/reports.csv` | GET | 范围统计 / CSV |
| `/api/audit` | GET | 当前范围操作审计 |
| `/api/admin/{kind}` | GET/POST | `users,roles,departments,dictionaries`可新增；其他固定目录仅编辑 |
| `/api/admin/{kind}/{id}` | PUT | 修改目录，账号/角色/部门带`version`；保留最后管理员 |
| `/api/admin/options` | GET | 账号维护的部门和角色选项 |

`action`为`approve,reject,cancel,issue,accept,return,receive,loss,resolve,recover,confirm_recovery`。客户端不能直接传目标状态。HTTP401会话失效，403权限或范围不足，400格式错误，409状态/版本/约束不满足，413实例列表上限。合法状态和岗位详见操作手册。数据列表最多10000条，在浏览器筛选、分页、排序；不是服务端游标接口。

English: use session cookies and the actual CSRF header for writes. Every custody mutation carries the record version and a factual note. Physical receipt additionally carries inspection result. Never replay a write automatically after an interrupted response. Only fixed entity routes are accepted; role and scope checks happen transactionally.
