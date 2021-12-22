/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.examples.lineage.extension

import org.apache.spark.sql.{SparkSession, SparkSessionExtensions}
import org.apache.spark.sql.catalyst.{FunctionIdentifier, TableIdentifier}
import org.apache.spark.sql.catalyst.analysis.UnresolvedStar
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.parser.ParserInterface
import org.apache.spark.sql.catalyst.plans.logical.{LogicalPlan, Project}
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.types.{DataType, StructType}
import org.apache.spark.sql.catalyst.analysis.ResolveLineage.{ResolveRelation,ResolveExpression}


object Example {
  def main(args: Array[String]) {
    // When working with Hive, one must instantiate `SparkSession` with Hive support, including
    // connectivity to a persistent Hive metastore, support for Hive serdes, and Hive user-defined
    // functions. Users who do not have an existing Hive deployment can still enable Hive support.
    // When not configured by the hive-site.xml, the context automatically creates `metastore_db`
    // in the current directory and creates a directory configured by `spark.sql.warehouse.dir`,
    // which defaults to the directory `spark-warehouse` in the current directory that the spark
    // application is started.

    // $example on:spark_hive$
    // warehouseLocation points to the default location for managed databases and tables
    type RuleBuilder = SparkSession => Rule[LogicalPlan]
    type ExtensionsBuilder = SparkSessionExtensions => Unit


    val ruleBuilderRelation1: RuleBuilder = _ => new ResolveRelation
    val ruleBuilderRelation2: RuleBuilder = _ => new ResolveExpression
    val extBuilder: ExtensionsBuilder = { e => {
//      e.injectParser(parserBuilder)
      e.injectResolutionRule(ruleBuilderRelation1)
      e.injectResolutionRule(ruleBuilderRelation2)

    }
    }


    val spark = SparkSession
      .builder()
      .master("local[*]")
      .appName("Spark Lineage Extension Parser Example")
      .enableHiveSupport()
      //      插入Parser控件
      .withExtensions(extBuilder)
      .getOrCreate()


//    val logic_plan=spark.sessionState.sqlParser.parsePlan("select * from (select 1+1  as b union all select 2)b left join (select 1  as a)a on a.a=b.b")
//    val qe = spark.sessionState.executePlan(logic_plan)
//    val resolved_plan=qe.analyzed
//    println(resolved_plan.lineageChildren(0))
//    println(resolved_plan.lineageChildren(0).collectLeaves())
//    println(resolved_plan.lineageChildren(0).treeString)

    val df=spark.sql("select * from (select 1 as a)")
    println(df.queryExecution.analyzed.lineageChildren)
  }
}
