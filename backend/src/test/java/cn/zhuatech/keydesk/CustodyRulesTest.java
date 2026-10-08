// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

/** 时间边界、状态门禁及安全导出回归。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class CustodyRulesTest {
  final Instant now = Instant.parse("2026-10-08T08:00:00Z");

  @Test
  void deadlineAtLimitAllowed() {
    assertDoesNotThrow(
        () -> CustodyRules.due(now, now.plusSeconds(3600), now.plusSeconds(3600), 1));
  }

  @Test
  void deadlineMustBeFuture() {
    assertThrows(Problem.class, () -> CustodyRules.due(now, now, now.plusSeconds(3600), 1));
  }

  @Test
  void deadlineCannotExceedGrant() {
    assertThrows(
        Problem.class,
        () -> CustodyRules.due(now, now.plusSeconds(3601), now.plusSeconds(3600), 2));
  }

  @Test
  void deadlineCannotExceedParameter() {
    assertThrows(
        Problem.class,
        () -> CustodyRules.due(now, now.plusSeconds(3601), now.plusSeconds(7200), 1));
  }

  @Test
  void grantHasHalfOpenValidity() {
    var g = new KeyGrant();
    g.validFrom = now;
    g.validUntil = now.plusSeconds(1);
    assertDoesNotThrow(() -> CustodyRules.grant(g, now));
    assertThrows(Problem.class, () -> CustodyRules.grant(g, g.validUntil));
  }

  @Test
  void revokedGrantRejected() {
    var g = new KeyGrant();
    g.validFrom = now;
    g.validUntil = now.plusSeconds(1);
    g.enabled = false;
    assertThrows(Problem.class, () -> CustodyRules.grant(g, now));
  }

  @Test
  void selfInspectionRejected() {
    assertThrows(Problem.class, () -> CustodyRules.independent(1L, 1L));
  }

  @Test
  void staleVersionRejected() {
    assertThrows(Problem.class, () -> CustodyRules.version(2, 1));
  }

  @Test
  void closedCannotBeIssued() {
    var l = new KeyLoan();
    l.state = "RETURNED";
    assertThrows(Problem.class, () -> CustodyRules.state(l, "APPROVED"));
  }

  @Test
  void csvFormulaNeutralized() {
    assertEquals("\"'  =SUM(1,2)\"", Rules.csv("  =SUM(1,2)"));
    assertEquals("\"a\"\"b\"", Rules.csv("a\"b"));
  }

  @Test
  void fractionalIdRejected() {
    assertThrows(Problem.class, () -> Rules.id(1.2));
  }

  @Test
  void bcryptByteLimit() {
    assertThrows(Problem.class, () -> AdminService.validatePassword("Aa9" + "中".repeat(24)));
  }
}
