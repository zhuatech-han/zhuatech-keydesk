// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 实名钥匙领用资格，限有效期并保留撤销历史。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "key_grant")
public class KeyGrant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "key_id", nullable = false)
  public Long keyId;

  @Column(name = "borrower_id", nullable = false)
  public Long borrowerId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "valid_from", nullable = false)
  public Instant validFrom;

  @Column(name = "valid_until", nullable = false)
  public Instant validUntil;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;

  @Column(name = "reason", nullable = false, length = 500)
  public String reason = "";
}
