# Adaptive Capacity for Multimodal Transport Network Resilience to Extreme Floods

本仓库包含论文 *"Adaptive capacity for multimodal transport network resilience to extreme floods"* 中所有结果复现所需的**数据**与**代码**。

仓库由两大模块组成：

| 模块 | 路径 | 主要用途 |
| :--- | :--- | :--- |
| **simulation** | `simulation/` | 基于 MATSim 的多模式交通仿真（含南京基准场景数据集、用于构造仿真输入与后处理的 Java/Python 工具） |
| **analysis** | `analysis/` | 对仿真结果进行结构韧性、功能韧性、适应模式、适应能力、社会维度、模型验证等分析的 Python 代码 |

其他相关城市场景参见：

* Hamburg：`https://github.com/matsim-scenarios/matsim-hamburg`
* Los Angeles：`https://github.com/matsim-scenarios/matsim-los-angeles`

---

## 1. 仓库目录总览

```
adaptiveCapacityOfUrbanTransportNetwork/
├── analysis/                     # 后处理/分析全部 Python 脚本
├── simulation/                   # MATSim 仿真工程
│   ├── src/main/java/            # 全部 Java 源码（网络/需求/时刻表处理与仿真）
│   ├── scenarios/                # 输入场景数据
│   ├── target/                   # Maven 编译产物（*.class 等）
│   ├── pom.xml                   # Maven 配置（依赖 MATSim、GeoTools、GDAL、PostgreSQL 等）
│   ├── mvnw / mvnw.cmd           # Maven Wrapper 启动脚本
│   ├── LICENSE                   # MATSim 代码许可（GPL v2）
│   └── README.md                 # 子模块简版说明
├── busLinePaths.json             # 跨城/夜间公交线路几何（GeoJSON-like 数组）
├── out/                          # IntelliJ 默认的 production 输出（analysis 脚本拷贝）
└── README.md                     # 本文件
```

> 说明：
>
> * `busLinePaths.json` 由 `TransitBuilder` 等工具生成后留作存档，供后续与公交线 shapefile 对照。
> * `out/` 为 IntelliJ 「Project Structure → Modules」下的 `Production output` 路径，是 analysis 脚本的编译/拷贝目录，源码仍以 `analysis/` 为准。
> * `simulation/target/` 是 `mvn compile` 生成的 class 文件目录，已在 `.gitignore` 推荐项中，建仓时一般不纳入版本控制。

---

## 2. `simulation/` 模块

`simulation/` 是一个标准的 Maven 工程，基于 **MATSim 13/14 风格 API（pom 中默认 `2026.0-2025w38`）** ，**Java 11**，主要依赖 MATSim、GeoTools（31.3）、GDAL（3.11）、PostgreSQL JDBC、commons-csv、commons-io。

### 2.1 关键文件

| 文件 | 说明 |
| :--- | :--- |
| `simulation/pom.xml` | Maven 工程定义；声明上述依赖以及 `maven-shade-plugin`（构建可执行 fat-jar，主类为 `org.matsim.gui.MATSimGUI`）。 |
| `simulation/mvnw`、`simulation/mvnw.cmd` | Maven Wrapper（Linux/macOS 与 Windows）脚本，免安装 Maven 即可执行 `./mvnw package`。 |
| `simulation/LICENSE` | MATSim 程序代码（`src/` 下 `*.java`）采用 **GPL v2**；仿真输入/输出/分析数据采用 **CC BY 4.0**。 |
| `simulation/README.md` | 项目内嵌简版 README（主要关于导入 IntelliJ、Java 版本、许可）。 |
| `simulation/target/` | Maven 编译输出，可由 `mvn clean` 清理，无需手动维护。 |

### 2.2 源码组织 `simulation/src/main/java/`

源码以包 `org.matsim.*` 为主，并保留了一个 `gui` 包作为 MATSim GUI 启动入口。

#### 2.2.1 `gui/MATSimGUI.java`

