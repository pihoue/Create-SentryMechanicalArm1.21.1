# Create: Sentry Mechanical Arm（非官方移植）

一个 Minecraft NeoForge 模组，让 Create 动力臂能够装备 [TaCZ](https://github.com/tacz-dev/tacz)（Timeless and Classics: Zero）枪械，作为自动化防御炮塔使用。

## 功能特性

- **自动索敌** — 哨戒臂在范围内扫描敌对实体，锁定目标并以平滑关节运动追踪。
- **完整 TaCZ 兼容** — 支持所有 TaCZ 枪械：半自动、连射、全自动、栓动、充能武器。
- **火控系统** — 连接烈焰火控台，可跨多个哨戒臂统一管理目标白名单/黑名单。
- **聚焦瞄准** — 使用哨戒瞄准镜指定优先目标，所有已连接哨戒集中攻击。
- **弹药管理** — 为哨戒臂附加弹药箱实现自动补给，弹药可用时即时装填。
- **物理结构兼容** — 完整支持 **Sable**（Create: Aeronautics）。

## 前置模组

| 模组 | 要求 |
|-----------|----------|
| [NeoForge](https://neoforged.net) 21.1.93+ | ✅ 必需 |
| [Create](https://modrinth.com/mod/create) 6.0.10+ | ✅ 必需 |
| [TaCZ](https://modrinth.com/mod/tacz) 1.1.8+ | ✅ 必需 |
| [Sable Companion](https://github.com/ryanhcode/sable-companion) 1.6.0+ | 仅运行时（Sable 飞船需要） |

## 操作键位

| 操作 | 输入 |
|--------|-------|
| 放入/取出枪械 | 右键哨戒臂 |
| 附加弹药箱 | 手持弹药箱右键 |
| 聚焦瞄准目标 | 瞄准镜 + 左键目标 |
| 配置火控 | 手持剪贴板右键火控台 |
| 调整射程 | 空手在哨戒臂上滚轮 |

## 坐标系说明（Sable 飞船）

| 操作 | 坐标空间 |
|-----------|-----------------|
| 实体扫描 AABB | **世界**（从子世界投影） |
| 角度计算 | **世界**（枪口和目标都在世界空间） |
| 视线检测 clip | **子世界**（转换后执行 level.clip） |
| 子弹生成 | **世界**（FakePlayer 放置在投影后位置） |

## 开发

```bash
./gradlew build          # 编译打包
./gradlew runClient      # 启动游戏客户端
./gradlew runServer      # 启动专用服务器
```

## 版本历史

- **v1.0.0** — 正式版：Sable 兼容，坐标空间修复，全物品配方
- **v0.5.0** — Sable（Aeronautics 子世界）兼容，坐标空间修复
- **v0.4.0** — 初始 NeoForge 1.21.1 移植

## 许可

MIT © Euphy
