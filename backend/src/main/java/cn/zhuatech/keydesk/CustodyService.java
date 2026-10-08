// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 实名钥匙授权、独立审批及双人实物交接；写入在实例目录锁下串行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class CustodyService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;

  /** 连接身份、事务和可测试时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public CustodyService(Store db, AccessService access, AdminService admin, Clock clock) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
  }

  /** 与数据库timestamp(6)一致，期限比较使用微秒精度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  Instant now() {
    return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  void begin(String code) {
    admin.lock();
    access.require(code);
  }

  boolean dept(Long id) {
    return access.department(id);
  }

  boolean selfOnly() {
    return access.role().scope.equals("ASSIGNED");
  }

  boolean loanVisible(KeyLoan l) {
    return dept(l.departmentId) && (!selfOnly() || l.borrowerId.equals(access.current().id));
  }

  void scoped(Long dept) {
    if (!dept(dept)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  KeyItem key(Long id) {
    var k = db.get(KeyItem.class, id);
    scoped(k.departmentId);
    return k;
  }

  KeyLoan loan(Long id) {
    var l = db.get(KeyLoan.class, id);
    if (!loanVisible(l)) throw new Problem(403, "OUT_OF_SCOPE");
    return l;
  }

  Instant instant(Object v) {
    try {
      return Instant.parse(Rules.text(v, 40, true))
          .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    } catch (Exception e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }

  String note(Map<String, Object> b) {
    return Rules.text(b.get("note"), 1000, true);
  }

  void event(KeyLoan l, String action, String note) {
    var e = new LoanEvent();
    e.loanId = l.id;
    e.actorId = access.current().id;
    e.action = action;
    e.note = note;
    e.createdAt = now();
    db.save(e);
    access.audit("LOAN_" + action, l.id, l.departmentId);
  }

  boolean eligible(Account a, Long department) {
    return a.enabled
        && db.get(Department.class, a.departmentId).enabled
        && (db.get(AccessRole.class, a.roleId).scope.equals("ALL")
            || a.departmentId.equals(department))
        && db.get(AccessRole.class, a.roleId).permissions.contains("request");
  }

  void validGrant(KeyLoan l) {
    var g = db.get(KeyGrant.class, l.grantId);
    CustodyRules.grant(g, now());
    Rules.check(eligible(db.get(Account.class, l.borrowerId), l.departmentId), "BORROWER_DISABLED");
  }

  /** 台账只展示所属部门；领用人仅看有当前授权或本人历史的钥匙。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<KeyItem> keys() {
    access.require("keys");
    var a = access.current();
    Set<Long> own = new HashSet<>();
    if (selfOnly()) {
      for (var g : db.all(KeyGrant.class))
        if (g.borrowerId.equals(a.id)
            && g.enabled
            && !now().isBefore(g.validFrom)
            && now().isBefore(g.validUntil)) own.add(g.keyId);
      for (var l : db.all(KeyLoan.class)) if (l.borrowerId.equals(a.id)) own.add(l.keyId);
    }
    return db.all(KeyItem.class).stream()
        .filter(k -> dept(k.departmentId) && (!selfOnly() || own.contains(k.id)))
        .toList();
  }

  /** 保存主数据；编号、所属部门固定，有借出则禁止修改保管位置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public KeyItem saveKey(Long id, Map<String, Object> b) {
    begin("manage");
    Rules.check(!selfOnly(), "OUT_OF_SCOPE");
    var k = id == null ? new KeyItem() : key(id);
    if (id == null) {
      k.code = Rules.text(b.get("code"), 60, true).toUpperCase(Locale.ROOT);
      Rules.check(k.code.matches("[A-Z0-9_-]{2,60}"), "INVALID_INPUT");
      k.departmentId = Rules.id(b.get("departmentId"));
      scoped(k.departmentId);
      Rules.check(db.get(Department.class, k.departmentId).enabled, "DEPARTMENT_DISABLED");
    } else {
      CustodyRules.version(k.version, b.get("version"));
      Rules.check(
          k.code.equals(b.get("code")) && k.departmentId.equals(Rules.id(b.get("departmentId"))),
          "IDENTITY_LOCKED");
      Rules.check(k.currentLoanId == null, "KEY_OCCUPIED");
      k.version++;
    }
    k.name = Rules.text(b.get("name"), 160, true);
    k.cabinet = Rules.text(b.get("cabinet"), 120, true);
    k.note = Rules.text(b.get("note"), 500, false);
    k.category = Rules.text(b.get("category"), 60, true);
    Rules.check(
        db.query(
                    DictionaryEntry.class,
                    "from DictionaryEntry where type='PLATFORM' and code=?1 and enabled=true",
                    k.category)
                .size()
            == 1,
        "CATEGORY_INVALID");
    if (id == null) db.save(k);
    access.audit("KEY_SAVE", k.id, k.departmentId);
    return k;
  }

  /** 退役保留历史，存在未结案领用记录时拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public KeyItem retire(Long id, Map<String, Object> b) {
    begin("manage");
    var k = key(id);
    Rules.check(!selfOnly(), "OUT_OF_SCOPE");
    CustodyRules.version(k.version, b.get("version"));
    Rules.check(
        !k.state.equals("RETIRED")
            && k.currentLoanId == null
            && db.query(KeyReview.class, "from KeyReview where keyId=?1 and state='REQUESTED'", id)
                .isEmpty()
            && db.query(KeyLoan.class, "from KeyLoan where keyId=?1", id).stream()
                .noneMatch(l -> CustodyRules.OPEN.contains(l.state)),
        "KEY_OCCUPIED");
    k.state = "RETIRED";
    k.version++;
    access.audit("KEY_RETIRE", k.id, k.departmentId);
    return k;
  }

  /** 授权目录，普通领用人只见自己的授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<KeyGrant> grants() {
    access.require("keys");
    return db.all(KeyGrant.class).stream()
        .filter(
            g -> dept(g.departmentId) && (!selfOnly() || g.borrowerId.equals(access.current().id)))
        .toList();
  }

  /** 授权由审批员签发，禁止自己给自己授权；不修改已有授权期限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public KeyGrant createGrant(Map<String, Object> b) {
    begin("approve");
    Rules.check(!selfOnly(), "OUT_OF_SCOPE");
    var k = key(Rules.id(b.get("keyId")));
    Rules.check(!k.state.equals("RETIRED"), "KEY_RETIRED");
    var borrower = db.get(Account.class, Rules.id(b.get("borrowerId")));
    Rules.check(eligible(borrower, k.departmentId), "BORROWER_DISABLED");
    CustodyRules.independent(access.current().id, borrower.id);
    var g = new KeyGrant();
    g.keyId = k.id;
    g.departmentId = k.departmentId;
    g.borrowerId = borrower.id;
    g.createdBy = access.current().id;
    g.validFrom = instant(b.get("validFrom"));
    g.validUntil = instant(b.get("validUntil"));
    Rules.check(
        g.validUntil.isAfter(now())
            && g.validUntil.isAfter(g.validFrom)
            && !g.validUntil.isAfter(g.validFrom.plusSeconds(366L * 86400)),
        "GRANT_DATES_INVALID");
    Rules.check(
        db
            .query(
                KeyGrant.class,
                "from KeyGrant where keyId=?1 and borrowerId=?2 and enabled=true",
                k.id,
                borrower.id)
            .stream()
            .noneMatch(
                x -> x.validFrom.isBefore(g.validUntil) && g.validFrom.isBefore(x.validUntil)),
        "GRANT_OVERLAP");
    g.reason = Rules.text(b.get("reason"), 500, true);
    db.save(g);
    access.audit("GRANT_CREATE", g.id, g.departmentId);
    return g;
  }

  /** 撤销影响后续审批和交出；已借出仍可归还，不伪造收回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public KeyGrant revoke(Long id, Map<String, Object> b) {
    begin("approve");
    Rules.check(!selfOnly(), "OUT_OF_SCOPE");
    var g = db.get(KeyGrant.class, id);
    scoped(g.departmentId);
    CustodyRules.version(g.version, b.get("version"));
    Rules.check(g.enabled, "STATE_INVALID");
    g.enabled = false;
    g.version++;
    access.audit("GRANT_REVOKE", g.id, g.departmentId);
    return g;
  }

  /** 领还目录采用岗位及部门范围，普通领用人只见本人记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<KeyLoan> loans() {
    access.require("keys");
    return db.all(KeyLoan.class).stream().filter(this::loanVisible).toList();
  }

  /** 本人申请须当前授权和有效期限；重复未结案申请拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public KeyLoan request(Map<String, Object> b) {
    begin("request");
    var g = db.get(KeyGrant.class, Rules.id(b.get("grantId")));
    var a = access.current();
    Rules.check(g.borrowerId.equals(a.id), "BORROWER_ONLY");
    scoped(g.departmentId);
    CustodyRules.grant(g, now());
    var k = key(g.keyId);
    Rules.check(k.state.equals("IN"), "KEY_UNAVAILABLE");
    Rules.check(
        db
            .query(KeyLoan.class, "from KeyLoan where keyId=?1 and borrowerId=?2", k.id, a.id)
            .stream()
            .noneMatch(l -> CustodyRules.OPEN.contains(l.state)),
        "OPEN_REQUEST_EXISTS");
    var l = new KeyLoan();
    l.keyId = k.id;
    l.grantId = g.id;
    l.departmentId = k.departmentId;
    l.borrowerId = a.id;
    l.createdAt = now();
    l.dueAt = instant(b.get("dueAt"));
    CustodyRules.due(now(), l.dueAt, g.validUntil, admin.setting("max_loan_hours"));
    l.purpose = Rules.text(b.get("purpose"), 500, true);
    db.save(l);
    event(l, "REQUEST", l.purpose);
    return l;
  }

  /** 每个状态动作都核对角色、本人限制、版本及实物占用，不自动重试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public KeyLoan action(Long id, String action, Map<String, Object> b) {
    begin("keys");
    var l = loan(id);
    var k = key(l.keyId);
    var a = access.current();
    CustodyRules.version(l.version, b.get("version"));
    String n = note(b);
    switch (action) {
      case "APPROVE", "REJECT" -> {
        access.require("approve");
        Rules.check(!selfOnly(), "OUT_OF_SCOPE");
        CustodyRules.independent(a.id, l.borrowerId);
        CustodyRules.state(l, "REQUESTED");
        l.approverId = a.id;
        if (action.equals("APPROVE")) {
          validGrant(l);
          Rules.check(l.dueAt.isAfter(now()) && k.state.equals("IN"), "KEY_UNAVAILABLE");
          l.state = "APPROVED";
          l.approvedUntil = now().plusSeconds(admin.setting("approval_hours") * 3600L);
        } else l.state = "REJECTED";
      }
      case "CANCEL" -> {
        Rules.check(
            a.id.equals(l.borrowerId) || access.has("approve") && !selfOnly(), "BORROWER_ONLY");
        CustodyRules.state(l, "REQUESTED", "APPROVED");
        l.state = "CANCELLED";
      }
      case "ISSUE" -> {
        access.require("custody");
        Rules.check(!selfOnly(), "OUT_OF_SCOPE");
        CustodyRules.independent(a.id, l.borrowerId);
        CustodyRules.state(l, "APPROVED");
        validGrant(l);
        Rules.check(now().isBefore(l.approvedUntil) && l.dueAt.isAfter(now()), "APPROVAL_EXPIRED");
        Rules.check(k.state.equals("IN") && k.currentLoanId == null, "KEY_UNAVAILABLE");
        k.state = "OUT";
        k.currentLoanId = l.id;
        k.version++;
        l.state = "HANDOVER_PENDING";
        l.issuerId = a.id;
        l.issuedAt = now();
      }
      case "ACCEPT" -> {
        access.require("request");
        Rules.check(a.id.equals(l.borrowerId), "BORROWER_ONLY");
        CustodyRules.state(l, "HANDOVER_PENDING");
        l.state = "ISSUED";
        l.acceptedAt = now();
      }
      case "RETURN" -> {
        access.require("request");
        Rules.check(a.id.equals(l.borrowerId), "BORROWER_ONLY");
        CustodyRules.state(l, "HANDOVER_PENDING", "ISSUED");
        l.state = "RETURN_REQUESTED";
      }
      case "RECEIVE" -> {
        access.require("custody");
        Rules.check(!selfOnly(), "OUT_OF_SCOPE");
        CustodyRules.independent(a.id, l.borrowerId);
        CustodyRules.state(l, "RETURN_REQUESTED");
        Rules.check(Objects.equals(k.currentLoanId, l.id), "KEY_OCCUPIED");
        String inspection = Rules.text(b.get("inspection"), 30, true);
        Rules.check(Set.of("GOOD", "DAMAGED").contains(inspection), "INVALID_INPUT");
        l.inspection = inspection;
        l.receiverId = a.id;
        l.returnedAt = now();
        l.state = "RETURNED";
        k.state = inspection.equals("GOOD") ? "IN" : "QUARANTINED";
        k.currentLoanId = null;
        k.version++;
      }
      case "RECOVER" -> {
        access.require("custody");
        Rules.check(!selfOnly(), "OUT_OF_SCOPE");
        CustodyRules.independent(a.id, l.borrowerId);
        CustodyRules.state(l, "HANDOVER_PENDING", "ISSUED");
        String inspection = Rules.text(b.get("inspection"), 30, true);
        Rules.check(Set.of("GOOD", "DAMAGED").contains(inspection), "INVALID_INPUT");
        l.inspection = inspection;
        l.receiverId = a.id;
        l.returnedAt = now();
        l.state = "RETURN_REVIEW";
        k.state = "QUARANTINED";
        k.version++;
      }
      case "CONFIRM_RECOVERY" -> {
        access.require("approve");
        Rules.check(!selfOnly(), "OUT_OF_SCOPE");
        CustodyRules.independent(a.id, l.borrowerId);
        CustodyRules.independent(a.id, l.receiverId);
        CustodyRules.state(l, "RETURN_REVIEW");
        l.state = "RETURNED";
        k.state = l.inspection.equals("GOOD") ? "IN" : "QUARANTINED";
        k.currentLoanId = null;
        k.version++;
      }
      case "LOSS" -> {
        Rules.check(
            a.id.equals(l.borrowerId) && access.has("request")
                || access.has("custody") && !selfOnly(),
            "FORBIDDEN");
        CustodyRules.state(l, "HANDOVER_PENDING", "ISSUED", "RETURN_REQUESTED");
        l.state = "LOST";
        l.lossReporterId = a.id;
        k.state = "QUARANTINED";
        k.version++;
      }
      case "RESOLVE" -> {
        access.require("approve");
        Rules.check(!selfOnly(), "OUT_OF_SCOPE");
        CustodyRules.independent(a.id, l.borrowerId);
        CustodyRules.independent(a.id, l.lossReporterId);
        CustodyRules.state(l, "LOST");
        Rules.check(n.length() >= 8, "NOTE_REQUIRED");
        l.state = "RESOLVED";
        k.currentLoanId = null;
        k.state = "RETIRED";
        k.version++;
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    l.version++;
    event(l, action, n);
    return l;
  }

  /** 受范围保护的完整事件历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    access.require("keys");
    var l = loan(id);
    return Map.of(
        "loan",
        l,
        "events",
        db.query(LoanEvent.class, "from LoanEvent where loanId=?1 order by id", id));
  }

  /** 隔离解除须保管员记录检查并由另一账号批准，不自动重新使用遗失钥匙。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public KeyReview requestReview(Map<String, Object> b) {
    begin("custody");
    Rules.check(!selfOnly(), "OUT_OF_SCOPE");
    var k = key(Rules.id(b.get("keyId")));
    Rules.check(k.state.equals("QUARANTINED") && k.currentLoanId == null, "KEY_OCCUPIED");
    Rules.check(
        db.query(KeyReview.class, "from KeyReview where keyId=?1 and state='REQUESTED'", k.id)
            .isEmpty(),
        "OPEN_REQUEST_EXISTS");
    var r = new KeyReview();
    r.keyId = k.id;
    r.requestedBy = access.current().id;
    r.createdAt = now();
    r.reason = Rules.text(b.get("reason"), 1000, true);
    db.save(r);
    access.audit("REVIEW_REQUEST", r.id, k.departmentId);
    return r;
  }

  /** 拒绝保留隔离，通过恢复在柜；申请人不得复核自己。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public KeyReview review(Long id, Map<String, Object> b) {
    begin("approve");
    Rules.check(!selfOnly(), "OUT_OF_SCOPE");
    var r = db.get(KeyReview.class, id);
    var k = key(r.keyId);
    CustodyRules.version(r.version, b.get("version"));
    CustodyRules.independent(access.current().id, r.requestedBy);
    Rules.check(
        r.state.equals("REQUESTED") && k.state.equals("QUARANTINED") && k.currentLoanId == null,
        "STATE_INVALID");
    r.decision = Rules.text(b.get("decision"), 1000, true);
    r.reviewedBy = access.current().id;
    r.state = Rules.flag(b.get("approved")) ? "APPROVED" : "REJECTED";
    r.version++;
    if (r.state.equals("APPROVED")) {
      k.state = "IN";
      k.version++;
    }
    access.audit("REVIEW_" + r.state, r.id, k.departmentId);
    return r;
  }

  /** 隔离记录只对具有台账权限的部门岗位开放，领用人无权查看其他人检查记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<KeyReview> reviews() {
    access.require("keys");
    if (selfOnly()) return List.of();
    return db.all(KeyReview.class).stream()
        .filter(r -> dept(db.get(KeyItem.class, r.keyId).departmentId))
        .toList();
  }

  /** 提供台账表单的最小目录，不返回登录名称、密码或角色管理资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> options() {
    access.require("keys");
    var accounts =
        db.all(Account.class).stream()
            .filter(a -> !selfOnly() && a.enabled && dept(a.departmentId))
            .map(a -> Map.of("id", a.id, "name", a.displayName, "departmentId", a.departmentId))
            .toList();
    return Map.of(
        "departments",
        db.all(Department.class).stream().filter(d -> d.enabled && dept(d.id)).toList(),
        "accounts",
        accounts,
        "categories",
        db.all(DictionaryEntry.class).stream().filter(d -> d.enabled).toList());
  }

  /** 实时指标使用同一岗位范围，逾期只统计已交出且尚未收回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> report() {
    access.require("reports");
    var ks = keys();
    var ls = loans();
    var states = new TreeMap<String, Long>();
    for (var l : ls) states.merge(l.state, 1L, Long::sum);
    return Map.of(
        "keys",
        ks.size(),
        "inCabinet",
        ks.stream().filter(k -> k.state.equals("IN")).count(),
        "quarantined",
        ks.stream().filter(k -> k.state.equals("QUARANTINED")).count(),
        "overdue",
        ls.stream()
            .filter(
                l ->
                    Set.of("HANDOVER_PENDING", "ISSUED", "RETURN_REQUESTED").contains(l.state)
                        && l.dueAt.isBefore(now()))
            .count(),
        "states",
        states,
        "generatedAt",
        now());
  }

  /** 范围内领还CSV报表，阻止公式注入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String csv() {
    access.require("reports");
    var s = new StringBuilder("id,key,borrower,state,due_at,purpose\r\n");
    for (var l : loans()) {
      s.append(l.id)
          .append(',')
          .append(Rules.csv(db.get(KeyItem.class, l.keyId).code))
          .append(',')
          .append(Rules.csv(db.get(Account.class, l.borrowerId).displayName))
          .append(',')
          .append(Rules.csv(l.state))
          .append(',')
          .append(Rules.csv(l.dueAt))
          .append(',')
          .append(Rules.csv(l.purpose))
          .append("\r\n");
    }
    return s.toString();
  }

  /** 操作审计按部门过滤，个人范围仅自己的动作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<AuditEvent> audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(
            e -> dept(e.departmentId) && (!selfOnly() || e.actor.equals(access.current().username)))
        .toList();
  }
}
