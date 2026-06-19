# Shadow Clash

一个使用 JavaFX Canvas 制作的横版 2D 格斗小游戏原型，包含双人本地对战和玩家打电脑两种模式。项目不复用《死神 vs 火影》的角色、音乐或原作素材，只参考横版格斗的操作节奏和表现方式。

## 功能

- 精美菜单、模式选择、角色选择、战斗 HUD、结算界面
- 双人同机对战
- 玩家 VS 电脑 AI
- 两名可选角色
- 普通攻击、闪避、两个技能、大招
- 血条、能量条、技能冷却提示
- 碰撞盒伤害判定、击退、硬直、防御
- 击中特效、格挡特效、受击闪白、震屏、浮动伤害数字
- 程序化 BGM 和攻击/受击/技能音效
- 使用网上下载并整理后的 CC0 透明 PNG 角色与背景素材

## 运行方式

需要 JDK 17 或更高版本，以及 JavaFX SDK。

当前工作区如果存在 `.jdk` 目录，`run.bat` 会自动优先使用里面的便携 JDK；否则会使用系统 PATH 里的 `java` / `javac`。

如果没有 `.javafx` 目录，先执行：

```bat
setup-javafx.bat
```

```bat
run.bat
```

或者手动执行：

```bat
javac --module-path .javafx\lib --add-modules javafx.controls,javafx.swing -encoding UTF-8 -d out src\main\java\com\shadowclash\*.java
java -Dprism.order=sw --module-path .javafx\lib --add-modules javafx.controls,javafx.swing -cp out com.shadowclash.ShadowClashFX
```

Smoke test：

```bat
build.bat
.jdk\bin\javac.exe --module-path .javafx\lib --add-modules javafx.controls,javafx.swing -encoding UTF-8 -cp out -d out src\test\java\com\shadowclash\SmokeTest.java
.jdk\bin\java.exe --module-path .javafx\lib --add-modules javafx.controls,javafx.swing -cp out com.shadowclash.SmokeTest
```

## 操作

菜单：

- 方向键或 W/S：切换选项
- Enter 或 Space：确认
- Esc：返回主菜单

玩家 1：

- A / D：左右移动
- W：跳跃
- S：防御
- J：普通攻击
- K：闪避
- U：技能 1
- I：技能 2
- O：大招

玩家 2：

- 左 / 右方向键：左右移动
- 上方向键：跳跃
- 下方向键：防御
- 数字键 1：普通攻击
- 数字键 2：闪避
- 数字键 4：技能 1
- 数字键 5：技能 2
- 数字键 6：大招

## 目录结构

```text
assets/
  processed/       游戏实际加载的素材
  source/          本地原始下载素材包，仓库不需要提交
src/main/java/
  com/shadowclash/ShadowClashFX.java     JavaFX 主入口
  com/shadowclash/ShadowClash.java
build.bat
run.bat
setup-javafx.bat
CREDITS.md
```

## 后续可扩展方向

- 把 `ShadowClash.java` 拆分成 screen、entity、combat、audio、ai 等包
- 增加更多角色和地图
- 为 AI 增加难度选择
- 增加暂停菜单和设置界面
- 用 JSON 配置角色技能数值
- 将程序化音频替换成授权清楚的 WAV/MP3 文件
