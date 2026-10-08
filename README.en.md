[中文](README.md) | [English](README.en.md)

<img src="frontend/public/brand/logo.jpg" alt="ZhiHua Technology" width="170">

# KeyDesk · Physical Key Custody Management

**Source available for non-commercial learning · 1.0.0 · Java 21 / Spring Boot / Vue 3 / MySQL 8.4**

ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.). Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/).

A manual custody register for facilities, property teams, offices and campuses. It records who is authorized, who approves, who hands over a key and who inspects its return. Intended for learning role separation and transactional workflows in a small, single-instance installation.

## From authorization to an inspected return

```text
Register physical key → Approver grants another person time-limited access
                             ↓
Borrower requests → Independent approval → Custodian issues → Borrower accepts
                             ↓
Borrower requests return → Another custodian inspects → In cabinet / quarantine
                                                           ↓
                           Inspection request → Another approver releases key

Borrower cannot confirm → Custodian recovers physical key → Independent review
Loss reported → Quarantine with custody retained → Independent resolution → Retire key
```

**This is a record of manual physical operations.** Issuing immediately makes the key unavailable, including while borrower acceptance is pending. Requesting a return never frees the key. Software does not identify physical keys, open cabinets or doors, or replace locks. Staff are responsible for actually performing and checking physical handovers.

### Implemented capabilities

| Area | Actual behavior |
|---|---|
| Key register | Immutable item code and department, name, category, cabinet location and note; edit descriptions and retire with history retained; occupied keys cannot be edited |
| Named grants | An approver grants another person explicit validity, rejects overlapping active grants and can revoke without rewriting original dates; validity is `[from, until)` |
| Requests and approval | Personal active grant, maximum loan hours and a due time within the grant; no duplicate unresolved request for the same borrower/key; approval expiry is stored as a snapshot |
| Two-party custody | Borrower cannot approve or issue their own key; exclusive cabinet occupancy, borrower acceptance and version checks; no automatic replay of writes |
| Return and quarantine | Borrower requests return, another custodian records good or damaged inspection; damaged items require an inspection request and independent release; disabled accounts can be handled by physical recovery and separate verification |
| Loss resolution | Borrower or custodian reports loss; a separate approver records external evidence and risk handling; the key is retired, without automatically replacing a lock |
| Accounts and administration | BCrypt, same-origin sessions, CSRF, failed-login throttling, password change, session revocation after disable/reset; users, roles, permissions, departments, menus, categories and operational parameters; final full administrator protection |
| Scope and audit | All departments / own department / own custody records enforced by the server; append-only API event history and action audit; password hashes are not returned |
| Search and reports | Scoped search, state filtering, pagination and chronological ID ordering; custody states, available/quarantined/overdue counts; CSV export neutralizes formula prefixes |
| Deployment and recovery | Flyway V1/V2, health checks, three-service Compose, private random configuration, consistent paused-write backup and restore into a new isolated instance |

### Borrower and administrative interfaces

Borrowers see keys with active personal grants or their own history, request keys, acknowledge receipt, request returns, report loss and read their event history. Administrators manage identities and system directories; approvers grant and independently approve/review; custodians maintain the register, issue and inspect keys; auditors read departmental reports.

Custom roles configure permissions and scope but cannot bypass independent approval or physical-inspection restrictions. `ASSIGNED` means the person's own custody records in this product. Departments within one instance are not independent SaaS tenant isolation.

## Actual running screens

Screens are captured from this application. All business records are clearly marked `TEST`, with no real client data, cabinet locations or credentials.

| Screen | Function |
|---|---|
| ![Sign in](docs/screenshots/01-login.png) | Personal authentication and product identity |
| ![Borrower home](docs/screenshots/02-borrower.png) | Own records, outstanding keys and personal scope |
| ![Custody details](docs/screenshots/03-custody.png) | Actual handover states, times and appended events |
| ![Administrative register](docs/screenshots/04-keys.png) | Item codes, cabinet locations and current availability |
| ![Reports](docs/screenshots/05-reports.png) | Scoped metrics and CSV export |
| ![Roles](docs/screenshots/06-roles.png) | Permissions and data scopes |
| ![Settings](docs/screenshots/07-settings.png) | Maximum loan and approval validity hours |
| ![Grants](docs/screenshots/08-grants.png) | Named grant validity and revocation |

