<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { computed, onMounted, ref, watch } from "vue";
import {
  KeyRound,
  LayoutList,
  ShieldCheck,
  ClipboardCheck,
  BarChart3,
  Users,
  Settings,
  LogOut,
  Search,
  Plus,
  X,
  RefreshCw,
  ArrowRight,
  History,
} from "@lucide/vue";
import { api, resetApi, download } from "./api.js";
import {
  t,
  language,
  stateName,
  errorMessage,
  cloneRecord,
  filterRows,
  utc,
} from "./ui.js";
import AdminPanel from "./components/AdminPanel.vue";
const me = ref(null),
  section = ref("loans"),
  pending = ref(false),
  error = ref(""),
  notice = ref(""),
  ready = ref(false);
const login = ref({ username: "", password: "" });
const keys = ref([]),
  grants = ref([]),
  loans = ref([]),
  reviews = ref([]),
  rows = ref([]),
  report = ref(null),
  options = ref({ departments: [], accounts: [], categories: [] });
const q = ref(""),
  state = ref(""),
  page = ref(1),
  order = ref("newest"),
  dialog = ref(null),
  detail = ref(null),
  password = ref({ oldPassword: "", newPassword: "" });
const can = (code) => me.value?.permissions.includes(code);
const own = (r) => r.borrowerId === me.value?.id;
const staff = () => me.value?.scope !== "ASSIGNED";
const visibleMenus = computed(() =>
  (me.value?.menus || []).filter((m) => m.code !== "reviews" || staff()),
);
const icons = {
  keys: KeyRound,
  loans: LayoutList,
  grants: ShieldCheck,
  reviews: ClipboardCheck,
  reports: BarChart3,
  users: Users,
  roles: ShieldCheck,
  settings: Settings,
  audit: History,
};
const keyName = (id) => keys.value.find((k) => k.id === id)?.name || `#${id}`;
const person = (id) =>
  options.value.accounts.find((a) => a.id === id)?.name ||
  (id === me.value?.id ? me.value.displayName : `#${id}`);
const titles = computed(() => ({
  keys: t("钥匙台账", "Key register"),
  loans: t("领还办理", "Custody desk"),
  grants: t("领用授权", "Grants"),
  reviews: t("隔离处置", "Quarantine review"),
  reports: t("状态报表", "Reports"),
  audit: t("审计记录", "Audit trail"),
}));
const activeRows = computed(() =>
  section.value === "keys"
    ? keys.value
    : section.value === "grants"
      ? grants.value
      : section.value === "loans"
        ? loans.value
        : section.value === "reviews"
          ? reviews.value
          : rows.value,
);
const enriched = computed(() =>
  activeRows.value.map((r) => ({
    ...r,
    keyName: r.keyId ? keyName(r.keyId) : r.name,
    borrowerName: r.borrowerId ? person(r.borrowerId) : "",
  })),
);
const filtered = computed(() =>
  filterRows(enriched.value, q.value, state.value).sort((a, b) =>
    order.value === "oldest" ? a.id - b.id : b.id - a.id,
  ),
);
const shown = computed(() =>
  filtered.value.slice((page.value - 1) * 15, page.value * 15),
);
const states = computed(() => [
  ...new Set(activeRows.value.map((r) => r.state).filter(Boolean)),
]);
const ownOpen = computed(
  () =>
    loans.value.filter(
      (l) =>
        own(l) &&
        ["HANDOVER_PENDING", "ISSUED", "RETURN_REQUESTED"].includes(l.state),
    ).length,
);
const displayTime = (v) =>
  v
    ? new Date(v).toLocaleString(language.value === "zh" ? "zh-CN" : "en-GB")
    : "—";
const isOverdue = (l) =>
  ["HANDOVER_PENDING", "ISSUED", "RETURN_REQUESTED"].includes(l.state) &&
  new Date(l.dueAt) < new Date();
