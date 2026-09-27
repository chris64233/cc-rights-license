# cc-rights-license

管理作品权利、地域期限和授权协议，并支持**受控转授权**（多层级权利树）。

## 主要业务规则

### 一级授权

- **作品与份额**：作品具有唯一编号；每个作品可登记多名权利人及其份额（`BigDecimal`，最多两位小数）。份额之和不得超过 100%，且只有在份额之和**精确等于 100%** 时才允许创建授权申请。
- **授权申请**：申请包含被授权方、一个或多个地域、一个或多个媒介、起止日期（闭区间）以及类型（独占 `EXCLUSIVE` / 非独占 `NON_EXCLUSIVE`），并可附带**转授权策略声明**（见下）。
- **共同批准**：申请创建后进入 `PENDING`，收集各权利人决定。同意份额累计达到 100% 才触发批准；任一权利人拒绝则申请立即终止（`REJECTED`）。每名权利人只能决定一次，决定事件不可修改；同一事件号重复提交时幂等返回首次决定。
- **独占冲突检测**（按每个 地域 × 媒介 组合，日期闭区间重叠判定，仅统计有效授权）：
  - 批准**独占**申请时，不得与任何有效授权（独占或非独占）重叠；
  - 批准**非独占**申请时，不得与有效**独占**授权重叠，但可与其他非独占授权并存。
- **原子批准**：多地域多媒介的整份申请要么全部生效（生成一条授权记录），要么整体标记为 `CONFLICT` 并记录冲突原因，不会部分生效。
- **并发控制**：申请行与作品行使用悲观写锁（`PESSIMISTIC_WRITE`）串行化审批；并发完成审批的冲突申请最多一个成功，失败申请保留全部决定记录并明确标记冲突原因。

### 受控转授权

- **转授权策略声明**：每条授权（含一级授权和每层转授权）可声明：
  - 是否允许转授权 `sublicensable`；
  - 允许向下转授的**层级数** `maxLevels`（相对本授权；不传表示不限）；
  - 允许转授的**地域**、**媒介**范围（不传表示与本授权范围一致）；
  - 允许转授的**期限范围** `startBound` / `endBound`（不传表示与本授权期限一致）。
  - 策略范围必须是授权自身范围的子集，否则创建申请时直接拒绝（400）。下级授权生效时，其再转授策略会被上级的剩余授权空间自动夹取，无法通过声明更宽松的策略逃逸层级或范围限制。
- **申请方资格**：转授权申请方必须是上级授权**当前有效**（`ACTIVE`）的被授权方；上级处于暂停、终止、到期状态时不能发起或批准新的转授权。
- **完全落在上级范围内**：申请的层级、地域、媒介、起止日期必须完全落在上级授权及其转授权策略允许的范围内，任一维度越界整份申请拒绝（422）。
  - 上级为**非独占**时，不得授予**独占**转授权（独占权利不能由非独占权利派生）。
- **独占冲突（同作品全局）**：
  - 独占转授权不得与同作品任何有效授权（含其他权利树）重叠；
  - 非独占转授权不得与任何有效独占授权重叠；
  - 自身层级链上的祖先授权天然覆盖申请范围，不计为冲突。
- **整份申请原子校验**：所有 地域 × 媒介 × 日期 组合作为一个整体校验，要么全部生效，要么整体 `CONFLICT`，不会部分生效。
- **审批决定**：转授权由上级被授权方单方审批（批准/拒绝），保存审批决定、事件号（幂等）以及**批准时实际采用的上级授权版本**（`parentVersion`）。
- **转授权号幂等**：`sublicenseNumber` 全局唯一；同一转授权号重复提交返回首次申请，不重复创建；同号用于不同上级授权返回 409。
- **层级链**：每条授权记录保存 `parentGrantId`、`rootGrantId`、`depth`（根授权为 0）与物化路径 `chainPath`（如 `/3/7/9/`），支持整棵权利树查询。
- **授权版本**：授权状态或范围每次变化 `version` 加 1；转授权申请记录创建时与批准时的上级版本。

### 上级状态变化的级联传播

上级授权**到期、撤销或范围缩减**时，受影响的全部下级授权进入暂停或终止：

| 上级事件 | 下级处理 |
| --- | --- |
| 撤销 `revoke` | 全部下级（逐层）**终止** `TERMINATED` |
| 到期 `expire`（每日巡检或管理入口） | 下级自身已到期则 `EXPIRED`，否则失去权利来源**暂停** `SUSPENDED` |
| 范围缩减 `reduce-scope`（只允许缩小） | 不再被新范围完整覆盖的下级**暂停**；仍被覆盖的保持有效 |

