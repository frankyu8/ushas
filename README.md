# Ushas

## 介绍
*
  Ushas 是一款在spark基础上进行封装，强化数据血缘治理的组件。传统数据治理中针对spark的表级别血缘判断虽然能一定程度上解决数据的依赖关系，但是对于精确到字段之间的关系识别则显得捉襟见肘。开发此组件的用意是为了能够加强spark在列级血缘上的追踪优势。shark代表了我们追求的不仅仅是简单的判断，而是能够精确地捕捉血缘

## 知识铺垫
* ### dataset中的逻辑计划实现
  Ushas 主要在spark-sql-catalyst和spark-sql-hive模块进行了修改，catalyst主要是负责spark在数据处理中的关系依赖管理，其中对于普通的dataset会通过如下代码，将逻辑计划处理嵌入对象内：
  ```
  Dataset.ofRows(sparkSession, logicalPlan)
  ```
* ### sql中逻辑计划实现（Parser分析）
  而spark2.0版本以上对于spark-sql的支持则是通过Antlr4进行语法解析，生成语法树，然后通过深度遍历的方式将Unresolved的语法信息处理为resolved信息。
  每条spark的sql，都会预先通过SparkSqlParser执行parse，parse添加了antlr4需要的词法以及处理器，然后生成逻辑计划：
  https://github.com/frankyu8/ushas/blob/ee36eac54b758e39689c7e2c51ea6f3aa5c27555/sql/core/src/main/scala/org/apache/spark/sql/SparkSession.scala#L641-L646

  parsePlan的内部会执行sql转化logicalplan的操作
  https://github.com/frankyu8/ushas/blob/ee36eac54b758e39689c7e2c51ea6f3aa5c27555/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/parser/ParseDriver.scala#L69-L76

* ### Analyzer分析
  ofRows中会触发Analyzer对于逻辑计划的解析，会调用Analyzer里面的batches进行UnresolveLogicalplan 到ResolveLogicalplan的转化（有则转化，无则跳过）
  https://github.com/frankyu8/ushas/blob/ee36eac54b758e39689c7e2c51ea6f3aa5c27555/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/rules/RuleExecutor.scala#L72-L80

  Analyzer的具体识别规则如下:
  https://github.com/frankyu8/ushas/blob/a7066a67ed9c1ad9db6078d68cfff8d28cce6bd4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L152-L214

  Analyzer中的一个batch具体的解析方式为采取后序遍历或者前序遍历，对每个嵌套逻辑计划进行解析
  ```
  def apply(plan: LogicalPlan): LogicalPlan = plan resolveOperatorsUp 
  ```
    https://github.com/frankyu8/ushas/blob/a7066a67ed9c1ad9db6078d68cfff8d28cce6bd4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/AnalysisHelper.scala#L85-L104

## 我们做的事
* ### 让logicalplan具备列级解析的能力
  我们为了让logicplan能够有列级血缘的识别能力，首先对逻辑计划的抽象类进行了修改（这里为了不影响logicplan的其他功能，所以进行了额外trait的添加），这样在Analyzer中新添列级血缘解析规则，所有影响因素都控制在额外的trait内，不会影响spark本身的正常逻辑计划解析
  https://github.com/frankyu8/ushas/blob/a7066a67ed9c1ad9db6078d68cfff8d28cce6bd4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/LogicalPlan.scala#L30-L36
* ### trait怎么进行工作的
  这里[trait]LineageHelper中携带的属性包括_lineageResolved(是否被Rule解析)，childrenLineageResolved用于递归判断所有的子逻辑计划是否已经被Rule解析过，markLineageResolved用来标注当前逻辑计划解析成功
  https://github.com/frankyu8/ushas/blob/a46317df9396161a257a2388938289926ff7a46a/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/lineageCollect/LineageHelper.scala#L24-L45
  
  所有的列级对象都会被lineageChildren 容器进行接手。

* ### 列级对象存在的合理性
  逻辑计划，其实是针对算子进行操作的，并没有对算子里面的涉及成员进行操作，比如select a,b,c  from table 那么这里只会生成一个RelationLogicplan 和 ProjectLogicplan, 其中 ProjectLogicplan里面对应的projectList（即所有的字段expression）才是我们关心的列级血缘对象，为了不在projectList里面直接对expression进行操作，所以我们预先定义了列级对象（和expression一一对应），简单的记录了每个expression里面我们所关心的属性[extend treeNode]，然后放入lineageChildren中。只要确保Analyzer工作时，每次深度遍历，在不参与计算的节点将lineageChildren进行复制，携带到上层节点，在对应需要操作的节点进行关系的判断，即可保证列级字段的正确解析。
  
  列级对象的子类包括ExpressionColumn,RelationColumn以及UnionColumn，ExpressionColumn主要记录的是Project逻辑计划里面的expression，RelationColumn主要记录的是LeafNode里面的从属关系,UnionColumn主要是记录了特殊的列字段关系，因为需要识别到相应的right lineageChildren信息。
  https://github.com/frankyu8/ushas/tree/main/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/lineage

* ### rule是怎么进行工作的
  因为logicplan是封装在所有的df对象内的，所以每次的df对象进行方法的操作，都会force 逻辑计划进行Analyzer的解析。我们在Analyzer的规则里面加上了自己的列级血缘判断规则