* 功能：MATSim 官方 GUI 启动器，封装 `org.matsim.run.gui.Gui.show(...)`。
* 使用：
  ```bash
  ./mvnw -pl simulation compile
  java -cp "simulation/target/classes;$(./mvnw -pl simulation dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" gui.MATSimGUI
  ```

#### 2.2.2 `org.matsim.project/` — 仿真主入口

| 文件 | 功能 |
| :--- | :--- |
| `RunMatsimBaseline.java` | **核心仿真运行器**：加载 `config.xml/network.xml/demand.xml/transitSchedule.xml/transitVehicle.xml`，配置 `pt`（公交+地铁）与 `car` 双模式、50 轮迭代、群体规模缩放（`countsScaleFactor=100`、`flowCapFactor=0.3`）、起步时间随机种子（默认 179），最终调用 `Controler.run()`。**所有路径已在源码中以注释形式列出了南京/广州两个版本**，切换时按需启用对应路径并重新编译即可。 |

> **如何使用**
> 1. 打开 `RunMatsimBaseline.java`，把 `configFile/networkFile/plansFile/scheduleFile/vehiclesFile/outputFile` 改为待仿真场景对应的文件。
> 2. 在 IDE 中以 `main` 方式运行，或：
>    ```bash
>    ./mvnw -pl simulation package
>    java -jar simulation/target/matsim-example-project-0.0.1-SNAPSHOT.jar
>    ```

#### 2.2.3 `org.matsim.network/` — 路网构建、洪水攻击、分析

| 文件 | 功能 |
| :--- | :--- |
| `NetworkLoader.java` | 工具类：加载/写出 MATSim 网络，并从 `network.attributes` 读取 `coordinateReferenceSystem`，统一返回 `LoadResult{scenario, network, crs}`。 |
| `CreatePtNetwork.java` | 根据 `transitSchedule.xml` 把每个 `TransitStopFacility` 视为节点，相邻站点之间生成 `pt` 模式 link，得到 **Space-L 公交网络**（用于统计间接运营失效）。 |
| `BusNetworkIntegrator.java` | 基于公交线路 shapefile + 站点 shapefile，按几何方向匹配站点，自动向 MATSim network 追加 `bus` 模式链路，并支持站点到最近节点的吸附。 |
| `MetroNetworkIntegrator.java` | 地铁专用集成器，节点 ID 规则 `NDPT+POIID/自增`、链路 ID 规则 `LKPT+11位自增`，返回每条 chain 的 forward/reverse link 列表。 |
| `NetworkMerge.java` | 把「公交 Space-L 网络」与「耦合路网中 `car` / `car,bus` 模式部分」合并，输出形如开源场景的「含 PT 的耦合网络」。 |
| `SeparateCarAndPt.java` | 将耦合网络按 `modes` 解耦：只保留含 `car` 的链路并把 modes 收敛为 `car`，得到纯小汽车网络。 |
| `SeparateRoadAndSubway.java` | 把耦合网络分成「道路子网（不含 `pt`）」与「轨道子网（含 `pt` 且不含 `car`）」两套独立网络文件。 |
| `AddArtificialLinks.java` | 在原 MATSim 含人工链网络基础上，把丢失的人工连接段重新接回到残存耦合网络中，常用于含洪水的重新耦合。 |
| `DeleteArtificialLinks.java` | 工具：`run(network)` 删除并返回所有 `artificial` 模式链路，便于还原最简网络。 |
| `DuplicateLinks.java` | 为某些路段复制一份以承载公交专用道/应急车道：保留端点、调整容量与允许模式。 |
| `OSMShp2Network.java` | 把 OSM Shapefile（fclass= motorway/trunk/primary/secondary/...）映射成 MATSim 网络，按 `fclass` 写入默认车速/车道数。 |
| `ClipNetwork.java` | 按矩形范围裁剪网络（按 `minX/maxX/minY/maxY` 判定节点出界并删除相邻链路）。 |
| `NetworkAttackFlood.java` | **核心：基于洪水深度栅格（`.tif`）攻击路网**。读取 `GDAL` Band，按阈值剔除节点/链路，输出「直接失效」与「间接失效」网络 XML；可切换南京/汉堡/洛杉矶投影。 |
| `IdentifyIndirectFailures.java` | 工具函数 `identifyRoadIndirectFailures`（在剩余网络中按最大连通子图找间接失效节点）和 `identifyPtIndirectFailures`（在时刻表中找被整条线路丢失的链路）。 |
| `Network2Shapefile.java` | 把网络 XML 转成 Shapefile（含 links + polygons），便于 geopandas/osmnx 处理。 |
| `WGS2Mercator.java` | 自实现的 `CoordinateTransformation`：WGS84 经纬度 → Web Mercator 平面坐标（m）。 |
| `TransitBuilder.java` | 端到端流水线：网络加载 → 公交 SHP 集成 → 地铁集成 → 时刻表+车辆写入，全部封装为单个 main。 |
| `TransitScheduleWriter.java` | 把内存中的 `TransitSchedule` 写入标准 `transitSchedule.xml`（带 CRS/路径/站点坐标）。 |
| `TransitVehiclesWriter.java` | 增强版：为 `transitSchedule.xml` 中的每个 `Departure` 按 `lineId_routeId_depTime` 生成 `Vehicle`，输出标准 `transitVehicles.xml`。 |
| `tif/RasterDemo.java` | GDAL 读 `.tif` 洪水栅格的最小示例：打开 → 读取仿射矩阵 → 按 WGS84 经纬度查询像元值（其它 4 个网络洪水类工具都基于此模板）。 |

