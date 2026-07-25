# 账号体系重构方案 (v2)

> 文档状态：**待审阅**
> 创建时间：2026/07/25
> 关联文档：`Design_ori_20260725.md`

---

## 0. 现状与问题

当前 APK 中"身份"是绑定在**设备**上的：

| 组件 | 现状 | 问题 |
|---|---|---|
| `RoleStore` (DataStore) | 存一个 `role: String?`，取值只有 `guardian` / `ward` / `null` | 选了就改不回来 —— 这就是用户报的 bug |
| `RolePolicy` | 所有可见性、闹钟、编辑权限都基于"本机 role"判断 | 没有"账号"概念，无法表达"一个账号同时是监护人和被监护人" |
| `Reminder.ownerRole` | 提醒里只有 `guardian` / `ward` 两个值 | 同一个提醒无法对应到具体的监护人账号和具体的被监护人账号 |
| `RoleSelectScreen` | 一次性选择身份，没有"退出/切换"按钮 | 等同于注册 + 登录流程的缺失 |
| 演示模式 | 通过 `demoMode` 开关让两角色共享本机数据库 | 是绕过问题的临时方案，不是真正的多账号模型 |

**结论**：当前模型假设的是"一台手机 = 一个角色"（监护人和被提醒人分别在两台手机上）。这跟用户实际想做的"账号 + 邀请"模型不匹配，需要重构。

---

## 1. 目标 (Goals)

1. **修 bug**：可以退出当前账号、切换/注册新账号。
2. **多角色**：同一个账号**既是监护人又是被提醒人**，用一份账号、两套"关系"表达。
3. **可邀请**：作为被提醒人，可以邀请任意其他账号做自己的监护人；**允许邀请自己**（自监护，便于单设备演示）。
4. **后端就绪**：账号和关系的数据模型、Repository 接口从一开始就按"以后会挪到服务器"来设计，本期只做**本机存储的 mock 实现**，未来替换为 HTTP 实现时**不改 UI 和 ViewModel**。
5. **最小爆炸半径**：尽量复用现有 `Reminder` / `AckLog` 的 AlarmEngine / AlertActivity；改动主要集中在 `data/` 和 `ui/`。

---

## 2. 新领域模型

### 2.1 Account（账号）

```kotlin
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String,            // 本期用 UUID；后端化后即 server-side id
    val username: String,                  // 登录用，唯一
    val displayName: String,
    val passwordHash: String,              // 本期 SHA-256 简单哈希；后端化后由后端校验
    val createdAt: Long
)
```

* `username` 是登录标识，唯一索引（Room `@Index(unique=true)`）。
* 注册时本地生成 UUID + 哈希口令。
* **后端化时**：`passwordHash` 字段可保留也可移除（取决于后端是否回显），但 `Account` 接口保持不变。

### 2.2 Relationship（关系）

```kotlin
@Entity(tableName = "relationships",
        indices = [Index(value = ["wardId", "guardianId"], unique = true)])
data class Relationship(
    @PrimaryKey val id: String,
    val wardId: String,                    // 被监护人账号 id
    val guardianId: String,                // 监护人账号 id
    val status: String,                    // PENDING / ACCEPTED / REVOKED
    val invitedAt: Long,
    val acceptedAt: Long? = null
)
```

* 一条关系 = "某个账号是被监护人 X，某个账号是他的监护人 Y"。
* 同一对 (ward, guardian) 只允许一条；自邀请 `wardId == guardianId` 也允许。
* **后端化时**：status 流转由后端负责（邀请通知 / 同意 / 撤销）；本地 mock 直接置 ACCEPTED。

### 2.3 Reminder（提醒，扩展字段）

```kotlin
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val wardId: String,                    // 被监护人账号 id（取代旧 ownerRole="ward"）
    val guardianId: String,                // 创建该提醒的监护人账号 id（取代旧 ownerRole="guardian"）
    val title: String,
    val note: String = "",
    val hour: Int,
    val minute: Int,
    val weekMask: Int = 0,
    val nextTriggerAt: Long,
    val enabled: Boolean = true,
    val audioNotePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

* `ownerRole` 字段彻底移除。
* 旧数据迁移：见 §6。

### 2.4 领域关系

```
Account ──┬── 作为 ward 出现在  N 条 Relationship ── 关联 N 个 guardian 账号
          └── 作为 guardian 出现在  N 条 Relationship ── 关联 N 个 ward 账号
                  │
                  └── 每条 Relationship 上挂着 M 条 Reminder（Reminder 必须落在某条 Relationship 上）