https://github.com/frankyu8/ushas/blob/a46317df9396161a257a2388938289926ff7a46a/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L211-L212

  我们新增了两个解析规则，一个是针对Relation的解析规则（即字段血缘的叶子节点判断），一个是针对Expression的解析规则（即字段血缘的所有中间关系判断）

  Relation的解析规则：
  https://github.com/frankyu8/ushas/blob/796fc00b72ed64b6e1f5b3b0544b6b765eadc327/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveLineage.scala#L120
  
  这里的Relation为简单的从属关系判断，只是记录了每个字段的attribute，并没有去逻辑计划中寻找catalog

  Expression的解析规则：
  https://github.com/frankyu8/ushas/blob/796fc00b72ed64b6e1f5b3b0544b6b765eadc327/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveLineage.scala#L29
  
  这里的Expression的解析用的是最为基础的map寻址，即上层字段的exprid和下层字段的exprid进行匹配，若匹配中则进行绑定（借用exprid唯一的特性）

* ### hive relation的识别
  针对于hive，目前的开发是拿取了所有的hive信息。sparksession若是启动了enablehive，默认为重写Analyzer，所以我们为了添加对于hive数据源的支持，在Analyzer基类里面新添了tailResolutionRules，并且在继承的analyzer里面对其进行重写
  
  基类的Analyzer为
  
  https://github.com/frankyu8/ushas/blob/30faef47cbab44293314faf6638d2594cd8af62b/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L149-L150
  
  在 hive 的 sessionStatebuilder 里面重写 Analyzer
  
  https://github.com/frankyu8/ushas/blob/30faef47cbab44293314faf6638d2594cd8af62b/sql/hive/src/main/scala/org/apache/spark/sql/hive/HiveSessionStateBuilder.scala#L69-L94

## 软件架构
* ### [module]assembly
  assembly模块是为了能够更加方便的获取打包内容，这里移植了spark的原生代码，可以一键自动化打包，在target/scala目录下获取所有jar包
* ### [module]dev
  dev模块是为了配置checkstyle的代码规范检测，spark有内置的scala代码规范要求，我们这里也沿用了他的所有要求，输出目录为target/checkstyle-output.xml
* ### [module]examples
  example模块是为了提供列级血缘的应用范例
* ### [directory]sql
  sql里面包含所有的spark catalyst解析，列级血缘的主要工作都集中在sql包含的三个模块上


## 安装教程


1.  idea maven 对spark-catalyst module 进行Gnerated Source Code 生成sqlbase.g4的语法树文件

2.  在跑样例文件时，先设置参数 -DLocal ，再设置 Include with provided scope，用默认本地形式和本地包运行spark

3.  样例文件位置  examples/src/main/scala/org/apache/spark/examples/lineage/SparkLineageExample.scala

4.  打包时，如果需要添加hive的插件支持，需要在spark profile中勾选hive

5.  因为本身就是spark的项目中进行的分离，所以只需要将  spark-hive_2.12-3.1.2.jar，spark-catalyst_2.12-3.1.2.jar 进行替换，即可完成列级血缘的快速部署


## 效果展示



1.  准备样例sql ： select * from (select substr(a+1,0,1) as c,a+3 as d  from (select 1 as a,2 as b))

2.  样例输出：

```
c#2
+- c#2
   +- Alias ( substring(cast((a#0 + 1) as string), 0, 1) AS c#2 )
      +- a#0
         +- Alias ( 1 AS a#0 )
```

3.  在spark-shell中如何查看列级血缘（API方法）
    
    df.queryExecution.analyzed.lineageChildren(0).treeString

4.  在pyspark中如何查看列级血缘（API方法）
    
    df._jdf.queryExecution().analyzed().lineageChildren().apply(0).treeString()

## 你可以做的事

1. 优化现有的代码结构，目前的项目代码结构和spark2.4的结构一致，可以对模块进行针对性的修改，将主要代码集中在一起

2. 添加新的模块，目前只提供了spark列级血缘的解析方式，但是对血缘的自动存储和展示这块完全是留给commiter的一片空白区域

3. 现有的rule寻找逻辑优化，目前的寻找对应列关系主要是依托了exprid的全局唯一性，这里因为是遍历寻找，所以会增加耗时，看是否可以进行优化

4. spark目前是靠UnresolveLogicplan 替换成 resolveLogicplan进行resolve标记，而我们是通过简单的在逻辑计划的lineageresolve中标记为True，并没有给一个规范的样例类，这块若要优化工程量非常大

5. example中有 通过spark 插件化进行注入的样例，本是希望能够尽少的动源码，在外部进行Rule规则的植入，但是结果不尽如人意，可以看看如何进行插件化的配置

6. 目前是对hive的数据源进行了寻址，也就是说目前的列级血缘可以对hive 的数据源进行准确的Catalog识别，但是别的外接数据源没有做任何的定义，这块可以由commiter进行丰富

## 参与贡献

1.  Fork 本仓库

2.  新建 Feat_xxx 分支

3.  提交代码

4.  新建 Pull Request

5.  帮助

    如何进行issue 和 pr 的关联
    
    https://docs.github.com/cn/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue
    
    如何配置 checkstyle 代码检查
    
    https://blog.csdn.net/qq_31424825/article/details/100050445

## 关于

1. 为什么选择将项目进行裁剪，而不是将整个spark代码进行迁移？

  目前的需求是支持列级血缘，Ushas也旨在先将列级血缘进行处理和完善，之后不排除会做更多的优化和迭代，逐步的扩大项目架构。
  为了精简化打包，快速纳入使用，一次打包只需要5min时间
 
