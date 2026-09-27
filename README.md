# cc-rights-license

管理作品权利、地域期限和授权协议，并支持**受控转授权（Sublicense）**：
根授权来自权利人共同批准；已授权方可在上级声明范围内向下转授，形成一棵可查询、可级联管控的权利树。

## 主要业务规则

### 原始授权（根授权）

- **作品与份额**：作品具有唯一编号；每个作品可登记多名权利人及其份额（`BigDecimal`，最多两位小数）。份额之和不得超过 100%，且只有在份额之和**精确等于 100%** 时才允许创建授权申请。
- **授权申请**：申请包含被授权方、一个或多个地域、一个或多个媒介、起止日期（闭区间）以及类型（独占 `EXCLUSIVE` / 非独占 `NON_EXCLUSIVE`）。
- **共同批准**：申请创建后进入 `PENDING`，收集各权利人决定。同意份额累计达到 100% 才触发批准；任一权利人拒绝则申请立即终止（`REJECTED`）。每名权利人只能决定一次，决定事件不可修改；同一事件号重复提交时幂等返回首次决定。
- **独占冲突检测**（按每个 地域 × 媒介 组合，日期闭区间重叠判定）：
  - 批准**独占**申请时，不得与任何已批准根授权（独占或非独占）重叠；
  - 批准**非独占**申请时，不得与已批准**独占**根授权重叠，但可与其他非独占授权并存。
- **原子批准**：多地域多媒介的整份申请要么全部生效（生成一条授权记录），要么整体标记为 `CONFLICT` 并记录冲突原因，不会部分生效。
- **并发控制**：申请行与作品行使用悲观写锁（`PESSIMISTIC_WRITE`）串行化审批；并发完成审批的冲突申请最多一个成功。决定表通过唯一约束保证「每申请每权利人一条决定」和「每申请每事件号一条决定」。

### 受控转授权

- **转授权声明**：根授权申请可携带 `sublicense` 声明，内容包括：
  - 是否允许转授权 `allowed`；
  - 允许的**最大层级** `maxDepth`（根为第 1 层，直接下级为第 2 层，必须 ≥ 2）；
  - 允许向下转授的**地域 / 媒介 / 期限上限**（缺省即与本级范围相同，且不得超出本级范围）。
  下级授权生效时携带的再转授声明由"上级声明"与"本次申请声明"逐字段取交集，**权利绝不放大**。
- **申请主体与范围**：转授权申请方必须是上级授权的**当前有效被授权方**；申请的日期、地域、媒介必须**完全落在上级授权当前范围**内，并落在上级声明的"可转授范围"内；层级深度不得超过 `maxDepth`。
- **独占规则**：
  - 上级为**独占**时，独占下级不得与任何已存在的有效下级（独占/非独占）在任一 地域 × 媒介 × 日期 组合上重叠；非独占下级不得与已有独占下级重叠；
  - 上级为**非独占**时，不得向下授予独占授权（不能授予超出自身的独占范围）。
- **原子校验**：所有 地域 × 媒介 × 日期 组合作为整份申请一次性校验，任一组合冲突则整体 `CONFLICT`，不产生任何下级授权。
- **版本机制**：授权范围缩减会写入新版本快照（`grant_versions`）。转授权申请固化其采用的**上级授权版本**（`parentVersion`），审批时若上级版本已变化（范围被缩减）则整份申请冲突，必须按新版本重新申请。
- **幂等**：
  - 转授权号 `sublicenseNo` 全局唯一，重复提交（含并发）幂等返回首次申请，唯一约束兜底；
  - 审批决定按事件号幂等；决定人唯一；只有上级当前被授权方可以审批。
  - 批准后保存审批决定、采用的上级版本、层级链（根在前自身在末）与物化祖先路径。

### 级联传播（暂停 / 恢复 / 终止 / 范围缩减 / 到期）

- **撤销**：授权终止，整棵子树（任意层级）全部终止（`PARENT_REVOKED`）。
- **暂停 / 恢复**：授权暂停时整棵子树暂停（`PARENT_SUSPENDED`），暂停期间不得新增下级转授权；恢复由统一守卫判定——所有上级均已恢复有效，且未与暂停期间新授予的有效兄弟授权发生独占冲突。若恢复时发生独占冲突，该节点保持暂停并标记 `RESUME_CONFLICT`，在冲突消除（如撤销冲突授权）后再次发起恢复即可生效。
- **范围缩减**：只能缩小（期限、地域、媒介均为子集）。生成新版本；不再被新范围完整覆盖的下级立即终止（`PARENT_SCOPE_REDUCED`），仍被完整覆盖的下级保留。
- **到期**：到期扫描把已过截止日且仍有效的授权标记终止并级联（`PARENT_EXPIRED`）。
- **可靠性（可重试、不遗漏）**：状态变化与为每个受影响下级写入的传播任务（`grant_propagations`）在**同一事务**落库；事务提交后逐条以独立事务应用。单条失败只记录失败次数并保留 `PENDING`，可通过重试接口反复处理，状态机守卫（终止不可逆、暂停不覆盖终止、恢复需全部祖先有效）保证重复应用幂等。
- **并发安全**：转授权审批与上级状态变化都对上级授权行加悲观写锁串行化，"新下级申请批准"与"上级撤销/暂停/缩减"并发时不会逃逸限制——不可能出现"上级已终止而下级仍生效"。

