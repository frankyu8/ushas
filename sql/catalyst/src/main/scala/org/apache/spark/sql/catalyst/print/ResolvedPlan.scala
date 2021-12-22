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
package org.apache.spark.sql.catalyst.print

import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.slf4j.{Logger, LoggerFactory}

//这里只是为了打印每一步的Resolve，用于判断
object ResolvedPlan {
  private val logger = LoggerFactory.getLogger(ResolvedPlan.getClass.getName)


  def logMatch(className: String, matchedLogicalPlan: LogicalPlan): Unit = {
    println("Rule:" + className + ",MatchedPlan:" + matchedLogicalPlan.getClass.getName)
    println("UnresolvedLogicalPlan is :")
    println(matchedLogicalPlan)

  }

  def logVisit[T](nodeName: String)(f: => T): T = {
    println(nodeName+" IN ")
    val s = f
    println(nodeName+" OUT ")
    s
  }
}
