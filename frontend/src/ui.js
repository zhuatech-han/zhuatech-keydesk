// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref("zh");
/** 页面语言切换，不翻译业务输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const t = (zh, en) => (language.value === "zh" ? zh : en);
const states = {
  IN: ["在柜", "In cabinet"],
  OUT: ["已交出", "Out"],
  QUARANTINED: ["隔离中", "Quarantined"],
  RETIRED: ["已退役", "Retired"],
  REQUESTED: ["待批准", "Requested"],
  APPROVED: ["已批准", "Approved"],
  HANDOVER_PENDING: ["待领用确认", "Awaiting acceptance"],
  ISSUED: ["领用中", "In custody"],
  RETURN_REQUESTED: ["待归还核验", "Awaiting inspection"],
  RETURN_REVIEW: ["收回待独立核验", "Recovery in review"],
  RETURNED: ["已归还", "Returned"],
  LOST: ["丢失待处置", "Loss reported"],
  RESOLVED: ["已处置", "Resolved"],
  REJECTED: ["已拒绝", "Rejected"],
  CANCELLED: ["已取消", "Cancelled"],
};
/** 只映射服务端实际状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const stateName = (s) => (states[s] ? t(...states[s]) : s);
const errors = {
  UNAUTHENTICATED: [
    "会话已失效，请重新登录。",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: [
    "账号、密码不正确或已停用。",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: [
    "尝试过多，五分钟后重试。",
    "Too many attempts. Retry in five minutes.",
  ],
  FORBIDDEN: ["没有这项操作权限。", "Permission denied."],
  OUT_OF_SCOPE: ["记录不在当前数据范围。", "Record is outside your scope."],
  VERSION_CONFLICT: [
    "记录已变更，请刷新核对。",
    "Record changed. Refresh and check.",
  ],
  INDEPENDENT_REQUIRED: [
    "必须由另一账号办理，不能自审或自行核验。",
    "Another account is required.",
  ],
  KEY_UNAVAILABLE: [
    "钥匙不在柜，无法申请或交出。",
    "Key is not available in the cabinet.",
  ],
  KEY_OCCUPIED: [
    "钥匙仍有未结束的交接或领还记录。",
    "Key has an unresolved custody record.",
  ],
  GRANT_INACTIVE: [
    "授权已撤销、未生效或过期。",
    "Grant revoked, not yet active or expired.",
  ],
  GRANT_OVERLAP: ["同一人的授权时间重叠。", "Grants for this person overlap."],
  GRANT_DATES_INVALID: [
    "授权时间需递增，跨度不超过366天。",
    "Grant times must increase, up to 366 days.",
  ],
  DUE_INVALID: [
    "归还期限须在授权与最长领用小时数内。",
    "Due time exceeds grant or maximum loan hours.",
  ],
  APPROVAL_EXPIRED: [
    "批准已过期或归还期限已到。",
    "Approval expired or due time reached.",
  ],
  BORROWER_ONLY: [
    "这项操作必须由领用人本人办理。",
    "Only the borrower can perform this action.",
  ],
  BORROWER_DISABLED: [
    "领用账号停用、部门或权限不匹配。",
    "Borrower disabled or incompatible permissions/scope.",
  ],
  OPEN_REQUEST_EXISTS: [
    "已有未结案申请，请先处理现有记录。",
    "An unresolved request already exists.",
  ],
  STATE_INVALID: [
    "当前状态不能执行此操作。",
    "Action is invalid for the current state.",
  ],
  NOTE_REQUIRED: [
    "请填写完整的检查或处置说明。",
    "Enter a complete inspection or resolution note.",
  ],
  PASSWORD_WEAK: [
    "密码需12–72字节，含大小写字母和数字。",
    "Password requires 12–72 bytes, uppercase, lowercase and a digit.",
  ],
  LAST_ADMIN: [
    "必须保留一个完整管理员。",
    "Keep at least one full administrator.",
  ],
  CONFLICT: [
    "编号重复或目录仍被引用。",
    "Duplicate code or referenced directory.",
  ],
  INVALID_INPUT: [
    "字段格式不正确，请检查表单。",
    "Invalid fields. Check the form.",
  ],
  CATEGORY_INVALID: ["分类已停用或不存在。", "Category disabled or missing."],
  RESULT_UNKNOWN: [
    "提交结果未知，请刷新核对后再操作。",
    "Submission result unknown. Refresh and check.",
  ],
  NETWORK_ERROR: [
    "连接失败，请刷新重试。",
    "Connection failed. Refresh and retry.",
  ],
  ACCOUNT_ASSIGNED: [
    "已有业务历史，不能变更部门。",
    "Business history prevents department reassignment.",
  ],
  IDENTITY_LOCKED: [
    "稳定编号或所属部门不可变更。",
    "Code or department is immutable.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "Current password is incorrect."],
};
/** 错误不被当作成功，未知代码明确显示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const errorMessage = (e) =>
  errors[e.message]
    ? t(...errors[e.message])
    : t("操作未完成：", "Action failed: ") + e.message;
/** API记录拷贝后编辑，不修改原列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const cloneRecord = (r) => JSON.parse(JSON.stringify(r));
/** 过滤与分页仅影响展示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function filterRows(rows, q, state) {
  const n = q.trim().toLowerCase();
  return rows.filter(
    (r) =>
      (!state || r.state === state) &&
      (!n || JSON.stringify(r).toLowerCase().includes(n)),
  );
}
/** 日期输入转换为UTC；无效日期拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function utc(value) {
  const d = new Date(value);
  if (!Number.isFinite(d.getTime())) throw new Error("INVALID_INPUT");
  return d.toISOString();
}