- **不遗漏**：状态变化事务内即为每个受影响下级落库一条级联任务（`cascade_tasks`，带 `(目标授权, 事件, 来源授权)` 唯一约束），任务与状态变化同事务提交。
- **可重试**：每条任务在独立事务（`REQUIRES_NEW`）中处理，失败记为 `FAILED`（含尝试次数与错误信息），可通过管理接口或定时补偿反复重试，成功才置 `DONE`，单条失败不阻塞其他任务。
- **并发不逃逸**：新建/批准转授权、撤销、缩减、到期都先按**根授权**加悲观写锁，再加锁并强制 `refresh` 目标授权（消除锁等待期间一级缓存的旧状态）。等待锁期间上级若已变化，审批会按最新状态重新原子校验，申请标记 `CONFLICT` 而不会逃逸为生效授权。

## 主要接口

### 一级授权

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/works` | 创建作品（唯一编号） |
| POST | `/api/works/{code}/holders` | 登记权利人与份额 |
| GET | `/api/works/{code}/holders` | 查询权利人列表 |
| POST | `/api/works/{code}/applications` | 创建授权申请（可含 `sublicensePolicy`） |
| POST | `/api/applications/{id}/decisions` | 提交权利人决定（`holderId`、`decision`、`eventNumber`） |
| GET | `/api/applications/{id}` | 查询申请详情与状态 |
| GET | `/api/applications/{id}/progress` | 审批进度：已同意份额、决定记录、待决定权利人 |
| GET | `/api/applications/{id}/conflicts` | 冲突明细：冲突原因及与之重叠的有效授权 |
| GET | `/api/works/{code}/calendar?from=&to=` | 作品授权日历（可按日期区间过滤） |

### 转授权与权利树

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/grants/{parentGrantId}/sublicenses` | 创建转授权申请（`sublicenseNumber` 幂等，含范围与转授权策略） |
| GET | `/api/sublicenses/{id}` | 查询转授权申请（含 `parentVersionAtCreation` 与策略） |
| POST | `/api/sublicenses/{id}/decisions` | 上级被授权方审批（`decision`、`eventNumber`，事件号幂等） |
| GET | `/api/sublicenses/{id}/decisions` | 转授权审批决定记录 |
| GET | `/api/sublicenses/{id}/conflicts` | 转授权冲突对象查询 |
| GET | `/api/grants/{id}` | 授权详情（状态、版本、层级链、转授权策略） |
| GET | `/api/grants/{id}/tree` | **权利树**：以该授权为根的全部下级（按层级排序） |
| GET | `/api/grants/{id}/children` | 直接下级授权 |
| GET | `/api/grants/{id}/effective-scope` | **有效范围**：沿层级链逐层求交后的实际有效地域、媒介、期限及失效原因 |
| POST | `/api/grants/{id}/revoke` | 撤销授权（级联终止全部下级） |
| POST | `/api/grants/{id}/reduce-scope` | 缩减授权范围（级联暂停越界下级） |
| POST | `/api/grants/{id}/expire` | 立即到期（管理入口；默认每日 00:05 自动巡检） |
| GET | `/api/cascade/tasks` | 级联传播任务列表（状态、尝试次数、错误） |
| POST | `/api/cascade/process` | 手动触发一轮任务处理（重试入口） |

### 请求示例

创建允许转授权（1 层、仅限 CN/TV、期限缩限）的一级授权申请：

```json
{
  "licensee": "发行商甲",
  "type": "EXCLUSIVE",
  "startDate": "2026-01-01",
  "endDate": "2028-12-31",
  "territories": ["CN", "JP"],
  "media": ["TV", "WEB"],
  "sublicensePolicy": {
    "sublicensable": true,
    "maxLevels": 1,
    "territories": ["CN"],
    "media": ["TV"],
    "startBound": "2026-03-01",
    "endBound": "2028-09-30"
  }
}
```

创建转授权申请：

```json
{
  "sublicenseNumber": "SUB-2026-0001",
  "applicant": "发行商甲",
  "sublicensee": "渠道商乙",
  "type": "EXCLUSIVE",
  "startDate": "2026-03-01",
  "endDate": "2028-09-30",
  "territories": ["CN"],
  "media": ["TV"],
  "sublicensePolicy": { "sublicensable": false }
}
```

## 授权状态

`ACTIVE`（有效）→ `SUSPENDED`（因上级到期/缩减暂停）、`TERMINATED`（撤销）、`EXPIRED`（到期）。冲突检测与新转授权只认 `ACTIVE` 授权；有效范围查询沿层级链求交，链上任一节点非有效即整体失效并返回原因。

## 定时任务与配置

- 到期巡检：每日 `00:05`（`rights.cascade.expiry-cron` 可配）。
- 失败级联任务补偿重试：每 60 秒（`rights.cascade.retry-delay-ms` 可配）。
- 总开关：`rights.scheduling.enabled`（默认 `true`；测试环境为 `false`）。

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

测试覆盖（共 43 个用例）：一级授权审批/冲突/并发，转授权范围与层级校验、非独占不得授独占、多组合原子性、转授权号与事件幂等、版本与层级链、权利树/有效范围/冲突查询、撤销/缩减/到期级联、失败任务重试补偿，以及「审批与上级撤销并发不得逃逸」「两个独占转授权并发最多一个生效」等并发场景。
