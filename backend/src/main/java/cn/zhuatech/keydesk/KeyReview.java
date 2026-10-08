// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 损坏钥匙独立放行的申请与核验记录。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "key_review")
public class KeyReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "key_id", nullable = false)
  public Long keyId;

  @Column(name = "requested_by", nullable = false)
  public Long requestedBy;

  @Column(name = "reviewed_by", nullable = true)
  public Long reviewedBy;

  @Column(name = "state", nullable = false, length = 30)
  public String state = "REQUESTED";

  @Column(name = "reason", nullable = false, length = 1000)
  public String reason = "";

  @Column(name = "decision", nullable = false, length = 1000)
  public String decision = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
