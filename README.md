# cc-rights-license

管理作品权利、地域期限和授权协议。

## 主要业务规则

- **作品与份额**：作品具有唯一编号；每个作品可登记多名权利人及其份额（`BigDecimal`，最多两位小数）。份额之和不得超过 100%，且只有在份额之和**精确等于 100%** 时才允许创建授权申请。
- **授权申请**：申请包含被授权方、一个或多个地域、一个或多个媒介、起止日期（闭区间）以及类型（独占 `EXCLUSIVE` / 非独占 `NON_EXCLUSIVE`）。
- **共同批准**：申请创建后进入 `PENDING`，收集各权利人决定。同意份额累计达到 100% 才触发批准；任一权利人拒绝则申请立即终止（`REJECTED`）。每名权利人只能决定一次，决定事件不可修改；同一事件号重复提交时幂等返回首次决定。
- **独占冲突检测**（按每个 地域 × 媒介 组合，日期闭区间重叠判定）：
  - 批准**独占**申请时，不得与任何已批准授权（独占或非独占）重叠；
  - 批准**非独占**申请时，不得与已批准**独占**授权重叠，但可与其他非独占授权并存。
- **原子批准**：多地域多媒介的整份申请要么全部生效（生成一条授权记录），要么整体标记为 `CONFLICT` 并记录冲突原因，不会部分生效。
- **并发控制**：申请行与作品行使用悲观写锁（`PESSIMISTIC_WRITE`）串行化审批；并发完成审批的冲突申请最多一个成功，失败申请保留全部决定记录并明确标记冲突原因。决定表通过唯一约束保证「每申请每权利人一条决定」和「每申请每事件号一条决定」。

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/works` | 创建作品（唯一编号） |
| POST | `/api/works/{code}/holders` | 登记权利人与份额 |
| GET | `/api/works/{code}/holders` | 查询权利人列表 |
| POST | `/api/works/{code}/applications` | 创建授权申请 |
| POST | `/api/applications/{id}/decisions` | 提交权利人决定（`holderId`、`decision`、`eventNumber`） |
| GET | `/api/applications/{id}` | 查询申请详情与状态 |
| GET | `/api/applications/{id}/progress` | 审批进度：已同意份额、决定记录、待决定权利人 |
| GET | `/api/applications/{id}/conflicts` | 冲突明细：冲突原因及与之重叠的已批准授权 |
| GET | `/api/works/{code}/calendar?from=&to=` | 作品授权日历（可按日期区间过滤） |

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
