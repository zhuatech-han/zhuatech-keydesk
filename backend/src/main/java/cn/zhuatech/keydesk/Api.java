// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 业务与后台API，所有权限与状态由事务服务核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class Api {
  final CustodyService service;
  final AdminService admin;

  public Api(CustodyService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 只读取固定白名单目录，不接受客户端实体名称。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{kind}")
  public Object read(@PathVariable String kind) {
    return switch (kind) {
      case "keys" -> service.keys();
      case "grants" -> service.grants();
      case "loans" -> service.loans();
      case "reviews" -> service.reviews();
      case "options" -> service.options();
      case "reports" -> service.report();
      case "audit" -> service.audit();
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 新增主数据、授权、申请或隔离复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{kind}")
  public Object create(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return switch (kind) {
      case "keys" -> service.saveKey(null, b);
      case "grants" -> service.createGrant(b);
      case "loans" -> service.request(b);
      case "reviews" -> service.requestReview(b);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 更新钥匙描述与位置，不能绕过领还流程修改状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/keys/{id}")
  public Object key(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.saveKey(id, b);
  }

  /** 退役与撤销保留历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/keys/{id}/retire")
  public Object retire(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.retire(id, b);
  }

  /** 撤销授权不会标记钥匙已收回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/grants/{id}/revoke")
  public Object revoke(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.revoke(id, b);
  }

  /** 单次状态操作以版本拒绝重复提交。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/loans/{id}/{action}")
  public Object action(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return service.action(id, action.toUpperCase(Locale.ROOT), b);
  }

  /** 历史细节只向有范围权限的账号开放。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/loans/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 隔离放行须独立复核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/reviews/{id}/decision")
  public Object review(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.review(id, b);
  }

  /** 下载当前范围报表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports.csv")
  public ResponseEntity<String> csv() {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=keydesk-custody.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(service.csv());
  }

  /** 后台目录范围和权限由AdminService验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  public Object directory(@PathVariable String kind) {
    return kind.equals("options") ? admin.options() : admin.read(kind);
  }

  /** 新增目录仅限完整范围管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object add(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 版本及最后管理员保护，不能绕过业务状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object update(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }
}
