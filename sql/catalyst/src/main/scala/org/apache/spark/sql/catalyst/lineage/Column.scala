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
package org.apache.spark.sql.catalyst.lineage

import org.apache.spark.sql.catalyst.expressions.ExprId
import org.apache.spark.sql.catalyst.trees.TreeNode


abstract class Column extends TreeNode[Column] {


  /**
   * Returns true if  all the children of this expression have been resolved to a specific schema
   * and false if any still contains any unresolved placeholders.
   */
  var childrenInternal : Seq[Column] = Seq()

  override def children: Seq[Column] = childrenInternal

  def name: String
  def exprId: ExprId

  def childrenLineageResolved: Boolean = children.forall(_.lineageResolved)

  /**
   * 判断下游是否都已经被列级血缘判断过
   */
  lazy val lineageResolved: Boolean = childrenLineageResolved


}
