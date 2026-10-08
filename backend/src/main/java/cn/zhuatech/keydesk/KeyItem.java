// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import jakarta.persistence.*;

/** 单个实体钥匙的不可变编号、保管位置及领还占用。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "physical_key")
public class KeyItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code = "";

  @Column(name = "name", nullable = false, length = 160)
  public String name = "";

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "category", nullable = false, length = 60)
  public String category = "";

  @Column(name = "cabinet", nullable = false, length = 120)
  public String cabinet = "";

  @Column(name = "state", nullable = false, length = 30)
  public String state = "IN";

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "current_loan_id", nullable = true)
  public Long currentLoanId;

  @Column(name = "version", nullable = false)
  public long version = 1;

  @Column(name = "note", nullable = false, length = 500)
  public String note = "";
}