```

**核心不变量**（用单元测试守护）：

| 不变量 | 测试类 |
|---|---|
| 一个账号 A 看到的"我监护的人"列表 = `Relationship.guardianId == A.id && status==ACCEPTED` 的所有 distinct wardId | `RelationshipPolicyTest` |
| 一个账号 A 看到的"监护我的人"列表 = `Relationship.wardId == A.id && status==ACCEPTED` 的所有 distinct guardianId | `RelationshipPolicyTest` |
| 创建 Reminder 必须满足：`currentAccountId == Relationship.guardianId`，且 `Relationship.wardId` 不为空 | `ReminderPolicyTest` |
| `wardId == guardianId` 的自邀请允许存在，对应的 Reminder 视为合法 | `ReminderPolicyTest` |
| 当前账号未登录时，所有数据访问必须被 `AuthGuard` 拒绝 | `AuthGuardTest` |

---

## 3. 持久化与 Repository 抽象

### 3.1 接口先行（后端化友好）

```kotlin
interface AccountRepository {
    suspend fun register(username: String, password: String, displayName: String): Account
    suspend fun login(username: String, password: String): Account          // 抛 InvalidCredentials
    suspend fun logout()
    suspend fun currentAccount(): Account?                                   // 已登录的账号
    suspend fun findByUsername(username: String): Account?
}

interface RelationshipRepository {
    suspend fun inviteGuardian(wardId: String, guardianUsername: String): Relationship  // 抛 GuardianNotFound / DuplicateInvite
    suspend fun acceptInvite(relationshipId: String)
    suspend fun revoke(relationshipId: String)
    fun observeMyWards(accountId: String): Flow<List<Relationship>>         // 我监护的人
    fun observeMyGuardians(accountId: String): Flow<List<Relationship>>     // 监护我的人
    suspend fun forAccount(accountId: String): List<Relationship>
}

interface ReminderRepository {
    fun observeRemindersFor(accountId: String, asRole: RoleSide): Flow<List<Reminder>>
    suspend fun upsert(reminder: Reminder): Long
    suspend fun delete(id: Long)
    suspend fun allEnabledFor(accountId: String, asRole: RoleSide): List<Reminder>
}

enum class RoleSide { GUARDIAN, WARD }   // 仅作为 UI 视角的过滤器
```

### 3.2 本期实现：`LocalXxxRepository`

* `LocalAccountRepository` — Room + DataStore
* `LocalRelationshipRepository` — Room
* `LocalReminderRepository` — Room（包装现有 `ReminderDao`，Dao 内 query 加 wardId/guardianId 过滤）

### 3.3 Session（已登录账号）

DataStore 单字段 `current_account_id: String?`。

```kotlin
class SessionStore(ctx: Context) {
    val currentAccountId: Flow<String?>
    suspend fun setCurrent(accountId: String)
    suspend fun clear()
}
```

> 替换掉 `RoleStore` 里的 `role` / `demoMode` / `pairingCode` 三个字段；旧字段在迁移期保留 1 个版本后删除（见 §6）。

---

## 4. UI 重构

### 4.1 路由（MainActivity 的 `when` 分支替换）

| 当前 | 新 |
|---|---|
| `role == null` → RoleSelectScreen | `session == null` → **AuthScreen**（登录/注册切换） |
| `role == guardian` → GuardianScreen | `session != null` → **HomeScreen**（我的账号 + 我的关系列表） |
| `role == ward` → WardScreen | 从 HomeScreen 进入 **GuardianWorkspaceScreen**（以监护人身份管理某 ward）或 **WardWorkspaceScreen**（以被监护人身份查看某 guardian） |

### 4.2 AuthScreen（新增，替换 RoleSelectScreen）

* 顶部 Tab：`登录` / `注册`。
* 登录：username + password → `AccountRepository.login()` → 成功后 `SessionStore.setCurrent()` → HomeScreen。
* 注册：username + displayName + password + confirm → 校验通过 → `AccountRepository.register()` → 自动登录。
* 错误提示：用户名不存在 / 密码错误 / 用户名已存在 / 密码长度 < 6。
* **无任何"角色选择"按钮** —— 身份完全由后续关系决定。

### 4.3 HomeScreen（新增）

```
┌──────────────────────────────────────┐
│  CareBeacon                  [退出]   │
│  当前账号：张三 (zhangsan)            │
├──────────────────────────────────────┤
│  我监护的人 (2)            [+ 邀请]    │
│   • 李四  → [管理提醒]                │
│   • 自己  → [管理提醒]                │
│                                       │
│  监护我的人 (1)            [+ 邀请]    │
│   • 王五                              │
└──────────────────────────────────────┘
```

* "我监护的人"列表来自 `RelationshipRepository.observeMyWards(currentId)`。
* 每条右侧一个 `[管理提醒]` 按钮 → 进入 GuardianWorkspaceScreen，传 wardId。
* "监护我的人"列表来自 `observeMyGuardians(currentId)`。
* 两个列表上方都有 `[+ 邀请]` 按钮 → 弹 InviteGuardianSheet（输入 username 邀请做自己的监护人 / 邀请某账号做某 ward 的监护人）。
* 顶栏退出按钮 → `SessionStore.clear()`、停 WardForegroundService、cancel 所有 AlarmManager → AuthScreen。

### 4.4 InviteGuardianSheet（新增）

* 输入框：被邀请人的 username。
* 下拉框（可选）：指定 wardId。默认就是当前账号作为被监护人；如果当前账号同时监护别人，可选"为李四邀请监护人"。
* 点击确认 → `RelationshipRepository.inviteGuardian()`，本期直接 ACCEPTED。

### 4.5 GuardianWorkspaceScreen（改造现有 GuardianScreen）

* 入参：`wardId: String`（从 HomeScreen 传入）。
* 顶部显示 "为 李四 管理提醒"。
* `viewModel.reminders` 改为 `ReminderRepository.observeRemindersFor(currentId, GUARDIAN)` 且 `reminder.wardId == wardId`。
* 保留 6 位邀请码卡片（不变）。

### 4.6 WardWorkspaceScreen（改造现有 WardScreen）

* 入参：`guardianId: String`（默认选第一个监护人；如果自监护就是当前账号自己）。
* 顶部显示 "由 王五 监护"。
* `viewModel.reminders` 改为 `observeRemindersFor(currentId, WARD)` 且 `reminder.guardianId == guardianId`。
* 进入此屏时启动 WardForegroundService + arm reminders（仅在该 workspace 是当前账号的 ward 视角时）。
* 离开时停服务。

### 4.7 整体导航图

```
AuthScreen ──login/register──> HomeScreen
                                  │
       ┌──────────────────────────┼──────────────────────────┐
       ▼                          ▼                          ▼
 GuardianWorkspace(wardId)   WardWorkspace(guardianId)   InviteSheet
       │                          │
       └──> ReminderEdit          └──> [启动守护] [权限]
