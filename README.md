# Ushas

#### 介绍

Ushas 是一款在spark基础上进行封装，强化数据血缘治理的组件。传统数据治理中针对spark的表级别血缘判断虽然能一定程度上解决数据的依赖关系，但是对于精确到字段之间的关系识别则显得捉襟见肘。开发此组件的用意是为了能够加强spark在列级血缘上的追踪优势。shark代表了我们追求的不仅仅是简单的判断，而是能够精确地捕捉血缘



#### 软件架构

Ushas 主要在spark-sql-catalyst和spark-sql-hive模块进行了修改，catalyst主要是负责spark在数据处理中的关系依赖管理，其中对于普通的dataset会通过如下代码，将逻辑计划处理嵌入对象内：
```
Dataset.ofRows(sparkSession, logicalPlan)
```
而spark2.0版本以上对于spark-sql的支持则是通过Antlr4进行语法解析，生成语法树，然后通过深度遍历的方式将Unresolved的语法信息处理为resolved信息。
每条spark的sql，都会预先通过SparkSqlParser执行parse，parse添加了antlr4需要的词法以及处理器，通过如下方法生成逻辑计划：

```

​    astBuilder.visitSingleStatement(parser.singleStatement()) match {

​      case plan: LogicalPlan => plan

​      case _ =>

​        val position = Origin(None, None)

​        throw QueryParsingErrors.sqlStatementUnsupportedError(sqlText, position)

​    }

```
值得注意的是，这里生成的逻辑计划是未解析过的，即可能有未识别的数据表名，或者未识别的函数、字段名。
spark会通过Dataset.ofRows的方法中通过调用queryExecution的Analyzer进行符合特定规则的解析(这里采用了深度遍历的形式，对语法树解析，resolveOperatorsUp/Down)，来实现命名识别，方法进入如下：
```
  def ofRows(sparkSession: SparkSession, logicalPlan: LogicalPlan): DataFrame = {
//    这里在session state里面创建一个新的analyzer，用于将Unresloved逻辑计划解析成Resolved
    val qe = sparkSession.sessionState.executePlan(logicalPlan)
    qe.assertAnalyzed()
    new Dataset[Row](sparkSession, qe, RowEncoder(qe.analyzed.schema))
  }
```
可以通过 df.queryExecution.logical来查看UnresolvedLogicalPlan，通过df.queryExecution.analyzed查看解析后的ResolvedLogicalPlan。
Analyzer的具体识别规则如下:
```
  lazy val batches: Seq[Batch] = Seq(
    Batch("Hints", fixedPoint,
      new ResolveHints.ResolveBroadcastHints(conf),
      ResolveHints.ResolveCoalesceHints,
      ResolveHints.RemoveAllHints),
    Batch("Simple Sanity Check", Once,
      LookupFunctions),
    Batch("Substitution", fixedPoint,
      CTESubstitution,
      WindowsSubstitution,
      EliminateUnions,
      new SubstituteUnresolvedOrdinals(conf)),
    Batch("Resolution", fixedPoint,
      ResolveTableValuedFunctions ::
        ResolveRelations ::
        ResolveReferences ::
        ResolveCreateNamedStruct ::
        ResolveDeserializer ::
        ResolveNewInstance ::
        ResolveUpCast ::
        ResolveGroupingAnalytics ::
        ResolvePivot ::
        ResolveOrdinalInOrderByAndGroupBy ::
        ResolveAggAliasInGroupBy ::
        ResolveMissingReferences ::
        ExtractGenerator ::
        ResolveGenerate ::
        ResolveFunctions ::
        ResolveAliases ::
        ResolveSubquery ::
        ResolveSubqueryColumnAliases ::
        ResolveWindowOrder ::
        ResolveWindowFrame ::
        ResolveNaturalAndUsingJoin ::
        ResolveOutputRelation ::
        ExtractWindowExpressions ::
        GlobalAggregates ::
        ResolveAggregateFunctions ::
        TimeWindowing ::
        ResolveInlineTables(conf) ::
        ResolveHigherOrderFunctions(catalog) ::
        ResolveLambdaVariables(conf) ::
        ResolveTimeZone(conf) ::
        ResolveRandomSeed ::
        TypeCoercion.typeCoercionRules(conf) ++
          extendedResolutionRules: _*),
    Batch("Post-Hoc Resolution", Once, postHocResolutionRules: _*),
    Batch("View", Once,
      AliasViewChild(conf)),
    Batch("Nondeterministic", Once,
      PullOutNondeterministic),
    Batch("UDF", Once,
      HandleNullInputsForUDF),
    Batch("FixNullability", Once,
      FixNullability),
    Batch("Subquery", Once,
      UpdateOuterReferences),
    Batch("Cleanup", fixedPoint,
      CleanupAliases),
```






#### 安装教程



1.  idea maven 对spark-catalyst module 进行Gnerated Source Code 生成sqlbase.g4的语法树文件

2.  在跑样例文件时，先设置参数 -DLocal ，再设置 Include with provided scope，用默认本地形式和本地包运行spark

3.  样例文件位置  examples/src/main/scala/org/apache/spark/examples/lineage/SparkLineageExample.scala

4.  打包时，如果需要添加hive的插件支持，需要在spark profile中勾选hive

#### 效果展示



1.  准备样例sql ： select * from (select substr(a+1,0,1) as c,a+3 as d  from (select 1 as a,2 as b))

2.  样例输出：

```
c#2
+- c#2
   +- Alias ( substring(cast((a#0 + 1) as string), 0, 1) AS c#2 )
      +- a#0
         +- Alias ( 1 AS a#0 )
```




#### 你可以做的事



1.  丰富spark的sqlbase.g4语法，进行多数据源的解析支持

2.  将列级血缘解析进行独立，做成通用的解析器

3.  丰富目前LineageTracker的内容



#### 参与贡献



1.  Fork 本仓库

2.  新建 Feat_xxx 分支

3.  提交代码

4.  新建 Pull Request

