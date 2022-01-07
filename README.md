# Ushas

## Description
*
  Ushas is a component that is packaged on the basis of spark to strengthen the governance of data lineage. Although the table-level lineage judgment for spark in traditional data governance can solve the dependence of data to a certain extent, it is difficult to identify the relationship between fields as accurate as possible. The purpose of developing this component is to strengthen spark's tracking advantage in column-level lineage. Ushas represents that what we pursue is not just a simple judgment, but the ability to accurately capture relationship.
## Pave the way for knowledge
* ### Realization of logical plan in dataset
  Ushas is mainly modified in the spark-sql-catalyst module and spark-sql-hive module. The catalyst is mainly responsible for the relationship dependency management of spark in data processing. For normal datasets, the following code will be used to embed the logical plan processing in the object:
  ```
  Dataset.ofRows(sparkSession, logicalPlan)
  ```
* ### Implementation of logical plan in sql (What parser do)
  The support for spark-sql above version 2.0 is to perform syntax analysis through Antlr4 to generate a syntax tree, and then process syntax information which unresolved into resolved information through a deep traversal method.
  Each spark sql will execute parse through SparkSqlParser in advance, SparkSqlParser adds the lexical and processor required by antlr4, and then generates a logical plan:
  https://github.com/frankyu8/ushas/blob/ee36eac54b758e39689c7e2c51ea6f3aa5c27555/sql/core/src/main/scala/org/apache/spark/sql/SparkSession.scala#L641-L646

  The internal parsePlan will perform the operation of sql conversion logicalplan
  https://github.com/frankyu8/ushas/blob/ee36eac54b758e39689c7e2c51ea6f3aa5c27555/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/parser/ParseDriver.scala#L69-L76

* ### Analyzer
  OfRows will trigger Analyzer to analyze the logical plan, and will call the batches in Analyzer to convert from UnresolveLogicalplan to ResolveLogicalplan (convert if there is, skip if not)
  https://github.com/frankyu8/ushas/blob/ee36eac54b758e39689c7e2c51ea6f3aa5c27555/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/rules/RuleExecutor.scala#L72-L80

  The specific identification rules of Analyzer are as follows:
  https://github.com/frankyu8/ushas/blob/a7066a67ed9c1ad9db6078d68cfff8d28cce6bd4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L152-L214

  The specific parsing method of a batch in Analyzer is to take a post-order traversal or a pre-order traversal to analyze each nested logic plan
  ```
  def apply(plan: LogicalPlan): LogicalPlan = plan resolveOperatorsUp 
  ```
    https://github.com/frankyu8/ushas/blob/a7066a67ed9c1ad9db6078d68cfff8d28cce6bd4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/AnalysisHelper.scala#L85-L104

## What we do
* ### Let logicalplan have the ability of column-level analysis
  In order to allow logicplan to have the ability to recognize column-level lineage, we first modified the abstract class of the logic plan (here in order not to affect the other functions of logicplan, so additional traits were added), so that when new batch which trying to deal with column-lineage is added to the Analyzer, all influencing factors are controlled within additional traits, and will not affect the normal logic plan analysis of spark itself
  https://github.com/frankyu8/ushas/blob/a7066a67ed9c1ad9db6078d68cfff8d28cce6bd4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/LogicalPlan.scala#L30-L36
* ### How traits work
  Here the attributes carried in [trait]LineageHelper include _lineageResolved (whether resolved by Rule), childrenLineageResolved (used to recursively determine whether all sub-logic plans have been resolved by Rule), markLineageResolved  (used to mark the success of the current logic plan resolution)
  https://github.com/frankyu8/ushas/blob/a46317df9396161a257a2388938289926ff7a46a/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/plans/logical/lineageCollect/LineageHelper.scala#L24-L45
  
  All column-level objects will be taken over by the lineageChildren container.

* ### The rationality of the existence of column-level objects
  The logic plan actually operates on the operator, and does not operate on the members involved in the operator. Only a RelationLogicplan and ProjectLogicplan will be generated here with the sentence 'select a, b, c from table'. The corresponding projectList which contains all expressiones in ProjectLogicplan is what we care about. In order not to directly operate the expression in the projectList, we pre-defined the column-level objects (one-to-one correspondence with the expression), and simply record the contents of each expression The attribute we care about [extend treeNode] is then put into lineageChildren. As long as it is ensured that when the Analyzer is working, every time it is traversed in depth, the lineageChildren is copied from the node that is not involved in the calculation, and carried to the upper node, and the relationship is judged on the node that needs to be operated, so that the correct analysis of the column-level field can be ensured.
  
  The subclasses of column-level objects include ExpressionColumn, RelationColumn and UnionColumn. ExpressionColumn mainly records the expression in the Project logical plan, RelationColumn mainly records the affiliation in LeafNode, and UnionColumn mainly records the special column field relationship because it needs to identify corresponding information of right lineageChildren.
  https://github.com/frankyu8/ushas/tree/main/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/lineage

* ### How does the rule work
  Because logicplan is encapsulated in all df objects, every time the df object performs a method operation, the logic plan will be forced to analyze the analyzer. We have added our own column-level lineage judgment rules to the Analyzer rules