#### 2.2.4 `org.matsim.population/` — 出行需求生成与攻击

| 文件 | 功能 |
| :--- | :--- |
| `CreatePopulationUtil.java` | 输入 `zone2zone` OD 表 + 时间分布：基于网格质心把 OD 离散为单 agent 的 `home-work-home`、`home-leisure-home` 等出行链，输出 MATSim population。 |
| `DemandXmlGenerator.java` | 数据库/CSV 版（基于 GeoTools 与 JDBC）的需求生成器：写为 MATSim `population.xml v6`。 |
| `Zone2ZoneDemandGeneration.py` | Python 版的 OD→agent 离散化脚本，函数包括 `wgs84toWebMercator`、`calModalProb`（基于距离的分模式概率，含 car/bus/subway 等），与 Java 版 `CreatePopulationUtil` 互为参考实现。 |
| `ZoneDemandGeneration.py` | 重力模型：输入 POI + 网格 WGS84 经纬度/人口/GDP，输出网格级「交通发生量/吸引量」。 |
| `GetMercatorFromID.java` | 工具：根据网格 ID 从 CSV 查询其质心的 UTM-50N 投影坐标，缓存到静态 Map 供其他类复用。 |
| `GetRandomTime.java` | 出发时刻随机抽样（基于《北京交通发展报告 2020》累计出发概率，整点→整分钟离散）。 |
| `CleanPopulationUtils.java` | 6 类精炼工具：`deletePerson`/`savePerson`、`deleteModes`、`deleteUnselectedPlans`、`deleteAgentsNoPlan`、`minedPopulation`（精简到只保留活动+主模式）、`deleteAgentsNoActivity`。 |
| `PopulationSampling.java` | 把一份 population 按比例随机抽样（`sampleDown(population, ratio)`），写到 `.xml.gz`。 |
| `PopulationSamplingSuperComputing.java` | 超算上跑：将读入的资源路径 plan 抽样到 `Sampled_plans.xml`，便于在受限环境继续分析。 |
| `ClipPopulation.java` | 按矩形范围裁剪 population：任一活动超出范围即认为该 agent 不在范围内。 |
| `PopulationAttackFlood.java` | **核心：基于活动失效栅格（`.tif`）攻击需求**。任一起讫点落在失效像元内 → 整 agent 失效；并区分"直接/间接"两种结果（直接 = 落水，间接 = 仅失去全部有效活动）。 |
| `PopulationAttackFlood2.java` | 同上，但行为略有差异：只要出行活动无效，则 agent 后续的 trip 全部移除，而不仅去除 agent。常用于按活动等级做敏感性分析。 |

