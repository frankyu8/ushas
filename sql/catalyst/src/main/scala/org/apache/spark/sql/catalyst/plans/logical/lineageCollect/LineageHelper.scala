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

package org.apache.spark.sql.catalyst.plans.logical.lineageCollect

import org.apache.spark.sql.catalyst.plans.QueryPlan
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan


trait LineageHelper extends QueryPlan[LogicalPlan] with LineageEntity{
  self: LogicalPlan =>
  private var _lineageResolved: Boolean = false

  /**
   * Returns true if  all the children of this expression have been resolved to a specific schema
   * and false if any still contains any unresolved placeholders.
   */
  def childrenLineageResolved: Boolean = children.forall(_.lineageResolved)

  /**
   * 判断下游的lineage是否已经被解析（即生成了对应的Col对象）
   */
  lazy val lineageResolved: Boolean = childrenLineageResolved

  /**
   * 标记对象为已被解析
   */
  def markLineageResolved(): Unit = {
    _lineageResolved = true
  }
}