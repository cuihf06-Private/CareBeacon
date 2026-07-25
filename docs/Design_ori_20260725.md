# **产品需求与技术设计文档 (PRD & Tech Spec)：护航强提醒 App**

## **1\. 产品概述**

### **1.1 产品愿景**

打造一款专门针对“必须被执行”场景（如长辈按时吃药、儿童按时做作业）的安卓提醒应用。通过极致的系统权限调用，确保提醒一旦触发，**绝对不可被忽略、不可被其他应用覆盖**，且必须强制用户手动确认。

### **1.2 核心用户与角色**

* **被提醒人 (Ward 模式)**：长辈、儿童。界面要求极简（甚至不需要界面，只有弹窗）。**无权**修改任何设置和时间。  
* **监护人 (Guardian 模式)**：子女、家长。负责远程配置提醒时间、内容，并实时接收“已确认”的回执。

### **1.3 核心痛点与解决思路**

* **痛点**：国产安卓机（华为、小米、OPPO、vivo）后台查杀极其严重，常规闹钟或推送经常失效。  
* **解决思路**：必须采用“前台服务保活” \+ setAlarmClock 最高级别闹钟 \+ “引导用户开启自启动/无限制电池优化” \+ “本地离线存储策略”。

## **2\. 核心功能与交互流程**

### **2.1 账户与配对 (无密码设计)**

* 首屏选择身份：“我是监护人” 或 “我是被提醒人”。  
* **配对机制**：监护人端生成一个 6 位数字邀请码（或二维码），被提醒人端输入该码完成绑定。绑定关系持久化存储在云端服务器。

### **2.2 监护人端功能 (Guardian)**

* **排班管理 (CRUD)**：设置提醒标题（如“吃降压药”）、时间（精确到分钟，支持按周重复）、可选备注语音或文字。  
* **状态监控**：查看被提醒人今日的完成情况（待提醒、已确认及确认时间、已超时）。  
* **实时回执**：当被提醒人点击“我知道了”，监护人不仅界面状态更新，还会收到一条手机系统通知（如果应用在后台）。

### **2.3 被提醒人端功能 (Ward \- 核心难点)**

* **静默运行**：平时隐藏在后台，无多余界面。  
* **强力弹窗 (The Unignorable Alert)**：  
  * **触发**：时间一到，强制点亮屏幕并解锁（或覆盖在锁屏之上）。  
  * **视觉**：使用 SYSTEM\_ALERT\_WINDOW (全局悬浮窗) 覆盖全屏，屏蔽返回键和Home键的常规交互逻辑。  
  * **听觉**：无视手机当前静音/震动状态，强制接管 AudioManager，将 STREAM\_ALARM 音量调至最大，循环播放刺耳/响亮的专属铃声。  
  * **操作**：屏幕正中央只有一个巨大的红色按钮“我知道了”。不点击该按钮，铃声和弹窗**永远不会消失**。  
* **断网容灾**：即使手机没网，闹钟也必须准时响。点击“我知道了”后，若无网络，则将确认记录存在本地，等有网时自动重传给服务器。

## **3\. 技术架构设计 (Android 端)**

**技术栈建议**：Kotlin, Jetpack Compose (UI), Room (本地数据库), Retrofit \+ OkHttp (网络), Kotlin Coroutines & Flow (异步)。

### **3.1 突破国产机限制的保活与唤醒机制 (AI 必须重点实现)**

* **精准闹钟**：**绝对不能**使用 WorkManager 处理定时提醒。必须使用 AlarmManager.setAlarmClock()。这是 Android 允许的最高优先级闹钟，会在状态栏显示闹钟图标，国产系统通常不会拦截它。  
  * 需要权限：\<uses-permission android:name="android.permission.SCHEDULE\_EXACT\_ALARM" /\>  
* **电池与自启动白名单 (极其关键)**：  
  * App启动时，必须检查并弹窗引导用户去系统设置中**关闭应用的电池优化** (跳转 ACTION\_REQUEST\_IGNORE\_BATTERY\_OPTIMIZATIONS)。  
  * 针对 MIUI, EMUI, ColorOS，需要编写一个工具类，尝试通过特定 Intent 跳转到各厂商的“自启动管理”页面，要求用户手动开启自启动。  
* **前台服务 (Foreground Service)**：被提醒人模式下，必须启动一个带有持久通知栏的前台服务（如：“护航提醒运行中”），以极大降低被系统杀死的概率。  
* **开机自启**：需监听 ACTION\_BOOT\_COMPLETED 广播，手机重启后立刻重新注册所有的 AlarmManager 闹钟。

### **3.2 “不可忽略”的强弹窗实现细节**