#### 2.2.5 `org.matsim.pt/` — 公共交通时刻表处理

| 文件 | 功能 |
| :--- | :--- |
| `CleanInvalidSchedule.java` | 基于网络 XML 清理时刻表：站点/线路经过的 link 若不存在则视为无效。早期版本，配合 `MyScheduleCleaner` 使用。 |
| `MyScheduleCleaner.java` | 通用版：`cleanScheduleWithNetwork(schedule, network)` 删除所有 link 不在网络中的线路。常用于 **间接运营失效** 的判定（耦合网络被剔除后，整条线路被删除）。 |
| `DeleteUnusedPtLinks.java` | 工具：在 pt 网络中移除不再被任何线路使用的 link，常与 `MyScheduleCleaner` 串联。 |
| `DecoupleBusMetro.java` | 把耦合 schedule 按线路 id 集合拆分为 `bus schedule.xml` 与 `subway schedule.xml`。 |
| `Lines2StopFacilities.java` | 把时刻表展平为 `transitLines2StopFacilities.txt`：每行一条线路，依次为经过站点的信息（便于人工核查）。 |
| `MySchedule2Shp.java` | 调用 pt2matsim 的 `Schedule2Geojson` 把时刻表导出为 GeoJSON（支持批处理阈值）。 |
| `TransitScheduleValidate.java` | 调用 pt2matsim 的 `CheckMappedSchedulePlausibility`，对 schedule 与 network 做一致性检查并输出诊断报告。 |
| `TransitScheduleDeparturesModifier.java` | 修改时刻表中发车时间/频率（如按洪水期间削峰）。 |
| `TransitStatistics.java` | 统计线路、路径、站点数量，是 `SimpleAnalyzer` 之外的快速统计脚本。 |

#### 2.2.6 `org.matsim.analysis/` — 结果分析

**`org.matsim.analysis.network/`**

| 文件 | 功能 |
| :--- | :--- |
| `SimpleAnalyzer.java` | 统计网络：按 `Set<String>`（如 `{car}`/`{car,bus}`/`{pt}`）分组统计链路数量，是其他分析器的基础统计函数。 |
| `ConnectivityTest.java` | 测试一组网络在被洪水攻击前后，路网（最大连通子图）覆盖的 link 数变化。 |
| `FloodDamageAnalysis4Nanjing.java` | 把 baseline 与 `threshold[m]` 洪水场景下的「道路直接/间接失效网络」、「公交 Space-L 直接/间接运营失效网络」一起统计，写到 CSV。 |
| `NetworkDamageAnalysis4OpenScenario.java` | 汉堡/洛杉矶版 `FloodDamageAnalysis4Nanjing`：相同指标，路径可切换。 |

**`org.matsim.analysis.population/`**

| 文件 | 功能 |
| :--- | :--- |
| `SimpleAnalyzer.java` | 人口统计：活动/子群/出行模式计数等通用函数。 |
| `ExtractSubpopulation.java` | 按属性值（如 `person`/`freight`）提取并写出只含目标子群的 population。 |
| `ExtractPopulation.java` / `ExtractPopulationTest.java` | 以 baseline 抽样得到的 `Sampled_plans` 为索引，从各洪水场景 plans 中抽取同一组 agent，便于横向对比。 |
| `PopulationSamplingSuperComputing.java` | 与上一项配对的「在超算平台做 plans 抽样」脚本（亦在该目录下重复收录，避免跨包引用）。 |
| `DeleteWrongPlan.java` | 删除明显错误的 plan（如不合法时长），保留合理样本。 |
| `PrintPlan.java` | 输出指定 person 的活动/出行结构到 XML/控制台，便于人工排查。 |
| `PrintTravelRoute.java` | 打印所有 trip 的每个 leg 的 `LegMode/LegRoute/LegStartLink/LegEndLink/...`（car leg 可拿到经过的全部 link，pt leg 仅给出首末 link）。 |
| `PopDamageAnalysis4OpenScenario.java` | 统计开源场景汉堡/南京需求在不同洪水阈值下的「agent 失效」组成（直接 vs 间接），写到 CSV。 |

