// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 从申请到实物归还的双人确认状态及期限快照。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "key_loan")
public class KeyLoan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "key_id", nullable = false)
  public Long keyId;

  @Column(name = "grant_id", nullable = false)
  public Long grantId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "borrower_id", nullable = false)
  public Long borrowerId;

  @Column(name = "approver_id", nullable = true)
  public Long approverId;

  @Column(name = "issuer_id", nullable = true)
  public Long issuerId;

  @Column(name = "receiver_id", nullable = true)
  public Long receiverId;

  @Column(name = "loss_reporter_id", nullable = true)
  public Long lossReporterId;

  @Column(name = "state", nullable = false, length = 30)
  public String state = "REQUESTED";

  @Column(name = "purpose", nullable = false, length = 500)
  public String purpose = "";

  @Column(name = "due_at", nullable = false)
  public Instant dueAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "approved_until", nullable = true)
  public Instant approvedUntil;

  @Column(name = "issued_at", nullable = true)
  public Instant issuedAt;

  @Column(name = "accepted_at", nullable = true)
  public Instant acceptedAt;

  @Column(name = "returned_at", nullable = true)
  public Instant returnedAt;

  @Column(name = "inspection", nullable = false, length = 30)
  public String inspection = "";

  @Column(name = "version", nullable = false)
  public long version = 1;
}