1. **全局悬浮窗**：必须请求 \<uses-permission android:name="android.permission.SYSTEM\_ALERT\_WINDOW"/\> 权限。  
2. 在闹钟的 BroadcastReceiver 被触发时，启动一个透明的 Activity (使用全屏 Intent)。  
3. 在该 Activity 中：  
   * 设置 Flags 突破锁屏：FLAG\_SHOW\_WHEN\_LOCKED, FLAG\_TURN\_SCREEN\_ON, FLAG\_KEEP\_SCREEN\_ON, FLAG\_DISMISS\_KEYGUARD。  
   * 强制音量：  
     val audioManager \= getSystemService(Context.AUDIO\_SERVICE) as AudioManager  
     val maxVol \= audioManager.getStreamMaxVolume(AudioManager.STREAM\_ALARM)  
     audioManager.setStreamVolume(AudioManager.STREAM\_ALARM, maxVol, 0\)

   * 循环播放：使用 MediaPlayer 播放 res/raw 下的音频，设置 isLooping \= true。  
   * 如果应用退到后台（如用户狂按Home键尝试逃课），在 onPause 中应立即启动一个同等样式的**全屏 WindowManager 悬浮窗**（类型设为 TYPE\_APPLICATION\_OVERLAY），彻底堵死绕过途径。

## **4\. 后端架构设计 (私有 Linux 服务器)**

鉴于国内无法依赖 Google FCM，且接入各厂商（华为、小米）官方推送 SDK 过于繁琐（需要企业资质和繁复的审核），针对个人服务器，采用 **WebSocket 长连接 \+ 离线同步** 是最佳方案。

**技术栈建议**：Node.js (Express \+ Socket.io) 或 Python (FastAPI \+ WebSockets) 或 Go。

**数据库**：SQLite 或 PostgreSQL。

### **4.1 核心数据模型 (DB Schema)**

* **Users**: id, role (guardian/ward), pairing\_code  
* **Relationships**: guardian\_id, ward\_id  
* **Reminders**: id, guardian\_id, ward\_id, title, cron\_expression (或具体的 UTC 触发时间), audio\_note\_url, status  
* **Logs**: id, reminder\_id, ward\_id, action ("acknowledged"), timestamp

### **4.2 通信协议与逻辑**

1. **长连接维持**：监护人和被提醒人的 App 在打开或后台运行时，维持与服务器的 WebSocket 连接（需有心跳包 Ping/Pong 防止路由器掐断）。  
2. **数据下发 (Guardian \-\> Server \-\> Ward)**：  
   * 监护人创建提醒，通过 RESTful API POST /reminders 上传服务器。  
   * 服务器保存数据库后，查找该 Ward 是否在线。  
   * 若在线，通过 WebSocket 立即下发最新日程 JSON。  
   * Ward 收到后，更新本地 Room 数据库，并重置本地 AlarmManager。  
3. **确认回执 (Ward \-\> Server \-\> Guardian)**：  
   * Ward 点击“我知道了”。  
   * Ward App 先将确认记录存入本地 Room DB。  
   * 尝试通过 REST API POST /acknowledge 发送给服务器。  
   * 服务器接收后，通过 WebSocket 将“XXX已确认”消息推送给 Guardian，Guardian 的 App 弹出本地通知。  
   * 如果 Ward 发送失败（无网络），则本地标记为 synced=false，等待网络恢复后使用 WorkManager 补发。

## **5\. 针对 AI 助手的 Vibe Coding 提示指南 (Prompting Guide)**

AI 助手，请严格按照以下 5 个阶段来生成代码。**不要一次性生成所有代码，请按阶段与用户确认。**

* **Phase 1: 基础框架与本地数据库**  
  * 搭建带有 Jetpack Compose 的 Android 工程。  
  * 实现 Room Database，建立 Reminder 和 AcknowledgeLog 实体。  
  * 编写简单的双身份入口 UI (Guardian / Ward)。  
* **Phase 2: 核心闹钟与保活引擎 (The Alarm Engine)**  
  * 这是最重要的部分。编写 AlarmManager 的调度代码（必须用 setAlarmClock）。  
  * 编写 BootReceiver (开机重启自启)。  
  * 编写引导用户开启“悬浮窗权限”、“忽略电池优化”的权限申请工具类。  
* **Phase 3: 不可忽略的弹窗 (The Unignorable UI)**  
  * 编写 AlertActivity。应用锁屏点亮、强制最大音量播放逻辑。  
  * 添加“我知道了”按钮，点击后关闭声音、取消全屏、记录日志到数据库。  
* **Phase 4: Node.js/Python 后端与 REST API**  
  * 搭建简单的服务器，实现配对逻辑。  
  * 实现创建提醒、拉取提醒列表的接口。  
* **Phase 5: WebSocket 实时通信与离线同步**  
  * 在 Android 端集成 Socket.io-client 或 OkHttp WebSocket。  
  * 实现双端的数据实时同步和回执提醒。  
  * 处理断网情况下的本地队列积压与重传逻辑。