**`org.matsim.analysis.schedule/`**

| 文件 | 功能 |
| :--- | :--- |
| `SimpleAnalyzer.java` | 时刻表统计：按 transportMode 汇总 route 与 stop 的数量。 |
| `ScheduleDamageAnalysis4OpenScenario.java` | 扫描 baseline 与各洪水阈值时刻表，统计剩余 transitStop / transitLine / transitRoute 数量并写到 CSV。 |

### 2.3 场景数据 `simulation/scenarios/`

```
scenarios/
├── equil/                                 # MATSim 官方最小 equil 测试场景
│   ├── config.xml
│   ├── network.xml
│   ├── plans100.xml
│   └── counts100.xml
└── nanjingBaseline/                       # 南京多模式基准场景
    ├── config.xml                         # 主仿真配置（含南京评分参数、线程数等）
    ├── config.Fa.xml                      # 洪水场景禁"模式切换/路径切换"
    ├── config.Fb.xml                      # 洪水场景允许路径切换但禁模式切换
    ├── network.xml                        # 道路 + 公交 Space-L 耦合网络
    ├── demand.xml                         # 出行需求（agent 级 plans）
    ├── transitSchedule.xml                # 公交 + 地铁时刻表
    ├── transitVehicle.xml                 # 公交 + 地铁车辆
    └── scenarios/
        └── nanjingBaselineOutput/         # 一次仿真的输出
            ├── ITERS/                     # 各迭代快照（plans/events/legs/trips CSV 等）
            ├── tmp/                       # 仿真中间缓存
            ├── output_plans.xml.gz        # 最终选中 plans
            ├── output_events.xml.gz       # 事件流
            ├── output_legs.csv.gz         # leg 级统计
            ├── output_trips.csv.gz        # trip 级统计
            ├── output_persons.csv.gz
            ├── output_network.xml.gz
            ├── output_counts.xml.gz
            ├── output_transitSchedule.xml.gz
            ├── output_transitVehicles.xml.gz
            ├── logfile.log / logfileWarningsErrors.log
            ├── modestats.txt / pkm_modestats.txt / scorestats.txt
            ├── traveldistancestats.txt
            ├── *.png                       # 自动生成的统计图
            ├── *.kmz                        # GoogleEarth 可视化
            └── modules.dot                 # MATSim 控制器的模块依赖图
```

> **洪水实验步骤**（典型工作流）
> 1. 用 `NetworkAttackFlood`、`PopulationAttackFlood` 在不同阈值下生成「结构破坏 + 需求破坏」XML。
> 2. 用 `CleanInvalidSchedule` / `MyScheduleCleaner` 生成间接运营失效时刻表。
> 3. 用 `AddArtificialLinks` / `NetworkMerge` 重新把人工链路接回到残存耦合网络。
> 4. 必要时按 `config.Fa.xml` / `config.Fb.xml` 重新跑一遍仿真（前者禁止切换模式，后者禁止切换路径）。
> 5. 把仿真输出丢给 `analysis/` 模块做后续统计。

---

## 3. `analysis/` 模块

`analysis/` 目录下共有 11 个 Python 脚本，按论文章节排序。所有脚本在运行前都需要：

1. 仿真已完成，并把 `output_trips.csv.gz`、`output_legs.csv.gz`、`output_transitSchedule.xml.gz` 与矢量底图等放入本地路径；
2. 修改每个脚本开头的 `"Your folder path"` / `"Your file path"` 占位符；
3. 部分脚本依赖 `pyproj`、`geopandas`、`seaborn`、`scipy`、`statsmodels`、`pyecharts`、`shapely`、`snapshot_selenium` 等。

### 3.1 数据预处理