## Architecture and source layout

Browser → non-root Nginx same-origin proxy → Spring Security sessions/CSRF → fixed APIs and transactional services → JPA → MySQL8.4. Flyway upgrades schemas; Hibernate validates rather than edits them. Writes are serialized using the instance directory row lock, preventing double issue and races with revocation or administration. This is a low-capacity design, not a high-throughput architecture.

| Layer | Versions |
|---|---|
| Backend | Java21, Spring Boot4.0.7, Spring Security, Spring Data JPA, Flyway, MySQL Connector/J |
| Frontend | Vue3.5.43, Vite8.1.5, JavaScript, Lucide icons |
| Build | Maven3.9, Node24.19.0, npm lockfile, Spotless, Prettier, ESLint |
| Infrastructure | MySQL8.4, Docker Compose v2, Nginx1.29, non-root application containers |
| Verification | Python3.11+; HTTP business checks use the standard library |

```text
backend/src/main/java/cn/zhuatech/keydesk/  Identity, directories, grants and custody
backend/src/main/resources/db/migration/  V1 identity / V2 custody schemas
backend/src/test/                         Boundary and real HTTP/JPA integration tests
frontend/src/                            Borrower and staff interfaces
frontend/public/brand/                    Official logo
scripts/                                 Config, verification, backup, restore, release checks
docs/                                    APIs, operations, deployment, security and screenshots
compose.yaml                             Isolated database, backend and frontend
```

## Install and initialize

Install Docker with Compose v2. Generate private random configuration, then start:

```bash
python3 scripts/init-env.py
docker compose config --quiet
docker compose -p keydesk-local up -d --build --wait --wait-timeout 240
```

