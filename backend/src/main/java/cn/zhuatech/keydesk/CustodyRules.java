// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import java.time.*;
import java.util.*;

/** 独立审批、授权期限和状态门禁；对实物交接不作自动推断。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class CustodyRules {
  static final Set<String> OPEN =
      Set.of(
          "REQUESTED",
          "APPROVED",
          "HANDOVER_PENDING",
          "ISSUED",
          "RETURN_REQUESTED",
          "RETURN_REVIEW",
          "LOST");

  /** 校验未来期限在实名授权与系统小时上限内。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void due(Instant now, Instant due, Instant grantEnd, int maxHours) {
    Rules.check(
        due.isAfter(now)
            && !due.isAfter(grantEnd)
            && !due.isAfter(now.plusSeconds(maxHours * 3600L)),
        "DUE_INVALID");
  }

  /** 校验当前有效实名授权；撤销不阻止归还及丢失上报。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void grant(KeyGrant g, Instant now) {
    Rules.check(
        g.enabled && !now.isBefore(g.validFrom) && now.isBefore(g.validUntil), "GRANT_INACTIVE");
  }

  /** 所有状态动作必须从明确许可的状态发起。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void state(KeyLoan l, String... allowed) {
    Rules.check(Arrays.asList(allowed).contains(l.state), "STATE_INVALID");
  }

  /** 版本冲突拒绝整个事务，客户端不得自动重试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void version(long current, Object expected) {
    Rules.check(current == Rules.id(expected), "VERSION_CONFLICT");
  }

  /** 审批与实物核验不允许由领用人自己办理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void independent(Long actor, Long borrower) {
    Rules.check(!Objects.equals(actor, borrower), "INDEPENDENT_REQUIRED");
  }

  private CustodyRules() {}
}