| 脚本 | 功能 | 输入 | 输出 |
| :--- | :--- | :--- | :--- |
| `0. Data preprocessing.py` | 读取 `output_trips.csv.gz`、投影到 WGS84、按模式剔除纯步行/骑行的 trip、清理零距离/零时长/距离<欧氏距离的异常样本、统一模式名（`car`/`pt`）并统计 `pt_times`。 | 各仿真场景下的 `output_trips.csv.gz` | `output_trips_wgs84.csv.gz`、`output_trips_wgs84_cleaned.csv` |

> 三类城市的投影常量已在脚本注释里给出：南京 `EPSG:32650`、汉堡 `EPSG:25832`、洛杉矶 `EPSG:3310`。

### 3.2 第 1 章 韧性评估

| 脚本 | 功能 |
| :--- | :--- |
| `1.1 Structural resilience.py` | 绘制**结构韧性**图：以道路+PT 耦合网络 SHP 与 PT 单独 SHP 为底图，按洪水阈值可视化「直接失效 / 间接失效 / 残余链路」。输出 PDF。 |
| `1.2 Functional resilience.py` | 绘制**功能韧性**图：(a) 各阈值下 `car`/`pt` 的行程时间分布（带中位数连线的箱线图）；(b) `passable_trips` 比例；(c) `pt` 换乘次数；(d) `car`/`pt` 模式分担率。 |

### 3.3 第 2 章 适应行为

| 脚本 | 功能 |
| :--- | :--- |
| `2.1 Adaptation pattern.py` | **行程模式适应**：把 baseline 与各洪水场景的 trip 表做 `merge(how='outer')`，区分"保持原模式 / 切换模式 / 行程消失 / 新增行程"四类，用 `pyecharts.Sankey` 出桑基图。 |
| `2.2 Universal mode shift.py` | **网格层级普适性**：按 1 km 网格统计每个网格中 `car` link 与 `pt` link 的数量，用回归分析「网格尺度上的适应能力」与洪水阈值的关系。 |

### 3.4 第 3 章 多模式协同与竞争

| 脚本 | 功能 |
| :--- | :--- |
| `3. Complementation and competition.py` | 把 leg 级 mode（`car`/`bus`/`subway`/walk）合并回 trip 级；结合城市 1 km 网格与 leg 起点/终点，刻画"双模式互补 vs 竞争"的时空分布（空间-时间双维度，叠加 NetworkX 网格拓扑）。 |

### 3.5 第 4 章 适应能力与干预

| 脚本 | 功能 |
| :--- | :--- |
| `4.1 Adaptive capacity.py` | **城市间适应能力对比**：从 baseline 输入 plans 中得到 `tripId`，再与不同洪水阈值下的 `tripIdFromPopulationTxx` 求差得到**行程消失量**；再与"行程时间 > 300 分钟"等组合，得到 **不可访问、不可接受、不可持续** 三类适应能力指标。覆盖南京/汉堡/洛杉矶三城市。 |
| `4.2 Intervention.py` | **PT 票价/补贴干预场景**：在不同 PT 补贴强度下拟合 `pt_split` 与 `passable_prop` 的回归关系，并出图；同时分析多随机种子下结果的稳定性。 |

### 3.6 第 5 章 适应能力的社会维度

| 脚本 | 功能 |
| :--- | :--- |
| `5. Social dimenssion of adaptive capacity.py` | 读取 `person attributes from la 10pct.txt`（家庭 ID、教育、收入、种族、车辆数、年龄、性别等），清理 `NAN` 后与 trip 表 join，分析不同社会-经济群体在洪水场景下的适应能力差异（公平性视角）。 |

### 3.7 第 6 章 模型验证

| 脚本 | 功能 |
| :--- | :--- |
| `6.1 model validation for Nanjing.py` | **南京模型验证**：以高德地图 OD 数据为参照源，解析 CSV → 计算 Geohash/网格流量 → KS 检验 + 距离分布对比，验证仿真模型生成的 OD 与经验数据的统计一致性。 |
| `6.2 model validation for LA.py` | **洛杉矶模型验证**：基于 SafeGraph/POI 等开源 OD 多日合并 → 限定到 LA County（`geoid = "06037"`）→ 计算欧氏距离 → 与仿真结果对比日级别流量与距离分布。 |