```

---

## 5. AppViewModel 重构

* 移除 `role: StateFlow<String?>`，替换为：
  * `session: StateFlow<String?>`（当前账号 id）
  * `currentAccount: StateFlow<Account?>`（从 accounts 表 observe）
* 新增 `relationships: StateFlow<List<Relationship>>`（observe 当前账号的所有关系）。
* `reminders` 改为入参 `(asRole: RoleSide, peerId: String)`，由 Screen 持有 `peerId` 并传入。
* 新增方法：
  * `login(username, password)` / `register(...)` / `logout()`
  * `inviteGuardian(wardId, guardianUsername)` / `revokeRelationship(id)`
  * `armRemindersForCurrentWard()` —— 把现在 MainActivity 里的 arm 逻辑搬进来，统一在登出时取消。
* 保留 `saveReminder / deleteReminder / fireNow`，内部加 `RelationshipPolicy.assertCanEdit(reminder, currentId)` 检查。
* 删除 `pairCode`（不再有"全局邀请码"概念；改为每条关系独立 token，但本期本地 mock 不实际使用，保留字段占位也可，**建议本期直接删除以保持简洁**）。

---

## 6. 数据迁移与兼容性

### 6.1 旧 DataStore 字段

* `role` / `demoMode` / `pairingCode` 保留 1 个版本：在登出/迁移时清掉。
* 本期**不读取**它们，直接当成"无 session"。

### 6.2 旧 Room 数据

* DB 版本从 1 → 2。
* `Migration_1_2`：
  1. 新建 `accounts`、`relationships` 表（空表）。
  2. 给 `reminders` 加 `wardId TEXT NOT NULL DEFAULT ''` 和 `guardianId TEXT NOT NULL DEFAULT ''` 列。
  3. **不尝试回填旧 reminders** —— 它们没有归属账号，没有意义。提供一次性的 `Migration_1_2.backfillDemo()` 方法：
     * 如果旧 reminders 表非空：插入两条占位账号 `"legacy-guardian"` 和 `"legacy-ward"`，建立关系，把旧 reminders 的 `wardId/guardianId` 全部填上这两个 id。
     * 不自动登录；用户在 AuthScreen 自己选登录或注册。
  4. 本次出于"严重 bug 重构"考虑，**提供一个隐藏入口 "导入旧数据并登录为 legacy-ward"**：如果 `accounts` 表为空但 `reminders` 旧数据有内容，AuthScreen 顶部显示一条横幅提示。
* 因为本地是开发期，可以容忍 `fallbackToDestructiveMigration()` —— 但**生产前必须删掉**。

---

## 7. 配套修改

### 7.1 AlarmEngine / TriggerCalculator

* `canArm(reminder, localRole)` 改为 `canArm(reminder, account, relationship)`：
  * 当前账号 == `reminder.wardId` 且 存在 ACCEPTED 关系且 `relationship.guardianId == reminder.guardianId`。
* `rescheduleAll` 接收当前账号 id 过滤；登出时 `cancelAll()`。
* `WardForegroundService` 由 `SessionStore` 状态驱动（已登录 + 有 ward 关系时启；登出/账号切换时停）。

### 7.2 单元测试（必加，挡回归）

| 文件 | 覆盖 |
|---|---|
| `AccountRepositoryTest` | 注册重复用户名抛异常；错误密码 login 抛 InvalidCredentials；register 后立刻 currentAccount() 不为空 |
| `RelationshipPolicyTest` | 邀请自己合法；邀请自己第二次抛 DuplicateInvite；撤销后从 observeMyWards 消失 |
| `ReminderPolicyTest` | 当前账号不是 reminder.guardianId 时 upsert 抛 Forbidden；自监护 reminder 可见且可 arm |
| `AuthGuardTest` | `currentAccountId == null` 时所有 Repository 调用抛 NotAuthenticated |
| `AppViewModelLogoutTest` | logout 后 session=null、reminders 为空、cancelAll 被调用 |

### 7.3 配套小修

* `AndroidManifest.xml`：现有 `<activity>` / `<service>` / `<receiver>` 不动；新增的 Sheet 用 Compose Dialog 实现，不增 Activity。
* `proguard-rules.pro`：UUID 生成、JSON 序列化字段保留（暂未引入 JSON，可不动）。

---

## 8. 实施顺序（建议 4 个 PR / 4 次提交）

1. **PR1 — 数据层**：新增 `Account` / `Relationship` 实体 + Dao + `AccountRepository` / `RelationshipRepository` 本地实现 + SessionStore；迁移脚本；通过单元测试。
2. **PR2 — ViewModel 改造**：替换 role/reminders 状态；新增 login/logout/invite/revoke；保留旧 `setRole/pairWithGuardian` 但打 `@Deprecated` 以便过渡期编译。
3. **PR3 — UI 替换**：AuthScreen / HomeScreen / InviteSheet 上线；旧 RoleSelectScreen / WardScreen / GuardianScreen 仍存在但不在导航中。下线 demoMode 开关。
4. **PR4 — 清扫**：删除旧 RoleStore 字段、demoMode 分支、`setRole/pairWithGuardian`、RoleSelectScreen；AlarmEngine 与 WardForegroundService 切到 SessionStore 驱动；回归测试。

每步结束都跑 `./gradlew test`，确保通过再进入下一步。

---

## 9. 不在本期范围

* 真实服务器后端 / REST API / WebSocket（Design 文档 §4 的内容）
* 推送通知 / 厂商通道（华为/小米 push SDK）
* 邀请码扫码 / 二维码
* 账号找回 / 改密
* 多设备登录同一账号的同步策略（先按"最后登录者赢"处理）

---

## 10. 已确认的决策 (2026/07/25)

1. **密码**：本期**无密码**。`Account.passwordHash` 字段移除，注册/登录只用 `username`。
2. **自邀请**：生产环境允许 `wardId == guardianId` 的自监护关系，不是调试开关。
3. **旧数据迁移**：`fallbackToDestructiveMigration()` + 一次性回填脚本：
   * 创建 1 个账号 `legacy` 和 1 条自监护关系（wardId == guardianId == legacy.id）。
   * 把所有旧 reminders 的 `wardId/guardianId` 都填上 legacy.id。
   * DataStore 不写 `current_account_id`，让用户在 AuthScreen 自己选登录或注册；如果用户想找回旧数据，用 username `legacy` 即可登录。
4. **自动登录**：DataStore 持久化 `current_account_id`；冷启时若该字段非空，直接进 HomeScreen，否则进 AuthScreen。
5. **PR 拆分**：保留 4 个独立分支，逐一 commit + merge 到 main。

---

## 11. PR1 — 数据层（本文件后续追加）

详见 `docs/AccountSystem_Redesign_PR1_20260725.md`（实现时落地）。