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

import org.apache.spark.sql.catalyst.expressions.{Expression, NamedExpression}
import org.apache.spark.sql.catalyst.expressions.ExprId

case class ExpressionColumn(expr: Expression) extends Column {
  /**
   * Returns a Seq of the children of this node.
   * Children should not change. Immutability required for containsChild optimization
   */

  /**
   * 这里是为了调用children的树方法
   */

  /** ONE line description of this node with more information */
  override def verboseString: String = expr.toString

  override def toString: String = expr.toString

  /**
   * Returns true if  all the children of this expression have been resolved to a specific schema
   * and false if any still contains any unresolved placeholders.
   */
  override def name: String = {
    expr match {
      case expression: NamedExpression =>
        expression.name
    }
  }

  override def exprId: ExprId = {
    expr match {
      case expression: NamedExpression =>
        expression.exprId
    }
  }
}
