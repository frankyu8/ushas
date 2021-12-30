# Ushas

## 介绍

Ushas 是一款在spark基础上进行封装，强化数据血缘治理的组件。传统数据治理中针对spark的表级别血缘判断虽然能一定程度上解决数据的依赖关系，但是对于精确到字段之间的关系识别则显得捉襟见肘。开发此组件的用意是为了能够加强spark在列级血缘上的追踪优势。shark代表了我们追求的不仅仅是简单的判断，而是能够精确地捕捉血缘

## 知识铺垫
### dataset中的逻辑计划实现
Ushas 主要在spark-sql-catalyst和spark-sql-hive模块进行了修改，catalyst主要是负责spark在数据处理中的关系依赖管理，其中对于普通的dataset会通过如下代码，将逻辑计划处理嵌入对象内：
```
Dataset.ofRows(sparkSession, logicalPlan)
```
### sql中逻辑计划实现（Parser分析）
而spark2.0版本以上对于spark-sql的支持则是通过Antlr4进行语法解析，生成语法树，然后通过深度遍历的方式将Unresolved的语法信息处理为resolved信息。
每条spark的sql，都会预先通过SparkSqlParser执行parse，parse添加了antlr4需要的词法以及处理器，然后生成逻辑计划：
https://github.com/frankyu8/ushas/blob/ee36eac54b758e39689c7e2c51ea6f3aa5c27555/sql/core/src/main/scala/org/apache/spark/sql/SparkSession.scala#L641-L646

parsePlan的内部会执行sql转化logicalplan的操作
https://github.com/frankyu8/ushas/blob/ee36eac54b758e39689c7e2c51ea6f3aa5c27555/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/parser/ParseDriver.scala#L69-L76

### Analyzer分析
ofRows中会触发Analyzer对于逻辑计划的解析，会调用Analyzer里面的batches进行UnresolveLogicalplan 到ResolveLogicalplan的转化（有则转化，无则跳过）
https://github.com/frankyu8/ushas/blob/ee36eac54b758e39689c7e2c51ea6f3aa5c27555/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/rules/RuleExecutor.scala#L72-L80

值得注意的是，这里生成的逻辑计划是未解析过的，即可能有未识别的数据表名，或者未识别的函数、字段名。
spark会通过Dataset.ofRows的方法中通过调用queryExecution的Analyzer进行符合特定规则的解析(这里采用了深度遍历的形式，对语法树解析，resolveOperatorsUp/Down)，来实现命名识别，方法进入如下：
可以通过 df.queryExecution.logical来查看UnresolvedLogicalPlan，通过df.queryExecution.analyzed查看解析后的ResolvedLogicalPlan。
Analyzer的具体识别规则如下:
https://github.com/frankyu8/ushas/blob/a7066a67ed9c1ad9db6078d68cfff8d28cce6bd4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L152-L214

## 我们做的事
### 让logicalplan具备列级解析的能力
我们为了让logicplan能够有列级血缘的识别能力，首先对逻辑计划的抽象类进行了修改（这里为了不影响logicplan的其他功能，所以进行了额外trait的添加），这样在Analyzer中新添列级血缘解析规则，所有的影响因素都控制在额外的trait内，不会影响spark本身的正常逻辑计划解析
https://github.com/frankyu8/ushas/blob/a7066a67ed9c1ad9db6078d68cfff8d28cce6bd4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/LogicalPlan.scala#L30-L36
### trait怎么进行工作的
这里[trait]LineageHelper中携带的属性包括_lineageResolved(是否被Rule解析)，childrenLineageResolved用于递归判断所有的子逻辑计划是否已经被Rule解析过，markLineageResolved用来标注当前逻辑计划解析成功
https://github.com/frankyu8/ushas/blob/a46317df9396161a257a2388938289926ff7a46a/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/lineageCollect/LineageHelper.scala#L24-L45
然后所有的列级对象都会被lineageChildren 容器进行接手。

### 列级对象存在的合理性
逻辑计划，其实是针对算子进行操作的，并没有对算子里面的涉及成员进行操作，比如select a,b,c  from table 那么这里只会生成一个RelationLogicplan 和 ProjectLogicplan, 其中 ProjectLogicplan里面对应的projectList（即所有的字段expression）才是我们关心的列级血缘对象，为了不在projectList里面直接对expression进行操作，所以我们预先定义了列级对象（和expression一一对应），简单的记录了每个expression里面我们所关心的属性[extend treeNode]，然后放入lineageChildren中。只要确保Analyzer工作时，每次深度遍历，在不参与计算的节点将lineageChildren进行复制，携带到上层节点，在对应需要操作的节点进行关系的判断，即可保证列级字段的正确解析。
列级对象的子类包括ExpressionColumn,RelationColumn以及UnionColumn，ExpressionColumn主要记录的是Project逻辑计划里面的expression，RelationColumn主要记录的是LeafNode里面的从属关系,UnionColumn主要是记录了特殊的列字段关系，因为需要识别到相应的right lineageChildren信息。
https://github.com/frankyu8/ushas/tree/main/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/lineage

### rule是怎么进行工作的
因为logicplan是封装在所有的df对象内的，所以每次的df对象进行方法的操作，都会force 逻辑计划进行Analyzer的解析。我们在Analyzer的规则里面加上了自己的列级血缘判断规则
https://github.com/frankyu8/ushas/blob/a46317df9396161a257a2388938289926ff7a46a/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L211-L212
我们新增了两个解析规则，一个是针对Relation的解析规则（即字段血缘的叶子节点判断），一个是针对Expression的解析规则（即字段血缘的所有中间关系判断）
Relation的解析规则：
https://github.com/frankyu8/ushas/blob/796fc00b72ed64b6e1f5b3b0544b6b765eadc327/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveLineage.scala#L120
Expression的解析规则：
https://github.com/frankyu8/ushas/blob/796fc00b72ed64b6e1f5b3b0544b6b765eadc327/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveLineage.scala#L29




## 软件架构
### [module]assembly
assembly








## 安装教程



1.  idea maven 对spark-catalyst module 进行Gnerated Source Code 生成sqlbase.g4的语法树文件

2.  在跑样例文件时，先设置参数 -DLocal ，再设置 Include with provided scope，用默认本地形式和本地包运行spark

3.  样例文件位置  examples/src/main/scala/org/apache/spark/examples/lineage/SparkLineageExample.scala

4.  打包时，如果需要添加hive的插件支持，需要在spark profile中勾选hive

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




## 你可以做的事



1.  进行

2.  将列级血缘解析进行独立，做成通用的解析器

3.  丰富目前LineageTracker的内容



## 参与贡献



1.  Fork 本仓库

2.  新建 Feat_xxx 分支

3.  提交代码

4.  新建 Pull Request

