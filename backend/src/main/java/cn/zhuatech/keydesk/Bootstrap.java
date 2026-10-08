// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化权限和管理员，不生成虚构钥匙或领用记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  static final List<String> CODES =
      List.of(
          "keys",
          "manage",
          "request",
          "approve",
          "custody",
          "reports",
          "users",
          "roles",
          "settings",
          "audit");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  /** 读取外部初始账号配置，不记录凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${keydesk.admin-username}") String username,
      @Value("${keydesk.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 仅首次初始化系统目录，重启保持全部账号及授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalStateException("Invalid administrator name");
    for (String c : CODES) {
      var p = new Permission();
      p.code = c;
      p.name = c;
      db.save(p);
    }
    var d = new Department();
    d.name = "主部门 / Main department";
    d.zone = "Asia/Shanghai";
    db.save(d);
    var admin = role("管理员 / Administrator", "ALL", CODES);
    role("审批员 / Approver", "DEPARTMENT", List.of("keys", "approve", "reports", "audit"));
    role("保管员 / Custodian", "DEPARTMENT", List.of("keys", "manage", "custody", "reports"));
    role("领用人 / Borrower", "ASSIGNED", List.of("keys", "request"));
    role("审计员 / Auditor", "DEPARTMENT", List.of("keys", "reports", "audit"));
    var a = new Account();
    a.username = username.toLowerCase(Locale.ROOT);
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = admin.id;
    a.departmentId = d.id;
    db.save(a);
    String[][] menus = {
      {"keys", "钥匙台账", "Key register", "keys"},
      {"loans", "领还办理", "Custody desk", "keys"},
      {"grants", "领用授权", "Grants", "keys"},
      {"reviews", "隔离处置", "Quarantine review", "keys"},
      {"reports", "状态报表", "Reports", "reports"},
      {"users", "登录账号", "Accounts", "users"},
      {"roles", "角色与权限", "Roles & permissions", "roles"},
      {"settings", "部门与设置", "Departments & settings", "settings"},
      {"audit", "审计记录", "Audit trail", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    String[][] cats = {
      {"FACILITY", "设施", "Facility"}, {"OFFICE", "办公室", "Office"}, {"VEHICLE", "车辆", "Vehicle"}
    };
    for (var c : cats) {
      var x = new DictionaryEntry();
      x.type = "PLATFORM";
      x.code = c[0];
      x.name = c[1];
      x.nameEn = c[2];
      db.save(x);
    }
    setting("max_loan_hours", "72");
    setting("approval_hours", "8");
  }

  private AccessRole role(String name, String scope, List<String> codes) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions.addAll(codes);
    return db.save(r);
  }

  private void setting(String code, String value) {
    var s = new SystemSetting();
    s.code = code;
    s.value = value;
    db.save(s);
  }
}