watch([q, state, order], () => (page.value = 1));
watch(section, () => {
  q.value = "";
  state.value = "";
  page.value = 1;
  detail.value = null;
  load();
});
/** 所有写入先显示处理中，不重试未知结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(path, method, body) {
  if (pending.value) return null;
  pending.value = true;
  error.value = "";
  notice.value = "";
  try {
    const result = await api(path, { method, body });
    notice.value = t("已保存", "Saved");
    return result;
  } catch (e) {
    handle(e);
    return null;
  } finally {
    pending.value = false;
  }
}
function handle(e) {
  error.value = errorMessage(e);
  if (e.status === 401) {
    me.value = null;
    resetApi();
  }
}
/** 批次读取实际台账，失败时隐藏旧数据，避免操作过期表单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  if (!me.value) return;
  ready.value = false;
  try {
    const values = await Promise.all(
      ["/keys", "/grants", "/loans", "/reviews", "/options"].map((p) => api(p)),
    );
    [keys.value, grants.value, loans.value, reviews.value, options.value] =
      values;
    if (section.value === "reports") report.value = await api("/reports");
    if (section.value === "audit") rows.value = await api("/audit");
    ready.value = true;
  } catch (e) {
    handle(e);
  }
}
async function signIn() {
  const result = await perform("/auth/login", "POST", login.value);
  login.value.password = "";
  if (result) {
    resetApi();
    me.value = result;
    section.value = result.menus[0]?.code || "loans";
    notice.value = "";
    await load();
  }
}
async function signOut() {
  await perform("/auth/logout", "POST", {});
  me.value = null;
  resetApi();
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    await load();
  } catch (e) {
    if (e.status !== 401) handle(e);
  }
});
/** 新建明确的主数据或本人申请，不自动填入业务事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function create() {
  const kind = section.value;
  let body = {};
  if (kind === "keys")
    body = {
      code: "",
      name: "",
      departmentId: options.value.departments[0]?.id || "",
      category: options.value.categories[0]?.code || "",
      cabinet: "",
      note: "",
    };
  if (kind === "grants")
    body = {
      keyId: "",
      borrowerId: "",
      validFrom: "",
      validUntil: "",
      reason: "",
    };
  if (kind === "loans") body = { grantId: "", dueAt: "", purpose: "" };
  if (kind === "reviews") body = { keyId: "", reason: "" };
  dialog.value = { kind, body };
}
function editKey(r) {
  dialog.value = { kind: "keys", body: cloneRecord(r) };
}
const createAllowed = computed(
  () =>
    ({
      keys: can("manage") && staff(),
      grants: can("approve") && staff(),
      loans: can("request"),
      reviews: can("custody") && staff(),
    })[section.value],
);
const actions = computed(() => ({
  APPROVE: t("批准", "Approve"),
  REJECT: t("拒绝", "Reject"),
  CANCEL: t("取消申请", "Cancel"),
  ISSUE: t("交出钥匙", "Issue key"),
  ACCEPT: t("确认已领到", "Accept key"),
  RETURN: t("申请归还", "Request return"),
  RECEIVE: t("核验收回", "Inspect & receive"),
  LOSS: t("上报丢失", "Report loss"),
  RESOLVE: t("独立处置", "Resolve loss"),
  RECOVER: t("代收实物", "Recover key"),
  CONFIRM_RECOVERY: t("独立核验代收", "Verify recovery"),
}));
/** 按岗位显示候选动作；服务端仍独立执行权限及版本门禁。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function allowed(l) {
  const a = [];
  if (l.state === "REQUESTED" && can("approve") && staff() && !own(l))
    a.push("APPROVE", "REJECT");
  if (
    ["REQUESTED", "APPROVED"].includes(l.state) &&
    (own(l) || (can("approve") && staff()))
  )
    a.push("CANCEL");
  if (l.state === "APPROVED" && can("custody") && staff() && !own(l))
    a.push("ISSUE");
  if (l.state === "HANDOVER_PENDING" && own(l) && can("request"))
    a.push("ACCEPT");
  if (["HANDOVER_PENDING", "ISSUED"].includes(l.state)) {
    if (own(l) && can("request")) a.push("RETURN");
    if (can("custody") && staff() && !own(l)) a.push("RECOVER");
  }
  if (l.state === "RETURN_REQUESTED" && can("custody") && staff() && !own(l))
    a.push("RECEIVE");
  if (
    ["HANDOVER_PENDING", "ISSUED", "RETURN_REQUESTED"].includes(l.state) &&
    ((own(l) && can("request")) || (can("custody") && staff()))
  )
    a.push("LOSS");
  if (
    l.state === "LOST" &&
    can("approve") &&
    staff() &&
    !own(l) &&
    l.lossReporterId !== me.value.id
  )
    a.push("RESOLVE");
  if (
    l.state === "RETURN_REVIEW" &&
    can("approve") &&
    staff() &&
    !own(l) &&
    l.receiverId !== me.value.id
  )
    a.push("CONFIRM_RECOVERY");
  return a;
}
function openAction(r, action) {
  dialog.value = {
    kind: "action",
    row: r,
    action,
    body: { version: r.version, note: "", inspection: "GOOD" },
  };
}
async function openDetail(r) {
  try {
    detail.value = await api("/loans/" + r.id);
  } catch (e) {
    handle(e);
  }
}
/** 日期转UTC再提交，成功后刷新所有关联台账。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  const d = dialog.value;
  let path,
    method = "POST",
    body = cloneRecord(d.body);
  try {
    if (d.kind === "action")
      path = `/loans/${d.row.id}/${d.action.toLowerCase()}`;
    else if (d.kind === "decision") path = `/reviews/${d.row.id}/decision`;
    else if (d.kind === "revoke") path = `/grants/${d.row.id}/revoke`;
    else if (d.kind === "retire") path = `/keys/${d.row.id}/retire`;
    else {
      path = "/" + d.kind + (body.id ? "/" + body.id : "");
      if (body.id) method = "PUT";
      if (d.kind === "grants") {
        body.validFrom = utc(body.validFrom);
        body.validUntil = utc(body.validUntil);
      }
      if (d.kind === "loans") body.dueAt = utc(body.dueAt);
    }
    const out = await perform(path, method, body);
    if (out) {
      dialog.value = null;
      detail.value = null;
      await load();
    }
  } catch (e) {
    handle(e);
  }
}
async function exportReport() {
  try {
    await download("/reports.csv", "keydesk-custody.csv");
  } catch (e) {
    handle(e);
  }
}
async function changePassword() {
  if (await perform("/auth/password", "POST", password.value)) {
    password.value = { oldPassword: "", newPassword: "" };
    me.value = null;
    resetApi();
  }
}
</script>
<template>
  <div v-if="!me" class="login-layout">
    <section class="login-intro">
      <img src="/brand/logo.jpg" alt="知华科技" width="160" />
      <div class="login-copy">
        <span class="eyebrow">KEYDESK · 1.0</span>
        <h1>{{ t("实体钥匙领还管理", "Physical key custody") }}</h1>
        <p>
          {{
            t(
              "实名授权 · 独立批准 · 双方交接 · 实物归还核验",
              "Named grants · Independent approval · Two-party custody · Inspected returns",
            )
          }}
        </p>
        <div class="login-line">
          <KeyRound :size="25" /><span>{{
            t("部门管理与个人领用工作台", "Department & borrower workspace")
          }}</span>
        </div>
      </div>
      <p class="muted">
        {{
          t(
            "知华科技（上海如静知华信息科技有限公司）",
            "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
          )
        }}
      </p>
    </section>
    <section class="login-form">
      <button
        class="language"
        @click="language = language === 'zh' ? 'en' : 'zh'"
      >
        {{ language === "zh" ? "English" : "中文" }}
      </button>
      <form @submit.prevent="signIn">
        <h2>{{ t("登录工作台", "Sign in to your workspace") }}</h2>
        <p class="muted">
          {{
            t(
              "使用管理员创建的个人账号。",
              "Use your personal account created by your administrator.",
            )
          }}
        </p>
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="login.username"
            required
            autocomplete="username"
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="login.password"
            type="password"
            required
            autocomplete="current-password"
            maxlength="128"
        /></label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button class="primary full" :disabled="pending">
          {{ pending ? t("登录中…", "Signing in…") : t("登录", "Sign in")
          }}<ArrowRight :size="16" />
        </button>
        <p class="hint">
          {{
            t(
              "公开源码学习版，未经书面授权不得商用。",
              "Source available for personal non-commercial learning. Commercial use requires written authorization.",
            )
          }}
        </p>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技官网与商业咨询", "ZhiHua Technology · Website & enquiries")
        }}</a>
      </form>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><b>KeyDesk</b
        ><span>{{ t("实体钥匙领还", "Physical key custody") }}</span>
      </div>
      <nav>
        <button
          v-for="m in visibleMenus"
          :key="m.code"
          :class="{ active: section === m.code }"
          :disabled="pending"
          @click="section = m.code"
        >
          <component :is="icons[m.code] || LayoutList" :size="18" />{{
            language === "zh" ? m.name : m.nameEn
          }}</button
        ><button
          :class="{ active: section === 'about' }"
          @click="section = 'about'"
        >
          <Settings :size="18" />{{ t("账号与关于", "Account & about") }}
        </button>
      </nav>
      <div class="sidebar-foot">
        <span>{{ me.displayName }}</span
        ><small>{{ me.role }}</small
        ><button @click="signOut">
          <LogOut :size="16" />{{ t("退出", "Sign out") }}
        </button>
      </div>
    </aside>
    <main>
      <header class="topbar">
        <span>{{
          t("知华科技 · 钥匙管理工作台", "ZhiHua Technology · Key workspace")
        }}</span
        ><button @click="language = language === 'zh' ? 'en' : 'zh'">
          {{ language === "zh" ? "English" : "中文" }}
        </button>
      </header>
      <div class="content">
        <p v-if="error" role="alert" class="error">
          {{ error }}<button @click="error = ''">×</button>
        </p>
        <p v-if="notice" role="status" class="notice">{{ notice }}</p>
        <AdminPanel
          v-if="['users', 'roles', 'settings'].includes(section)"
          :section="section"
          :pending="pending"
          :perform="perform"
          @error="handle"
        />
        <section v-else-if="section === 'about'" class="about">
          <h1>{{ t("账号与关于", "Account & about") }}</h1>
          <div class="panel">
            <h2>KeyDesk 1.0.0</h2>
            <p>
              {{
                t(
                  "知华科技（上海如静知华信息科技有限公司）",
                  "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
                )
              }}
            </p>
            <p>
              {{
                t(
                  "人工交接台账，未连接钥匙柜或门禁。软件记录不等同于已验证实物，核验由操作人员负责。",
                  "Manual custody register. No key cabinet or access-control integration. Software records do not verify physical custody; staff perform inspections.",
                )
              }}
            </p>
            <p>
              {{
                t(
                  "源码公开、非商业使用；商用须书面授权。",
                  "Source available, non-commercial use. Commercial use requires written authorization.",
                )
              }}
            </p>
            <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
              >https://www.zhuatech.cn/</a
            >
            <p v-if="language === 'zh'">
              {{
                t(
                  "商业授权或深度定制开发请联系知华科技。微信：zhuatech、zhuatech2。",
                  "",
                )
              }}
            </p>
            <p v-else>
              <a href="mailto:han@zhuatech.cn">han@zhuatech.cn</a> ·
              <a href="mailto:jack@zhuatech.cn">jack@zhuatech.cn</a> ·
              <a href="https://wa.me/8617521234993">WhatsApp +86 17521234993</a>
            </p>
          </div>
          <form class="panel narrow" @submit.prevent="changePassword">
            <h2>{{ t("修改个人密码", "Change password") }}</h2>
            <label
              >{{ t("原密码", "Current password")
              }}<input
                v-model="password.oldPassword"
                type="password"
                required
                autocomplete="current-password" /></label
            ><label
              >{{ t("新密码", "New password")
              }}<input
                v-model="password.newPassword"
                type="password"
                required
                autocomplete="new-password"
                minlength="12"
                maxlength="72" /></label
            ><button class="primary" :disabled="pending">
              {{ t("保存并重新登录", "Save & sign in again") }}
            </button>
          </form>
        </section>
        <section v-else-if="section === 'reports'">
          <header class="page-heading">
            <div>
              <h1>{{ t("状态报表", "Custody reports") }}</h1>
              <p class="muted">
                {{
                  t(
                    "当前账号范围 · 连续小时期限",
                    "Current account scope · Continuous-hour deadlines",
                  )
                }}
              </p>
            </div>
            <div class="actions">
              <button @click="load">
                <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
              ><button class="primary" @click="exportReport">
                {{ t("导出领还CSV", "Export custody CSV") }}
              </button>
            </div>
          </header>
          <template v-if="ready && report"
            ><div class="metrics">
              <div>
                <small>{{ t("钥匙总数", "Registered keys") }}</small
                ><strong>{{ report.keys }}</strong>
              </div>
              <div>
                <small>{{ t("在柜可用", "In cabinet") }}</small
                ><strong>{{ report.inCabinet }}</strong>
              </div>
              <div>
                <small>{{ t("隔离中", "Quarantined") }}</small
                ><strong>{{ report.quarantined }}</strong>
              </div>
              <div>
                <small>{{ t("逾期未收回", "Overdue custody") }}</small
                ><strong class="warning">{{ report.overdue }}</strong>
              </div>
            </div>
            <div class="panel">
              <h2>{{ t("领还状态分布", "Custody status distribution") }}</h2>
              <div
                v-for="(count, s) in report.states"
                :key="s"
                class="report-line"
              >
                <span>{{ stateName(s) }}</span>
                <div class="bar">
                  <span
                    :style="{
                      width:
                        Math.min(
                          100,
                          (count / Math.max(1, loans.length)) * 100,
                        ) + '%',
                    }"
                  ></span>
                </div>
                <b>{{ count }}</b>
              </div>
              <p v-if="!Object.keys(report.states).length" class="empty">
                {{ t("尚无领还记录", "No custody records yet") }}
              </p>
              <p class="hint">{{ displayTime(report.generatedAt) }}</p>
            </div></template
          >
        </section>
        <section v-else>
          <header class="page-heading">
            <div>
              <span class="eyebrow">KEYDESK / {{ section.toUpperCase() }}</span>
              <h1>{{ titles[section] }}</h1>
              <p class="muted">
                {{
                  section === "loans"
                    ? t(
                        "每次交出需要领用人确认，归还需要保管员核验。",
                        "Borrowers confirm handover; custodians inspect returns.",
                      )
                    : section === "grants"
                      ? t(
                          "实名授权有明确期限，撤销不会自动收回钥匙。",
                          "Named grants have explicit validity. Revocation does not recover a key.",
                        )
                      : section === "reviews"
                        ? t(
                            "实物检查后，由另一账号复核放行。",
                            "A separate account reviews release after physical inspection.",
                          )
                        : t(
                            "维护实际台账，保留全部历史。",
                            "Maintain actual records and retain history.",
                          )
                }}
              </p>
            </div>
            <div class="actions">
              <button :disabled="pending" @click="load">
                <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
              ><button
                v-if="createAllowed"
                class="primary"
                :disabled="pending || !ready"
                @click="create"
              >
                <Plus :size="16" />{{
                  section === "loans"
                    ? t("申请领用", "Request a key")
                    : t("新增", "Add")
                }}
              </button>
            </div>
          </header>
          <div
            v-if="section === 'loans' && can('request')"
            class="personal-strip"
          >
            <KeyRound :size="20" />
            <div>
              <b
                >{{ t("我的待归还钥匙", "My outstanding keys") }} ·
                {{ ownOpen }}</b
              >
              <p>
                {{
                  t(
                    "按约定时间归还。丢失请及时上报，勿以归还结案。",
                    "Return by the agreed time. Report a loss; never record it as a return.",
                  )
                }}
              </p>
            </div>
          </div>
          <div class="panel">
            <div class="section-toolbar">
              <label class="search"
                ><Search :size="16" /><input
                  v-model="q"
                  :aria-label="t('搜索记录', 'Search records')"
                  :placeholder="
                    t('编号、钥匙名称、用途…', 'ID, key name, purpose…')
                  " /></label
              ><select
                v-model="state"
                :aria-label="t('状态筛选', 'Filter state')"
              >
                <option value="">{{ t("全部状态", "All states") }}</option>
                <option v-for="s in states" :key="s" :value="s">
                  {{ stateName(s) }}
                </option></select
              ><select v-model="order" :aria-label="t('排序', 'Order')">
                <option value="newest">
                  {{ t("最近记录优先", "Newest first") }}
                </option>
                <option value="oldest">
                  {{ t("最早记录优先", "Oldest first") }}
                </option></select
              ><small>{{ filtered.length }} {{ t("条", "records") }}</small>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <template v-if="section === 'keys'"
                      ><th>{{ t("编号与钥匙", "Code & key") }}</th>
                      <th>{{ t("保管位置", "Cabinet location") }}</th>
                      <th>{{ t("状态", "Status") }}</th>
                      <th>{{ t("分类", "Category") }}</th></template
                    ><template v-else-if="section === 'grants'"
                      ><th>{{ t("钥匙", "Key") }}</th>
                      <th>{{ t("领用人", "Borrower") }}</th>
                      <th>{{ t("有效期", "Validity") }}</th>
                      <th>{{ t("授权说明", "Reason") }}</th>
                      <th>{{ t("状态", "Status") }}</th></template
                    ><template v-else-if="section === 'loans'"
                      ><th>{{ t("钥匙与用途", "Key & purpose") }}</th>
                      <th>{{ t("领用人", "Borrower") }}</th>
                      <th>{{ t("归还期限", "Due time") }}</th>
                      <th>{{ t("状态", "Status") }}</th></template
                    ><template v-else-if="section === 'reviews'"
                      ><th>{{ t("钥匙", "Key") }}</th>
                      <th>{{ t("检查说明", "Inspection note") }}</th>
                      <th>{{ t("状态", "Status") }}</th>
                      <th>{{ t("复核结论", "Decision") }}</th></template
                    ><template v-else
                      ><th>{{ t("操作者", "Actor") }}</th>
                      <th>{{ t("操作", "Action") }}</th>
                      <th>{{ t("对象", "Object") }}</th>
                      <th>{{ t("时间", "Time") }}</th></template
                    >
                    <th>{{ t("办理", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody v-if="ready">
                  <tr v-for="r in shown" :key="r.id">
                    <td class="mono">{{ r.id }}</td>
                    <template v-if="section === 'keys'"
                      ><td>
                        <b>{{ r.name }}</b
                        ><small class="sub mono">{{ r.code }}</small>
                      </td>
                      <td>{{ r.cabinet }}</td>
                      <td>
                        <span class="badge" :class="r.state">{{
                          stateName(r.state)
                        }}</span>
                      </td>
                      <td>
                        {{
                          options.categories.find(
                            (c) => c.code === r.category,
                          )?.[language === "zh" ? "name" : "nameEn"] ||
                          r.category
                        }}
                      </td>
                      <td>
                        <div class="row-actions">
                          <button
                            v-if="can('manage') && staff()"
                            :disabled="pending || r.state === 'OUT'"
                            @click="editKey(r)"
                          >
                            {{ t("编辑", "Edit") }}</button
                          ><button
                            v-if="
                              can('manage') && staff() && r.state !== 'RETIRED'
                            "
                            :disabled="pending"
                            @click="
                              dialog = {
                                kind: 'retire',
                                row: r,
                                body: { version: r.version },
                              }
                            "
                          >
                            {{ t("退役", "Retire") }}
                          </button>
                        </div>
                      </td></template
                    ><template v-else-if="section === 'grants'"
                      ><td>{{ r.keyName }}</td>
                      <td>{{ r.borrowerName }}</td>
                      <td>
                        {{ displayTime(r.validFrom)
                        }}<small class="sub"
                          >→ {{ displayTime(r.validUntil) }}</small
                        >
                      </td>
                      <td>{{ r.reason }}</td>
                      <td>
                        <span
                          class="badge"
                          :class="r.enabled ? 'IN' : 'RETIRED'"
                          >{{
                            r.enabled
                              ? t("启用", "Enabled")
                              : t("已撤销", "Revoked")
                          }}</span
                        >
                      </td>
                      <td>
                        <button
                          v-if="can('approve') && staff() && r.enabled"
                          :disabled="pending"
                          @click="
                            dialog = {
                              kind: 'revoke',
                              row: r,
                              body: { version: r.version },
                            }
                          "
                        >
                          {{ t("撤销", "Revoke") }}
                        </button>
                      </td></template
                    ><template v-else-if="section === 'loans'"
                      ><td>
                        <b>{{ r.keyName }}</b
                        ><small class="sub">{{ r.purpose }}</small>
                      </td>
                      <td>{{ r.borrowerName }}</td>
                      <td :class="{ warning: isOverdue(r) }">
                        {{ displayTime(r.dueAt)
                        }}<small v-if="isOverdue(r)" class="sub">{{
                          t("已逾期", "Overdue")
                        }}</small>
                      </td>
                      <td>
                        <span class="badge" :class="r.state">{{
                          stateName(r.state)
                        }}</span>
                      </td>
                      <td>
                        <div class="row-actions">
                          <button @click="openDetail(r)">
                            {{ t("详情", "Details") }}</button
                          ><button
                            v-for="a in allowed(r)"
                            :key="a"
                            :disabled="pending"
                            @click="openAction(r, a)"
                          >
                            {{ actions[a] }}
                          </button>
                        </div>
                      </td></template
                    ><template v-else-if="section === 'reviews'"
                      ><td>{{ r.keyName }}</td>
                      <td>{{ r.reason }}</td>
                      <td>
                        <span class="badge" :class="r.state">{{
                          stateName(r.state)
                        }}</span>
                      </td>
                      <td>{{ r.decision || "—" }}</td>
                      <td>
                        <button
                          v-if="
                            r.state === 'REQUESTED' &&
                            can('approve') &&
                            staff() &&
                            r.requestedBy !== me.id
                          "
                          @click="
                            dialog = {
                              kind: 'decision',
                              row: r,
                              body: {
                                version: r.version,
                                approved: true,
                                decision: '',
                              },
                            }
                          "
                        >
                          {{ t("独立复核", "Review") }}
                        </button>
                      </td></template
                    ><template v-else
                      ><td>{{ r.actor }}</td>
                      <td class="mono">{{ r.action }}</td>
                      <td>{{ r.objectId }}</td>
                      <td>{{ displayTime(r.createdAt) }}</td>
                      <td></td
                    ></template>
                  </tr>
                </tbody>
              </table>
              <div v-if="!ready" class="empty">
                {{ t("读取中…", "Loading…") }}
              </div>
              <div v-else-if="!shown.length" class="empty">
                {{ t("没有匹配记录", "No matching records") }}
              </div>
            </div>
            <footer class="pagination">
              <span
                >{{ page }} /
                {{ Math.max(1, Math.ceil(filtered.length / 15)) }}</span
              ><button :disabled="page <= 1" @click="page--">
                {{ t("上一页", "Previous") }}</button
              ><button :disabled="page * 15 >= filtered.length" @click="page++">
                {{ t("下一页", "Next") }}
              </button>
            </footer>
          </div>
        </section>
      </div>
    </main>
  </div>
  <div v-if="dialog" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="
        dialog.kind === 'action'
          ? actions[dialog.action]
          : t('办理表单', 'Record form')
      "
    >
      <header>
        <h2>
          {{
            dialog.kind === "action"
              ? actions[dialog.action]
              : dialog.kind === "decision"
                ? t("独立复核", "Independent review")
                : dialog.kind === "retire"
                  ? t("确认退役", "Confirm retirement")
                  : dialog.kind === "revoke"
                    ? t("确认撤销授权", "Confirm revocation")
                    : t("记录表单", "Record form")
          }}
        </h2>
        <button
          :disabled="pending"
          :aria-label="t('关闭', 'Close')"
          @click="dialog = null"
        >
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="save">
        <template v-if="dialog.kind === 'keys'"
          ><div class="form-grid">
            <label
              >{{ t("稳定编号", "Immutable code")
              }}<input
                v-model="dialog.body.code"
                :readonly="!!dialog.body.id"
                required
                maxlength="60"
                pattern="[A-Za-z0-9_-]{2,60}" /></label
            ><label
              >{{ t("所属部门", "Department")
              }}<select
                v-model="dialog.body.departmentId"
                :disabled="!!dialog.body.id"
                required
              >
                <option
                  v-for="d in options.departments"
                  :key="d.id"
                  :value="d.id"
                >
                  {{ d.name }}
                </option>
              </select></label
            >
          </div>
          <label
            >{{ t("钥匙名称", "Key name")
            }}<input
              v-model="dialog.body.name"
              required
              maxlength="160" /></label
          ><label
            >{{ t("保管位置", "Cabinet location")
            }}<input
              v-model="dialog.body.cabinet"
              required
              maxlength="120" /></label
          ><label
            >{{ t("分类", "Category")
            }}<select v-model="dialog.body.category" required>
              <option
                v-for="c in options.categories"
                :key="c.id"
                :value="c.code"
              >
                {{ language === "zh" ? c.name : c.nameEn }}
              </option>
            </select></label
          ><label
            >{{ t("备注", "Note")
            }}<textarea
              v-model="dialog.body.note"
              maxlength="500"
            ></textarea></label
        ></template>
        <template v-if="dialog.kind === 'grants'"
          ><label
            >{{ t("钥匙", "Key")
            }}<select v-model="dialog.body.keyId" required>
              <option value="">{{ t("请选择", "Select") }}</option>
              <option
                v-for="k in keys.filter((k) => k.state !== 'RETIRED')"
                :key="k.id"
                :value="k.id"
              >
                {{ k.code }} · {{ k.name }}
              </option>
            </select></label
          ><label
            >{{ t("领用人", "Borrower")
            }}<select v-model="dialog.body.borrowerId" required>
              <option value="">{{ t("请选择", "Select") }}</option>
              <option
                v-for="a in options.accounts.filter((a) => a.id !== me.id)"
                :key="a.id"
                :value="a.id"
              >
                {{ a.name }}
              </option>
            </select></label
          >
          <div class="form-grid">
            <label
              >{{ t("生效时间", "Valid from")
              }}<input
                v-model="dialog.body.validFrom"
                type="datetime-local"
                required /></label
            ><label
              >{{ t("到期时间", "Valid until")
              }}<input
                v-model="dialog.body.validUntil"
                type="datetime-local"
                required
            /></label>
          </div>
          <label
            >{{ t("授权依据", "Grant reason")
            }}<textarea
              v-model="dialog.body.reason"
              required
              maxlength="500"
            ></textarea></label
        ></template>
        <template v-if="dialog.kind === 'loans'"
          ><label
            >{{ t("我的授权", "My grant")
            }}<select v-model="dialog.body.grantId" required>
              <option value="">
                {{ t("请选择有效授权", "Select an active grant") }}
              </option>
              <option
                v-for="g in grants.filter(
                  (g) =>
                    g.borrowerId === me.id &&
                    g.enabled &&
                    new Date(g.validFrom) <= new Date() &&
                    new Date(g.validUntil) > new Date(),
                )"
                :key="g.id"
                :value="g.id"
              >
                #{{ g.id }} · {{ keyName(g.keyId) }} ·
                {{ displayTime(g.validUntil) }}
              </option>
            </select></label
          ><label
            >{{ t("约定归还时间", "Agreed due time")
            }}<input
              v-model="dialog.body.dueAt"
              type="datetime-local"
              required /></label
          ><label
            >{{ t("领用用途", "Purpose")
            }}<textarea
              v-model="dialog.body.purpose"
              required
              maxlength="500"
            ></textarea></label
        ></template>
        <template v-if="dialog.kind === 'reviews'"
          ><label
            >{{ t("隔离钥匙", "Quarantined key")
            }}<select v-model="dialog.body.keyId" required>
              <option value="">{{ t("请选择", "Select") }}</option>
              <option
                v-for="k in keys.filter((k) => k.state === 'QUARANTINED')"
                :key="k.id"
                :value="k.id"
              >
                {{ k.name }}
              </option>
            </select></label
          ><label
            >{{ t("实物检查及维修说明", "Physical inspection & repair note")
            }}<textarea
              v-model="dialog.body.reason"
              required
              maxlength="1000"
            ></textarea></label
        ></template>
        <template v-if="dialog.kind === 'action'"
          ><p class="summary">
            #{{ dialog.row.id }} · {{ keyName(dialog.row.keyId) }} ·
            {{ stateName(dialog.row.state) }}
          </p>
          <p v-if="dialog.action === 'ISSUE'" class="hint">
            {{
              t(
                "仅在实物已经交出后确认。确认后钥匙立即标记为已交出，等待领用人确认。",
                "Confirm only after handing over the physical key. It becomes unavailable immediately, pending borrower acceptance.",
              )
            }}
          </p>
          <p v-if="dialog.action === 'RECOVER'" class="hint">
            {{
              t(
                "用于账号停用或无法本人确认等实物代收情形；钥匙隔离至另一审批账号核验。",
                "Use when the borrower cannot confirm, including a disabled account. The recovered key stays quarantined until another approver verifies.",
              )
            }}
          </p>
          <p v-if="dialog.action === 'RESOLVE'" class="hint">
            {{
              t(
                "填写外部处置凭据、换锁或风险处理结果；完成后本钥匙退役。软件不会执行换锁。",
                "Record external resolution evidence and risk handling. This key is retired after resolution. Software does not replace locks.",
              )
            }}
          </p>
          <label v-if="['RECEIVE', 'RECOVER'].includes(dialog.action)"
            >{{ t("实物核验结果", "Inspection result")
            }}<select v-model="dialog.body.inspection" required>
              <option value="GOOD">
                {{ t("完好，编号核对无误", "Good; tag checked") }}
              </option>
              <option value="DAMAGED">
                {{ t("损坏，隔离待处置", "Damaged; quarantine") }}
              </option>
            </select></label
          ><label
            >{{ t("办理说明", "Action note")
            }}<textarea
              v-model="dialog.body.note"
              required
              :minlength="dialog.action === 'RESOLVE' ? 8 : 1"
              maxlength="1000"
            ></textarea></label
        ></template>
        <template v-if="dialog.kind === 'decision'"
          ><label
            >{{ t("复核结果", "Decision")
            }}<select v-model="dialog.body.approved">
              <option :value="true">
                {{ t("通过，解除隔离", "Approve and release") }}
              </option>
              <option :value="false">
                {{ t("拒绝，继续隔离", "Reject and retain quarantine") }}
              </option>
            </select></label
          ><label
            >{{ t("核验说明", "Verification note")
            }}<textarea
              v-model="dialog.body.decision"
              required
              maxlength="1000"
            ></textarea></label
        ></template>
        <p v-if="dialog.kind === 'revoke'" class="hint">
          {{
            t(
              "撤销后不能再批准或交出；实际借出的钥匙仍须归还核验。",
              "Revocation blocks approval and issue. Outstanding keys still require inspected returns.",
            )
          }}
        </p>
        <p v-if="dialog.kind === 'retire'" class="hint">
          {{
            t(
              "退役保留历史；有未结案领还记录时无法退役。",
              "Retirement retains history and is blocked by unresolved custody records.",
            )
          }}
        </p>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <footer>
          <button type="button" :disabled="pending" @click="dialog = null">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="pending">
            {{
              pending
                ? t("保存中…", "Saving…")
                : t("确认保存", "Confirm & save")
            }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="detail" class="modal-backdrop">
    <section
      class="modal wide"
      role="dialog"
      aria-modal="true"
      :aria-label="t('领还详情', 'Custody details')"
    >
      <header>
        <h2>{{ t("领还详情", "Custody details") }} #{{ detail.loan.id }}</h2>
        <button :aria-label="t('关闭', 'Close')" @click="detail = null">
          <X :size="20" />
        </button>
      </header>
      <div class="modal-body">
        <div class="detail-summary">
          <h3>{{ keyName(detail.loan.keyId) }}</h3>
          <span class="badge" :class="detail.loan.state">{{
            stateName(detail.loan.state)
          }}</span>
          <p>{{ detail.loan.purpose }}</p>
          <p>
            {{ t("归还期限", "Due time") }} ·
            {{ displayTime(detail.loan.dueAt) }}
          </p>
          <p>
            {{ t("批准可交出至", "Issue approval valid until") }} ·
            {{ displayTime(detail.loan.approvedUntil) }}
          </p>
          <p>
            {{
              t("交出 / 领用确认 / 归还核验", "Issue / accepted / inspected")
            }}
            · {{ displayTime(detail.loan.issuedAt) }} /
            {{ displayTime(detail.loan.acceptedAt) }} /
            {{ displayTime(detail.loan.returnedAt) }}
          </p>
        </div>
        <h3>{{ t("办理历史", "Event history") }}</h3>
        <div v-for="e in detail.events" :key="e.id" class="event">
          <div>
            <span class="mono">{{ e.action }}</span
            ><small
              >{{ displayTime(e.createdAt) }} · {{ person(e.actorId) }}</small
            >
          </div>
          <p>{{ e.note }}</p>
        </div>
      </div>
    </section>
  </div>
</template>