https://github.com/frankyu8/ushas/blob/a46317df9396161a257a2388938289926ff7a46a/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L211-L212

  We have added two new parsing rules, one is the parsing rule for Relation (that is, the leaf node judgment of the field blood), and the other is the parsing rule for Expression (that is, the judgment of all the intermediate relations of the field blood)

  Relation's parsing rules：
  https://github.com/frankyu8/ushas/blob/796fc00b72ed64b6e1f5b3b0544b6b765eadc327/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveLineage.scala#L120
  
  Relation here is a simple affiliation judgment to record the attribute of each field which does not look for catalog in the logical plan.

  Expression's parsing rules：
  https://github.com/frankyu8/ushas/blob/796fc00b72ed64b6e1f5b3b0544b6b765eadc327/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/ResolveLineage.scala#L29
  
  The analysis of Expression here uses the most basic map addressing, that is, the exprid of the upper field is matched with the exprid of the lower field, and if it is matched, it will be bound (borrowing the only feature of exprid)

* ### Recognition of hive relation
  For hive, the current development is to take all the hive information。If enablehive is started in sparksession, the default is to rewrite Analyzer, so in order to add support for hive data sources, we add tailResolutionRules to the Analyzer base class and rewrite it in the inherited analyzer.
  
  Analyzer of the base class
  
  https://github.com/frankyu8/ushas/blob/30faef47cbab44293314faf6638d2594cd8af62b/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L149-L150
  
  Rewrite Analyzer in hive's sessionStatebuilder
  
  https://github.com/frankyu8/ushas/blob/30faef47cbab44293314faf6638d2594cd8af62b/sql/hive/src/main/scala/org/apache/spark/sql/hive/HiveSessionStateBuilder.scala#L69-L94

## Software Architecture
* ### [module]assembly
  The assembly module is for more convenient access to the packaged content. The native code of spark is transplanted here, which can be automatically packaged with one click, and all jar packages are obtained in the target/scala directory.
* ### [module]dev
  The dev module is to configure checkstyle code specification detection. Spark has built-in scala code specification requirements. We also follow all his requirements here. The output directory is target/checkstyle-output.xml
* ### [module]examples
  The example module is to provide application examples of column-level lineage
* ### [directory]sql
  SQL contains all the analysis of spark catalyst, and the main work of the column-level lineage is concentrated on the three modules contained in SQL


## Installation tutorial


1.  Using 'Gnerated Source Code' with maven inside Idea on spark-catalyst module to generate syntax tree file through sqlbase.g4 . 

2.  When running the sample file, set the parameter -DLocal first, then set Include with provided scope, and run spark with the default local format and local package

3.  Sample file location  examples/src/main/scala/org/apache/spark/examples/lineage/SparkLineageExample.scala

4.  When packaging, if you need to add hive plug-in support, you need to check hive in the spark profile

5.  Because it is the separation in the spark project, you only need to replace spark-hive_2.12-3.1.2.jar and spark-catalyst_2.12-3.1.2.jar to complete the rapid deployment of the column-level blood relationship


## Show results



1.  Prepare sample sql ： select * from (select substr(a+1,0,1) as c,a+3 as d  from (select 1 as a,2 as b))

2.  Sample output：

```
c#2
+- c#2
   +- Alias ( substring(cast((a#0 + 1) as string), 0, 1) AS c#2 )
      +- a#0
         +- Alias ( 1 AS a#0 )
```

3.  How to view column-level lineage in spark-shell
    
    df.queryExecution.analyzed.lineageChildren(0).treeString

4.  How to view column-level lineage in pyspark
    
    df._jdf.queryExecution().analyzed().lineageChildren().apply(0).treeString()

## Things you can do

1. Optimize the existing code structure. The current project code structure is consistent with the structure of spark2.4. Modules can be modified in a targeted manner, and the main code can be gathered together.

2. Add a new module. Currently, only Spark column-level lineage analysis is provided, but the automatic storage and display of lineage is a blank area left for the committee.

3. Existing rules search for logical optimization. The current search for corresponding column relations mainly relies on the global uniqueness of exprid. Here, because it is traversal search, it will increase the time consumption to see if optimization can be performed.

4. Spark currently relies on UnresolveLogicplan to replace resolveLogicplan to perform the resolve mark, and we simply mark it as True in the lineageresolve of the logic plan, and does not give a standardized sample class. The amount of engineering to optimize this area is very large.

5. There is an example of injection through spark plug-inization in the example. I hoped to modify the source code as little as possible and implement the rule rules externally, but the result is not satisfactory. You can try how to configure the plug-in.

6. At present, the data source of hive is addressed, that is to say, the current column-level lineage can accurately identify the data source of hive in the catalog, but other external data sources are not defined in any way. This will be done all by you. 

## Participate in contribution

1.  Fork this repository

2.  Add Feat_xxx branch

3.  Submit code

4.  New Pull Request

5.  Help

    How to associate issue and pr
    
    https://docs.github.com/cn/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue
    
    How to configure checkstyle code inspection
    
    https://blog.csdn.net/qq_31424825/article/details/100050445
 
