# 哨戒机械臂移植工作前期总结

> 时间线：对话开始 → 用户说"反装底座依旧透明"之前
> 项目：Create Sentry Mechanical Arm 移植至 1.21.1 NeoForge

---

## 项目背景

将原版 Create Sentry Mechanical Arm 模组移植到 Minecraft 1.21.1 NeoForge。该模组增加了一个哨戒机械臂，可自动扫描、跟踪并射击目标。

## 核心问题

### 1. `setChunkForced` 导致存档损坏
原版使用 `serverLevel.setChunkForced()` 强制加载实体所在区块。在 NeoForge 下频繁调用会导致存档保存失败、NBT 损坏。
- **解决**: 全局 `TickEvent` 中为未加载区块的实体执行 tick；避免直接 force-load 区块

### 2. 物理结构（Aeronautics/Sable）子世界坐标转换
Create 的物理结构（飞艇、列车、电梯等）使用子世界（sub-level）系统。BlockEntity 实际在一个极远坐标（~2000万区块）处渲染，玩家看到的是投影。
- 需要将扫描、瞄准、射击的坐标在子世界空间（sub-level）与世界空间（world）之间正确转换
- 涉及 `SableCompanion`、`projectOutOfSubLevel`、`toLocalVector` 等 API

### 3. 客户端与服务端角度竞争
原始设计上客户端和服务端都在调用 `aimAtAngle()` 控制角度，导致两者互相覆盖、目标锁不定。
- **解决**: 服务端战斗模式下角度完全由服务端通过 `sendData()` 同步；客户端只使用 `chase(serverAngle, 0.25, EXP)` 追赶，不自行计算角度

### 4. 反装（天花板）模式底座透明
哨戒机械臂支持倒挂安装（上下颠倒）。将原有 6 个元素堆叠的底座模型绕 X 轴旋转 180° 后，由于 Model 加载器会剔除相邻面的共享面，导致底座对应面透明。
- **探索方案**: 改为单体无堆叠平面的独立模型 `sentry_base_ceiling.json`
- **用户反馈**: "反装底座依旧透明" → 此问题当时尚未彻底解决

---

## 完成的工作

### 物理结构坐标转换
- `scanForTarget()`: 扫描中心通过 `SableCompanion::projectOutOfSubLevel` 投影到世界坐标
- `getBestTargetPos()`: 实体世界坐标通过位姿逆矩阵转回子世界空间做 LOS 检测
- `isPointVisible()`: 使用 `toLocalVector` 将世界方向向量转本地
- `getProjectedMuzzlePos()`: 先在子世界空间计算枪口偏移，再投影到世界

### FakePlayer 定位
- `SentryFakePlayer.sync()`: 子世界模式下使用每个手臂独立的 `getProjectedMuzzlePos()` 计算位置

### 目标有效性检测
- `isValidTarget` 黑白名单反转逻辑修复（Friendly Fire 配置方向错误）

### 服务端角度控制
- `read()` 战斗模式下：用 `chase(serverAngle, 0.25, EXP)` 替代 `setValue`，平滑同步
- 客户端在 `syncedTargetId != -1` 时跳过自己的 `aimAtAngle()` 和 `resetAimer()`
- 每 5 tick 发送一次 `sendData()` 确保角度同步

### 追踪参数统一
- 两大模式（普通 & 物理结构）统一使用 45% 减速的追逐速度
- 追逐器: `EXP`，速度: 0.55/0.44/0.22/0.19（原 1.0/0.8/0.4/0.35 的 55%）

### 目标超时
- 每 300 tick（15 秒）强制重新扫描，即使缓存目标仍有效

### 空弹状态
- `NO_AMMO` 状态：调用 `sentryDeactivated()`（lowerArm→135°, upperArm→45°, head→0°），跳过 `sentryLogic()`
- 自动恢复：`hasAnyAmmo()` 检测内部弹药和弹药箱，有弹药时回到 `IDLE`
- HUD 显示红色 "无弹药" / "NO AMMO" 警告

### 聚焦瞄准（Focus Scope）
- 客户端在 `foundAceId == -1` 时发送 `SentryFocusPacket(-1, fcPos, targetId)` 走静态 `BlazeFireControlBlockEntity` 路径
- 第二次标记同一目标时切换锁定/解锁

---

## 文件清单

| 文件 | 说明 |
|------|------|
| `SentryArmBlockEntity.java` | 核心 BE，含子世界判断、坐标转换、弹药检测、战斗逻辑、角度同步 |
| `SentryFakePlayer.java` | FakePlayer，`sync()` 支持子世界枪口坐标投影 |
| `SentryMovementBehaviour.java` | 移动行为，`aimAtAngle()` 统一减速追逐 |
| `SentryClientInputHandler.java` | 客户端输入，聚焦瞄准回退路径 |
| `SentryFocusPacket.java` | 网络包：锁定/解锁切换 |
| `SentryHudHandler.java` | HUD 渲染，含空弹警告 |
| `SentrySpriteShifts.java` | 纹理映射表 |
| `SentryPartialModels.java` | 零件模型注册 |

---

## 已知未解决问题（反装透明前）

1. **反装底座透明** —— 玩家反馈依然存在，需进一步分析渲染管线
2. **彩色纺织变体** —— `colored/` 目录下的彩色纹理尚未适配新纹理集
3. **零件模型拆分** —— 统一模型手动拆分为 7 个 partial JSON 可能不精确，需 Blockbench GUI 重新导出各组

---

## 技术决策记录

1. 放弃全局 Tick 处理方案（`SentryGlobalLogic.java`），因复杂度太高收益低
2. 服务端角度权威：客户端战斗下不参与角度计算，仅追赶同步值
3. 天花板模式使用分离的 `sentry_base_ceiling.json` 单体模型避免面剔除
4. 弹药检测使用 `hasAnyAmmo()` 同时检查内部弹药和外部弹药箱
