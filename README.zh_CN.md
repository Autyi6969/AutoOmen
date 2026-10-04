<p align="center"><img src="https://raw.githubusercontent.com/Autyism/AutoOmen/master/src/main/resources/assets/autoomen/icon.png" width="128" alt="icon"></p>
<h1 align="center">Auto Omen</h1>
<p align="center">袭击塔挂机神器：在刚好的时机替你喝下一瓶不祥之瓶，袭击一场接一场不停。</p>

[English](https://github.com/Autyism/AutoOmen/blob/master/README.md) | **简体中文**

![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-62B47A) ![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4) ![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue)

Auto Omen 是 Minecraft 26.2 的纯客户端 Fabric 小模组。你在袭击塔挂机，它盯着袭击的 boss 栏和你身上的效果，一到能开下一场的时候，就从背包里拿一瓶不祥之瓶喝掉，再把剑放回你手上。

## 功能

- **时机刚好才喝。** 身上没有不祥之兆、也没有袭击之兆时才喝，不会把瓶子浪费在已经有的兆头上。
- **两档时机。**
  - **袭击结束后**（默认）：袭击的 boss 栏消失后再喝。
  - **胜利时就喝**：boss 栏一显示"袭击 - 胜利"就喝。旧袭击一被移除新袭击就开始，每场大约快 30 秒，而且这瓶绝不会被并进旧袭击。
- **瓶子放哪都行。** 快捷栏、背包（用普通的物品栏换位操作换到空的快捷栏格子）、副手都可以。喝完手上回到原来那格。
- **不会乱点方块。** 直接"使用手里的物品"，你面前的按钮、门、床都不会被点到。
- **瓶子优先级。** 先喝最低级、先喝最高级，或者只喝某一个等级（I~V）。
- **血量限制。** 血量低于你设的值就不喝。
- **聊天栏提示**：每喝一瓶提示一行，还剩几瓶（可以关）。
- **安全暂停。** 如果连续两瓶都没开出袭击（关了袭击规则、人不在村子里……），模组会自己暂停并提醒你，不会把瓶子全喝光。
- **开关键**（默认不绑定），Mod Menu 里有设置界面。
- 英文、简体中文。

## 到底什么时候喝才不浪费？

袭击之兆到期时，如果旧袭击还在打（或者还在胜利庆祝），这瓶会被并进旧袭击，不会开新的。旧袭击在胜利 30 秒后才被移除，而袭击之兆正好持续 30 秒，所以只要在"胜利"字样出现之后再喝，就一定会开一场新袭击。完整说明（对照 26.2 源码）见 [docs/MECHANICS.md](https://github.com/Autyism/AutoOmen/blob/master/docs/MECHANICS.md)。

## 需要

- Minecraft 26.2、Fabric Loader 0.19.5 或更新、Fabric API
- Mod Menu（可选，用来打开设置）
- 纯客户端。单人档可用；挂机位置要在袭击中心 96 格以内，游戏才会给你显示袭击的 boss 栏。

## 许可证

GPL-3.0-only，见 [LICENSE](LICENSE)。

## 自己构建

Gradle 需要在 Java 25 上运行（Fabric Loom 1.18）：

```
./gradlew build -Dorg.gradle.java.home=<JDK 25 的路径>
```

作者：Autyism