Open [http://127.0.0.1:8128](http://127.0.0.1:8128). Health: [http://127.0.0.1:8128/actuator/health](http://127.0.0.1:8128/actuator/health).

Initial account: `admin`. Read its generated `ADMIN_PASSWORD` from your local `.env`. There is no shared weak password. The generator refuses overwrites and uses file mode0600. Only an empty database receives the administrator, five roles, menus, categories and parameters; **no fabricated business records are seeded**. Create separate approver, custodian and borrower accounts. Environment changes do not reset existing user passwords.

### Configuration

| Variable | Meaning |
|---|---|
| `MYSQL_ROOT_PASSWORD` | Required independent random database administrator password |
| `DATABASE_PASSWORD` | Required independent random application database password |
| `ADMIN_USERNAME` | Initial administrator name, default `admin` |
| `ADMIN_PASSWORD` | Required, 12–72 UTF-8 bytes with uppercase, lowercase and a digit |
| `WEB_PORT` | Default8128; override to avoid other projects |
| `BIND_ADDRESS` | Default127.0.0.1 |
| `COOKIE_SECURE` | False for local HTTP; true behind public HTTPS |
| `DATABASE_URL` / `DATABASE_USER` | Optional external MySQL; verify TLS identity and trusted CA. Never commit real connection configuration. Bundled backup tools only support the internal database |

`.env.example` supplies names and guidance. Database and backend ports are not exposed to the host. Example: `WEB_PORT=8228 docker compose -p keydesk-local up -d`, adjusting page and health URLs accordingly. Administrative `max_loan_hours` accepts1–720 and `approval_hours` accepts1–72; they affect new requests/approvals and never rewrite existing deadlines.

### Local development

Use Java21, Maven3.9, Node24.19 and MySQL8.4. Create `zhuatech_keydesk`, inject `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `ADMIN_PASSWORD`. Example JDBC URL: `jdbc:mysql://127.0.0.1:3306/zhuatech_keydesk?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`. Inject passwords externally.

```bash
cd backend
mvn spring-boot:run
# Another terminal, from the project root
cd frontend
npm ci
npm run dev
```

Vite serves port5173 and proxies local port8080. Production pages use same-origin `/api`, with no fixed localhost backend address.

## Database, tests and deployment

Schema files: `backend/src/main/resources/db/migration/V1__identity.sql` and `V2__key_custody.sql`. They include foreign keys, unique item codes, query indexes and grant validity constraints. Upgrade by backing up, pausing writes and starting the new image. Flyway checks and applies new versions. Never edit applied migrations. Roll back using matching application images and a backup restored into a separate new instance.

```bash
cd backend
mvn spotless:check clean verify
cd ../frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
npm audit
cd ..
python3 -m py_compile scripts/*.py
python3 scripts/release-check.py
docker compose config --quiet
# Creates TEST records only in your designated isolated test instance
python3 scripts/smoke.py --base http://127.0.0.1:8128 --env-file .env --state-output /private/tmp/keydesk-quality-state.json
```

Docker builds run backend tests without skip flags. Test state contains test credentials and must remain private. Do not run fixture creation against production. Actual verification results are recorded in [Verification](docs/verification.md).

```bash
# Temporarily stops this backend for a consistent private backup, then restarts it
python3 scripts/backup.py --project keydesk-local --output /private/tmp/keydesk-backup.zip
# Separate configuration uses a new port; restore only into new resources
python3 scripts/restore.py /private/tmp/keydesk-backup.zip --project keydesk-restored --env-file /private/tmp/keydesk-restored.env
```

Backups contain password hashes and business records, use mode0600 and must be trusted before restore. See [Deployment](docs/deployment.md), [API](docs/api.md), [Operations](docs/operations.md) and [Security](docs/security.md).

## Limits and troubleshooting

- No smart cabinet, access-control hardware, scanner integration, messaging reminders, mobile app, e-signatures, tamper protection, SSO, distributed sessions or external connectors. No AI or paid service calls. Scanner adaptation is possible future customization, not a verified integration.
- Physical handover, damage checks and loss handling are manual records. Application audit is not tamper-proof third-party certification; database administrators can still modify data. Never use actual sensitive-room or critical-facility key information in public demonstrations.
- Sessions are in one backend's memory and require login after restart. Timestamps use UTC with microsecond precision, displayed in browser timezone; deadlines use continuous hours, without business-day calendars. Search/pagination/sort operate in the browser; each server entity list is capped at10000 records and returns an explicit error beyond that. No production capacity or high-load certification.
- Pending acceptance means the key has **already been handed out**. Grant revocation or approval expiry never recover a physical key. Receipt acknowledgment, return or loss reporting remain possible after issue; disabled borrowers require custodian recovery and independent verification.
- `GRANT_INACTIVE`: check validity/revocation. `KEY_UNAVAILABLE`: another record occupies the key. `VERSION_CONFLICT`: refresh and inspect before resubmitting. Network interruption yields an unknown result: refresh before retrying.
- Database readiness failures require checking this instance's logs and configuration; never remove other projects' volumes. Existing database passwords do not change automatically with `.env`.
- Empty lists can indicate missing grants, insufficient scope or no business records. Public deployment requires commercial authorization, HTTPS, backups, isolation and independent security review. This version is not claimed production-ready.

## License, contribution and contact

Self-owned source is governed by [LICENSE](LICENSE): **source available, non-commercial use**, limited to personal learning, research and non-commercial exchange. Commercial use, commercial delivery, paid deployment, SaaS operation, resale, commercial training and customization require prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. Preserve attribution. This is not an OSI open-source license. Third-party software retains its own licenses; see [NOTICE](NOTICE).

Contributions should include sanitized reproductions. Do not publish client information, real cabinet locations, credentials or database backups. Report security issues privately via official email. Software is supplied as-is, without warranties of suitability, physical custody safety or merchantability.

ZhiHua Technology provides enterprise digitization, private deployment, software development, implementation, systems integration, FDE outsourcing, OPC technical support and deep customization. Commercial licensing or customization enquiries:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
