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

import org.apache.spark.sql.catalyst.expressions.{AttributeReference, ExprId, NamedExpression}
import org.apache.spark.sql.catalyst.plans.logical.LeafNode

case class UnionColumn(leftExprCol: Column, rightExprCol: Column) extends Column {
  /**
   * Returns a Seq of the children of this node.
   * Children should not change. Immutability required for containsChild optimization
   */

  override def name: String = leftExprCol.name

  override def exprId: ExprId = leftExprCol.exprId

  override def children: Seq[Column] = leftExprCol :: rightExprCol :: Nil

  /** ONE line description of this node with more information 树状图打印信息 */
  override def verboseString: String = "Union"

  override def toString: String = "Union"

}