---

## 4. 一键复现工作流（典型步骤）

1. **准备场景**
   * 直接使用 `simulation/scenarios/nanjingBaseline/` 作为 MATSim 输入；如需测试其它场景可参考 `equil/`。
   * 在 `RunMatsimBaseline.java` 里修改路径并执行 `Controler.run()`，把输出放到 `scenarios/nanjingBaselineOutput/`，也可以用 `mvn package` 后命令行运行。

2. **生成洪水场景**
   * 使用 `NetworkAttackFlood`（多阈值）生成各 `threshold[m]` 的网络 XML。
   * 使用 `PopulationAttackFlood` 生成需求 XML。
   * 使用 `MyScheduleCleaner` + `CleanInvalidSchedule` 生成时刻表 XML。
   * 使用 `AddArtificialLinks` 或 `NetworkMerge` 重新耦合网络。

3. **再次仿真（洪水场景）**
   * 切换至 `config.Fa.xml` 或 `config.Fb.xml`（分别禁止模式切换或路径切换）进行 `RunMatsimBaseline`。
   * 同一组洪水场景下，按不同随机种子多次重复实验。

4. **后处理与分析**
   * 跑 `analysis/0. Data preprocessing.py` 清洗 trip 输出。
   * 依次跑 `1.1 / 1.2 / 2.1 / 2.2 / 3 / 4.1 / 4.2 / 5 / 6.1 / 6.2.py` 得到所有图表与统计表。

---

## 5. 依赖与运行要求

### 5.1 simulation（Java）

* **JDK 11**（已在 `pom.xml` 中固定 `<maven.compiler.source/target>11`）。
* **Maven 3.6+**（推荐使用 `mvnw`）。
* **GDAL 3.11.0**（Java bindings，pom 中已声明）。运行时将 `gdal-bindir` 指向 `src/main/resources/gdal/win32`（已写在配置中）。
* **GeoTools 31.3**（已统一版本）。
* **PostgreSQL JDBC**（如需直连 PG 数据库）。
* 大量 `.tif`（洪水栅格）原始数据**不在仓库内**，需另行下载；脚本顶部路径按文件实际位置调整。

### 5.2 analysis（Python）

* Python ≥ 3.9（已在 `analysis/environment.yml` 固定为 `3.13`）。
* 脚本实际 import 到的库：

  ```text
  pyproj >= 3.4
  geopandas
  pandas
  numpy
  matplotlib
  seaborn
  scipy
  statsmodels
  shapely
  rasterio / osgeo.gdal   # 读 tif 洪水栅格（4.1 / 2.2）
  pyecharts                # 桑基图（2.1）
  snapshot_selenium        # 把 echarts 渲染为 PNG/PDF（2.1）
  ```
* 由于脚本内的路径以 `r"Your folder path"` 占位，需要把仿真输出位置、原始数据 SHP/TIF、城市网格 shapefile 等替换为本地绝对路径。
* `analysis/6.1 model validation for Nanjing.py` 会 `import CoordinatesConverter as cc`，这是一个**仓库外的本地辅助模块**，请把它放到 `analysis/` 或任意能被解释器找到的目录。

#### 5.2.1 一键安装 Conda 环境（推荐）

Windows / macOS / Linux 通用：

```bash
conda env create -f analysis/environment.yml
conda activate matsim-py
```

> **为何选 conda 而非纯 pip？**`osgeo.gdal`（Windows / macOS 上）依赖一组 C/C++ 二进制（proj、geos、sqlite），pip 源里 wheel 与本地 libgdal 版本很容易冲突。conda-forge 一次性提供 `geopandas / shapely / pyproj / gdal / rasterio` 的二进制一致版本，是 MATSim 后处理脚本最省心的方案。

#### 5.2.2 仅使用 pip（次选）

