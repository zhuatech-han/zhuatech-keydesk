// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.keydesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 真实Spring Security/HTTP/Flyway/JPA集成，不模拟业务服务；所有夹具明确TEST。官网 https://www.zhuatech.cn/；微信 zhuatech /
 * zhuatech2。
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(CustodyIntegrationTest.TimeConfig.class)
class CustodyIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();
  static final Instant BASE = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  static final AtomicReference<Instant> TIME = new AtomicReference<>(BASE);

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock testClock() {
      return new Clock() {
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
          return this;
        }

        public Instant instant() {
          return TIME.get();
        }
      };
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:keydesk;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("keydesk.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, borrower, approver, custodian, other;
  long borrowerId, keyId, grantId, loanId, custodianId;
  String suffix;
  JsonNode l;

  Map<String, Object> m(Object... p) {
    var b = new LinkedHashMap<String, Object>();
    for (int i = 0; i < p.length; i += 2) b.put(p[i].toString(), p[i + 1]);
    return b;
  }

  MockHttpServletRequestBuilder req(String p, String method, Map<String, Object> b)
      throws Exception {
    var r =
        switch (method) {
          case "POST" -> post("/api" + p);
          case "PUT" -> put("/api" + p);
          default -> get("/api" + p);
        };
    if (b != null)
      r.with(csrf()).contentType("application/json").content(json.writeValueAsString(b));
    return r;
  }

  MvcResult result(MockHttpSession s, String p, String method, Map<String, Object> b)
      throws Exception {
    return mvc.perform(req(p, method, b).session(s)).andReturn();
  }

  JsonNode call(MockHttpSession s, String p, String method, Map<String, Object> b)
      throws Exception {
    var r = result(s, p, method, b);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(
      MockHttpSession s, String p, String method, Map<String, Object> b, int status, String code)
      throws Exception {
    var r = result(s, p, method, b);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).get("code").asString());
  }

  MockHttpSession login(String u) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(m("username", u, "password", PASSWORD))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  long user(String u, int role, long dept) throws Exception {
    return call(
            admin,
            "/admin/users",
            "POST",
            m(
                "username",
                u,
                "displayName",
                "TEST " + u,
                "password",
                PASSWORD,
                "roleId",
                role,
                "departmentId",
                dept,
                "enabled",
                true))
        .get("id")
        .asLong();
  }

  JsonNode grant(long key, long who) throws Exception {
    return call(
        approver,
        "/grants",
        "POST",
        m(
            "keyId",
            key,
            "borrowerId",
            who,
            "validFrom",
            BASE.minusSeconds(60).toString(),
            "validUntil",
            BASE.plusSeconds(86400).toString(),
            "reason",
            "TEST access task"));
  }

  JsonNode request(MockHttpSession s, long g) throws Exception {
    return call(
        s,
        "/loans",
        "POST",
        m("grantId", g, "dueAt", BASE.plusSeconds(43200).toString(), "purpose", "TEST inspection"));
  }

  JsonNode act(MockHttpSession s, String action) throws Exception {
    return act(
        s,
        action,
        m(
            "version",
            l.get("version").asLong(),
            "note",
            "TEST checked physical tag",
            "inspection",
            "GOOD"));
  }

  JsonNode act(MockHttpSession s, String action, Map<String, Object> b) throws Exception {
    l = call(s, "/loans/" + loanId + "/" + action, "POST", b);
    return l;
  }

  @BeforeEach
  void setup() throws Exception {
    TIME.set(BASE);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    borrowerId = user("bo" + suffix, 4, 1);
    user("ap" + suffix, 2, 1);
    custodianId = user("cu" + suffix, 3, 1);
    user("ot" + suffix, 4, 1);
    borrower = login("bo" + suffix);
    approver = login("ap" + suffix);
    custodian = login("cu" + suffix);
    other = login("ot" + suffix);
    keyId =
        call(
                custodian,
                "/keys",
                "POST",
                m(
                    "code",
                    "TEST_" + suffix.toUpperCase(),
                    "name",
                    "TEST Facility key",
                    "departmentId",
                    1,
                    "category",
                    "FACILITY",
                    "cabinet",
                    "TEST Cabinet 01",
                    "note",
                    ""))
            .get("id")
            .asLong();
    grantId = grant(keyId, borrowerId).get("id").asLong();
    l = request(borrower, grantId);
    loanId = l.get("id").asLong();
  }

  @Test
  void fullTwoPartyReturnHistory() throws Exception {
    act(approver, "approve");
    act(custodian, "issue");
    assertEquals("OUT", keyState());
    act(borrower, "accept");
    act(borrower, "return");
    assertEquals("OUT", keyState());
    act(custodian, "receive");
    assertEquals("RETURNED", l.get("state").asString());
    assertEquals("IN", keyState());
    assertEquals(6, call(borrower, "/loans/" + loanId, "GET", null).get("events").size());
  }

  String keyState() throws Exception {
    for (var k : call(admin, "/keys", "GET", null))
      if (k.get("id").asLong() == keyId) return k.get("state").asString();
    throw new AssertionError();
  }

  @Test
  void cannotReceiveBeforeBorrowerReturn() throws Exception {
    act(approver, "approve");
    act(custodian, "issue");
    fail(
        custodian,
        "/loans/" + loanId + "/receive",
        "POST",
        m("version", l.get("version").asLong(), "note", "TEST", "inspection", "GOOD"),
        409,
        "STATE_INVALID");
    assertEquals(
        "HANDOVER_PENDING",
        call(admin, "/loans/" + loanId, "GET", null).get("loan").get("state").asString());
  }

  @Test
  void otherBorrowerCannotReadOrMutate() throws Exception {
    fail(other, "/loans/" + loanId, "GET", null, 403, "OUT_OF_SCOPE");
    fail(
        other,
        "/loans/" + loanId + "/accept",
        "POST",
        m("version", 1, "note", "TEST"),
        403,
        "OUT_OF_SCOPE");
    assertEquals(0, call(other, "/loans", "GET", null).size());
  }

  @Test
  void borrowerCannotApproveEvenIfRoleModified() throws Exception {
    var a = call(admin, "/admin/users", "GET", null);
    JsonNode row = null;
    for (var x : a) if (x.get("id").asLong() == borrowerId) row = x;
    var b = json.convertValue(row, Map.class);
    b.put("roleId", 1);
    b.put("password", "");
    call(admin, "/admin/users/" + borrowerId, "PUT", b);
    fail(
        borrower,
        "/loans/" + loanId + "/approve",
        "POST",
        m("version", 1, "note", "TEST"),
        409,
        "INDEPENDENT_REQUIRED");
    b.put("roleId", 4);
    b.put("version", row.get("version").asLong() + 1);
    call(admin, "/admin/users/" + borrowerId, "PUT", b);
  }

  @Test
  void revokedGrantBlocksIssueButNotReturn() throws Exception {
    act(approver, "approve");
    call(approver, "/grants/" + grantId + "/revoke", "POST", m("version", 1));
    fail(
        custodian,
        "/loans/" + loanId + "/issue",
        "POST",
        m("version", l.get("version").asLong(), "note", "TEST"),
        409,
        "GRANT_INACTIVE");
    act(borrower, "cancel");
  }

  @Test
  void revocationWhileOutDoesNotInventReturn() throws Exception {
    act(approver, "approve");
    act(custodian, "issue");
    call(approver, "/grants/" + grantId + "/revoke", "POST", m("version", 1));
    assertEquals("OUT", keyState());
    act(borrower, "accept");
    act(borrower, "return");
    act(custodian, "receive");
    assertEquals("IN", keyState());
  }

  @Test
  void approvalExpiresExactlyAtBoundary() throws Exception {
    act(approver, "approve");
    TIME.set(BASE.plusSeconds(8 * 3600));
    fail(
        custodian,
        "/loans/" + loanId + "/issue",
        "POST",
        m("version", l.get("version").asLong(), "note", "TEST"),
        409,
        "APPROVAL_EXPIRED");
    assertEquals("IN", keyState());
  }

  @Test
  void staleRepeatDoesNotAppendEvent() throws Exception {
    act(approver, "approve");
    act(custodian, "issue");
    fail(
        custodian,
        "/loans/" + loanId + "/issue",
        "POST",
        m("version", 2, "note", "TEST"),
        409,
        "VERSION_CONFLICT");
    assertEquals(3, call(admin, "/loans/" + loanId, "GET", null).get("events").size());
  }

  @Test
  void twoApprovedRequestsCannotBothTakeOneKey() throws Exception {
    long second = user("b2" + suffix, 4, 1);
    long secondGrant = grant(keyId, second).get("id").asLong();
    var sess = login("b2" + suffix);
    var secondLoan = request(sess, secondGrant);
    call(
        approver,
        "/loans/" + secondLoan.get("id").asLong() + "/approve",
        "POST",
        m("version", 1, "note", "TEST"));
    act(approver, "approve");
    act(custodian, "issue");
    fail(
        custodian,
        "/loans/" + secondLoan.get("id").asLong() + "/issue",
        "POST",
        m("version", 2, "note", "TEST"),
        409,
        "KEY_UNAVAILABLE");
  }

  @Test
  void damagedReturnNeedsIndependentRelease() throws Exception {
    act(approver, "approve");
    act(custodian, "issue");
    act(borrower, "return");
    act(
        custodian,
        "receive",
        m(
            "version",
            l.get("version").asLong(),
            "note",
            "TEST damaged tag",
            "inspection",
            "DAMAGED"));
    assertEquals("QUARANTINED", keyState());
    var r =
        call(
            custodian,
            "/reviews",
            "POST",
            m("keyId", keyId, "reason", "TEST repaired and inspected"));
    fail(
        custodian,
        "/reviews/" + r.get("id").asLong() + "/decision",
        "POST",
        m("version", 1, "approved", true, "decision", "TEST"),
        403,
        "FORBIDDEN");
    call(
        approver,
        "/reviews/" + r.get("id").asLong() + "/decision",
        "POST",
        m("version", 1, "approved", true, "decision", "TEST independent tag and function check"));
    assertEquals("IN", keyState());
  }

  @Test
  void lossRetiresAfterIndependentResolution() throws Exception {
    act(approver, "approve");
    act(custodian, "issue");
    act(borrower, "loss");
    assertEquals("QUARANTINED", keyState());
    act(
        approver,
        "resolve",
        m(
            "version",
            l.get("version").asLong(),
            "note",
            "TEST external lock replacement receipt 001"));
    assertEquals("RETIRED", keyState());
    assertEquals("RESOLVED", l.get("state").asString());
  }

  @Test
  void disabledBorrowerRecoveredWithIndependentReview() throws Exception {
    act(approver, "approve");
    act(custodian, "issue");
    JsonNode row = null;
    for (var x : call(admin, "/admin/users", "GET", null))
      if (x.get("id").asLong() == borrowerId) row = x;
    var b = json.convertValue(row, Map.class);
    b.put("enabled", false);
    b.put("password", "");
    call(admin, "/admin/users/" + borrowerId, "PUT", b);
    fail(borrower, "/loans", "GET", null, 401, "UNAUTHENTICATED");
    act(custodian, "recover");
    assertEquals("QUARANTINED", keyState());
    act(approver, "confirm_recovery");
    assertEquals("IN", keyState());
  }

  @Test
  void dataScopeRejectsOtherDepartment() throws Exception {
    var d =
        call(
            admin,
            "/admin/departments",
            "POST",
            m("name", "TEST other dept " + suffix, "zone", "UTC", "enabled", true));
    long id = user("xd" + suffix, 3, d.get("id").asLong());
    assertTrue(id > 0);
    var s = login("xd" + suffix);
    fail(
        s,
        "/keys/" + keyId,
        "PUT",
        m("version", 1, "code", "X", "departmentId", 1),
        403,
        "OUT_OF_SCOPE");
    fail(s, "/loans/" + loanId, "GET", null, 403, "OUT_OF_SCOPE");
  }

  @Test
  void lastAdministratorProtectionRollsBack() throws Exception {
    JsonNode row = null;
    for (var x : call(admin, "/admin/users", "GET", null))
      if (x.get("username").asString().equals("admin")) row = x;
    var b = json.convertValue(row, Map.class);
    b.put("enabled", false);
    b.put("password", "");
    fail(admin, "/admin/users/" + row.get("id").asLong(), "PUT", b, 409, "LAST_ADMIN");
    assertEquals("admin", call(admin, "/auth/me", "GET", null).get("username").asString());
  }

  @Test
  void csrfAndAnonymousDenied() throws Exception {
    assertEquals(
        403,
        mvc.perform(
                post("/api/loans").session(borrower).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(401, mvc.perform(get("/api/keys")).andReturn().getResponse().getStatus());
  }

  @Test
  void keyEditsCannotMoveDepartment() throws Exception {
    fail(
        custodian,
        "/keys/" + keyId,
        "PUT",
        m(
            "version",
            1,
            "code",
            "NEW",
            "departmentId",
            1,
            "name",
            "TEST",
            "cabinet",
            "TEST",
            "note",
            "",
            "category",
            "FACILITY"),
        409,
        "IDENTITY_LOCKED");
  }

  @Test
  void overlappingGrantRejected() throws Exception {
    fail(
        approver,
        "/grants",
        "POST",
        m(
            "keyId",
            keyId,
            "borrowerId",
            borrowerId,
            "validFrom",
            BASE.toString(),
            "validUntil",
            BASE.plusSeconds(7200).toString(),
            "reason",
            "TEST"),
        409,
        "GRANT_OVERLAP");
  }

  @Test
  void passwordResetRevokesExistingSession() throws Exception {
    JsonNode row = null;
    for (var x : call(admin, "/admin/users", "GET", null))
      if (x.get("id").asLong() == borrowerId) row = x;
    var b = json.convertValue(row, Map.class);
    b.put("password", "Bb8" + UUID.randomUUID());
    call(admin, "/admin/users/" + borrowerId, "PUT", b);
    fail(borrower, "/auth/me", "GET", null, 401, "UNAUTHENTICATED");
  }

  @Test
  void selfGrantRejectedAfterRolePromotion() throws Exception {
    JsonNode row = null;
    for (var x : call(admin, "/admin/users", "GET", null))
      if (x.get("id").asLong() == borrowerId) row = x;
    var b = json.convertValue(row, Map.class);
    b.put("roleId", 1);
    b.put("password", "");
    call(admin, "/admin/users/" + borrowerId, "PUT", b);
    try {
      fail(
          borrower,
          "/grants",
          "POST",
          m(
              "keyId",
              keyId,
              "borrowerId",
              borrowerId,
              "validFrom",
              BASE.toString(),
              "validUntil",
              BASE.plusSeconds(7200).toString(),
              "reason",
              "TEST"),
          409,
          "INDEPENDENT_REQUIRED");
    } finally {
      b.put("roleId", 4);
      b.put("version", row.get("version").asLong() + 1);
      call(admin, "/admin/users/" + borrowerId, "PUT", b);
    }
  }

  @Test
  void dueParameterReallyBlocksLongRequest() throws Exception {
    long second = user("px" + suffix, 4, 1);
    var g = grant(keyId, second);
    var s = login("px" + suffix);
    JsonNode setting = null;
    for (var x : call(admin, "/admin/settings", "GET", null))
      if (x.get("code").asString().equals("max_loan_hours")) setting = x;
    call(
        admin,
        "/admin/settings/" + setting.get("id").asLong(),
        "PUT",
        m("code", "max_loan_hours", "value", "1"));
    fail(
        s,
        "/loans",
        "POST",
        m(
            "grantId",
            g.get("id").asLong(),
            "dueAt",
            BASE.plusSeconds(3601).toString(),
            "purpose",
            "TEST"),
        409,
        "DUE_INVALID");
    call(
        admin,
        "/admin/settings/" + setting.get("id").asLong(),
        "PUT",
        m("code", "max_loan_hours", "value", "72"));
  }
}
