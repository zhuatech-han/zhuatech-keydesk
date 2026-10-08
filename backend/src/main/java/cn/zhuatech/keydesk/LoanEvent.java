// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 领还与异常的追加历史，不覆盖原有事件。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "loan_event")
public class LoanEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "loan_id", nullable = false)
  public Long loanId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "action", nullable = false, length = 40)
  public String action = "";

  @Column(name = "note", nullable = false, length = 1000)
  public String note = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