```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# Linux/macOS:
source .venv/bin/activate

pip install -r analysis/requirements.txt
# 然后再单独解决 GDAL：
#   1) conda install -c conda-forge gdal   ← 推荐
#   2) 或从 https://www.lfd.uci.edu/~gohlke/pythonlibs/#gdal  下载与 Python 版本对应的 wheel
```

#### 5.2.3 在 IntelliJ IDEA 中联调 Java + Python

工程已按 **Java 根模块 + Maven 子模块 + Python 子模块** 三段式拆好：

```
adaptiveCapacityOfUrbanTransportNetwork (项目)
├── .idea/adaptiveCapacityOfUrbanTransportNetwork.iml   ← Java 根模块（占位）
├── simulation/simulation.iml                          ← MATSim Java 子模块（Maven）
└── analysis/analysis.iml                               ← Python 后处理子模块
```

* **Python 插件**：`Settings → Plugins → Marketplace` 搜 `Python` 安装（Community / Ultimate 自带）。
* **Conda 解释器**：`Settings → Project Structure → SDKs → + → Add Python SDK → Conda Environment → Existing environment → ...\anaconda3\envs\matsim-py\python.exe`。在 `Project Structure → Project SDK` 把项目默认 SDK 设为该解释器。
* **运行单个脚本**：`Run → Edit Configurations → + → Python`，`Script path` 指向 `analysis/<某脚本>.py`，`Working directory` 用 `$MODULE_WORKING_DIR$`，`Python interpreter` 选 `Project Default (matsim-py)`。需要 GDAL 时在 `Environment variables` 加 `PROJ_LIB=...\envs\matsim-py\Library\share\proj`（Windows）。
* **`out/` 污染问题**：根 Java 模块已通过 `<excludeFolder url="file://$MODULE_DIR$/out" />` 排除 `out/` 目录，新增 `analysis/*.py` 时不会再被同步到 `out/production/...`。
* **依赖管理**：`Settings → Python Interpreter → Python integrated tools → Requirements.txt` 全部关掉，本工程已自带 `analysis/requirements.txt`，不再需要 IntelliJ 弹窗自动生成。

---

## 6. 许可（License）

* `simulation/src/main/java/**/*.java` — **GNU GPL v2**（参考 `simulation/LICENSE`）。
* `simulation/scenarios/**`、`simulation/output/**`、`analysis/**` 中的数据与可视化 — **Creative Commons Attribution 4.0 International (CC BY 4.0)**。
* 第三方原始数据（如 `original-input-data`、高德 OD 等）遵循各自版权声明，使用前请单独确认。

---

## 7. 引用

如本仓库对您的研究有帮助，请引用：

> *"Adaptive capacity for multimodal transport network resilience to extreme floods."*

并附上链接：

* Hamburg 场景：<https://github.com/matsim-scenarios/matsim-hamburg>
* Los Angeles 场景：<https://github.com/matsim-scenarios/matsim-los-angeles>

---

## 8. 当前进行中的工作（与论文无关）

> ⚠️ 本节是 2026-09 期间的**一次性扩展工作**——用 MATSim 仿真 + 手机信令数据校准广州公交分担率（目标 car=64%, pt=36%）。
> 这项工作**不在论文主线**（论文主线是第 1-7 节介绍的"洪水韧性"），不影响论文复现。

### 8.1 工作进展指针

接手本工作请直接阅读：[`广州公交校准-工作交接.md`](./广州公交校准-工作交接.md)

该文档包含：
- 当前状态（断裂率 88%，待决策是否重跑仿真）
- 关键文件清单（区分开源默认 vs 我们改过）
- 标准 SOP（编译 → TransitBuilder → RunMatsimBaseline）
- 已知坑 & 调试技巧

### 8.2 完整修改历史

详见：[`仿真修改记录.md`](./仿真修改记录.md)（8 节，每轮仿真数据点 + 代码片段）

### 8.3 一句话总结

5 轮迭代后最好结果：car=77.23%, pt=22.52%（pt 远低于 36% 目标）。根因：96% 公交线路组的虚拟 link 存在节点断裂。