### 查询

提供权利树、层级链、有效范围与冲突对象查询：直接下级、整棵子树（列表/树状）、含自身的层级链、按状态过滤的下级，以及转授权申请的冲突对象明细。

## 主要接口

### 作品与原始授权

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/works` | 创建作品（唯一编号） |
| POST | `/api/works/{code}/holders` | 登记权利人与份额 |
| GET | `/api/works/{code}/holders` | 查询权利人列表 |
| POST | `/api/works/{code}/applications` | 创建授权申请（可带 `sublicense` 转授权声明） |
| POST | `/api/applications/{id}/decisions` | 提交权利人决定（`holderId`、`decision`、`eventNumber`） |
| GET | `/api/applications/{id}` | 查询申请详情与状态 |
| GET | `/api/applications/{id}/progress` | 审批进度 |
| GET | `/api/applications/{id}/conflicts` | 根授权冲突明细 |
| GET | `/api/works/{code}/calendar?from=&to=` | 作品授权日历 |

`POST /api/works/{code}/applications` 的 `sublicense` 字段示例：

```json
"sublicense": {
  "allowed": true,
  "maxDepth": 3,
  "territories": ["CN"],
  "media": ["TV", "WEB"],
  "startDate": "2026-01-01",
  "endDate": "2026-06-30"
}
```

### 转授权

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/sublicenses` | 创建转授权申请（`sublicenseNo` 幂等、`parentGrantNo`、范围、可选下级 `sublicense` 声明） |
| GET | `/api/sublicenses/{id}` | 按 id 查询转授权申请（含采用的上级版本、批准后生成的授权号） |
| GET | `/api/sublicenses/by-no/{sublicenseNo}` | 按转授权号查询（幂等取回） |
| POST | `/api/sublicenses/{id}/decisions` | 上级当前被授权方审批（`decider`、`decision`、`eventNumber`） |
| GET | `/api/sublicenses/{id}/conflicts` | 冲突对象（与之重叠的有效下级授权） |

### 授权生命周期与级联

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/grants/{grantNo}/revoke` | 撤销（body：`eventNo`，幂等） |
| POST | `/api/grants/{grantNo}/suspend` | 暂停 |
| POST | `/api/grants/{grantNo}/resume` | 恢复 |
| POST | `/api/grants/{grantNo}/reduce-scope` | 范围缩减（新期限/地域/媒介，必须是子集） |
| POST | `/api/grants/expire-scan?today=` | 到期扫描并级联 |
| POST | `/api/grants/propagations/retry` | 重试全部待处理级联任务，返回成功应用数 |
| GET | `/api/grants/propagations/pending-count` | 待处理级联任务数（用于核对"不遗漏"） |

### 权利树与有效范围

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/grants/{grantNo}` | 授权详情（授权号、层级、范围、版本、状态、转授声明、层级链） |
| GET | `/api/grants/{grantNo}/children` | 直接下级 |
| GET | `/api/grants/{grantNo}/subtree` | 整棵子树（扁平列表，按层级排序） |
| GET | `/api/grants/{grantNo}/tree` | 整棵子树（嵌套树状结构） |
| GET | `/api/grants/{grantNo}/chain` | 含自身的层级链（根在前） |
| GET | `/api/grants/{grantNo}/descendants?status=ACTIVE` | 按状态过滤的全部下级 |

## 数据模型要点

- `license_grants`：统一表示根授权与下级授权；`parent_grant_id`、`depth`、`ancestor_path`（物化祖先路径）、`grant_chain`（含自身层级链）表达权利树；`status`（`ACTIVE`/`SUSPENDED`/`TERMINATED`）、`halt_reason`、`current_version` 记录当前状态与版本。
- `sublicense_applications` / `sublicense_decisions`：转授权申请与审批决定，固化 `parent_version`，转授权号与事件号均唯一。
- `grant_versions`：每次范围变化的不可变快照（范围 + 当时的转授声明）。
- `grant_lifecycle_events`：撤销/暂停/恢复/到期/缩减事件，事件号幂等。
- `grant_propagations`：级联传播任务（outbox），状态 `PENDING`/`APPLIED`，记录重试次数与错误。

## 开发环境

- JDK 21
- Spring Boot 4.1.1
- Maven Wrapper 3.9.9
- H2

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test
