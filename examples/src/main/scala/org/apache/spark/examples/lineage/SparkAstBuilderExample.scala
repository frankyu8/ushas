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
package org.apache.spark.examples.lineage

import java.io.File

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.plans.logical.Project

object SparkAstBuilderExample {
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

    val spark = SparkSession
      .builder()
      .master("local[*]")
      .appName("Spark Lineage Example")
      .enableHiveSupport()
      .getOrCreate()
    //  org\apache\spark\sql\execution\SparkSqlParser.scala
    val a = spark.sessionState.sqlParser
    // 这里是第一步获取逻辑计划
    val logic_plan = a.parsePlan("select * from (select 1 as a,2 as b)")
    println(logic_plan)
    // 这步判断是否已经执行过Resolve解释
    println(logic_plan.resolved)
    // 这里是第二步将逻辑计划Unresolved转化为Resolved
    val qe = spark.sessionState.executePlan(logic_plan)
    //     这步是加载lazy执行Resolve解析
    val resolved_plan = qe.analyzed
    println(resolved_plan)
    // 这步判断是否已经执行过Resolve解释
    println(resolved_plan.resolved)
    println("Done")

  }
}
