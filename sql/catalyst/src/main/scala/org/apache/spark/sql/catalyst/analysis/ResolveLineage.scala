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

package org.apache.spark.sql.catalyst.analysis

import org.apache.spark.sql.catalyst.expressions.{AttributeReference, Expression, NamedExpression}
import org.apache.spark.sql.catalyst.lineage.{Column, ExpressionColumn, RelationColumn, UnionColumn}
import org.apache.spark.sql.catalyst.plans.logical.{LeafNode, LogicalPlan, OneRowRelation, Project, Union}
import org.apache.spark.sql.catalyst.rules.Rule

import scala.collection.mutable

object ResolveLineage {

  class ResolveExpression extends Rule[LogicalPlan] {


    def fetchRelation(projectExprCol: Column, lineageCols: Seq[Column]):
    Column = {
      val lineageColsMap = mutable.HashMap.empty[Long, Column]
      lineageCols.foreach(x => {
        lineageColsMap(x.exprId.id) = x
      })
      val key = projectExprCol.exprId.id
      if (lineageColsMap.contains(key)) {
        projectExprCol.childrenInternal = projectExprCol.childrenInternal :+ lineageColsMap(key)
      }
      projectExprCol
    }

    /**
     * find project leaves
     * List((a#11 + 1) AS b#12, (a#11 + 2) AS c#13)  =>  List(a#11, a#11)
     *
     * @return
     */
    def findAllChildren(projectExpressions: Seq[NamedExpression], lineageCols: Seq[Column]):
    Seq[Column] = {
      val projectColsRe = projectExpressions.foldLeft(Seq.empty[Column]) {
        (projectCols: Seq[Column], projectExpr: NamedExpression) => {
          val projectCol = ExpressionColumn(projectExpr)
          projectCol.childrenInternal = projectCol.childrenInternal ++: projectExpr.collectLeaves().
            foldLeft(Seq.empty[Column]) { (projectExprCols, projectExprChild: Expression) => {
              projectExprChild match {
                case ne: NamedExpression =>
                  println("matched NamedExpression")
                  val projectExprCol = ExpressionColumn(ne)
                  fetchRelation(projectExprCol, lineageCols)
                  projectExprCols :+ projectExprCol
                case _ =>
                  println("nothing matched")
                  projectExprCols
              }
            }
            }
          projectCols :+ projectCol
        }
      }
      projectColsRe
    }


    /**
     * 解析方法，当逻辑计划的子计划已经解析时，会进行列级关系的匹配绑定
     * 当逻辑计划未解析时，进行列对象集合的创建
     *
     * @param plan
     * @return
     */
    override def apply(plan: LogicalPlan): LogicalPlan = plan.resolveOperatorsUp {


      case p: Project if p.lineageResolved =>
        val projectExpressions = p.projectList
        val lineageCols = p.child.lineageChildren
        println("Project lineageChildren is")
        println(lineageCols)
        p.lineageChildren = findAllChildren(projectExpressions, lineageCols)
        p.markLineageResolved()
        p

      case u@Union(left :: right :: Nil) if u.lineageResolved =>
        //        union这里需要特殊处理,否则识别不到相应的right children信息,而且必须上下一一对应
        val leftChildren = left.lineageChildren
        val rightChildren = right.lineageChildren
        val zippedLeftRight = leftChildren zip rightChildren
        u.lineageChildren = zippedLeftRight.foldLeft(Seq.empty[Column]) {
          (result, zippedLeftChild) =>
            val unionCol = UnionColumn(zippedLeftChild._1, zippedLeftChild._2)
            result :+ unionCol
        }
        u.markLineageResolved()
        u
      case l: LogicalPlan if (l.lineageResolved && !l.isInstanceOf[LeafNode])=>
        l.lineageChildren = l.children.foldLeft(Seq.empty[Column]) { (result, child) => {
          result ++: child.lineageChildren
        }
        }
        println("LogicalPlan lineageChildren is")
        println(l.lineageChildren)
        l.markLineageResolved()
        l
    }
  }

  class ResolveRelation extends Rule[LogicalPlan] {
    override def apply(plan: LogicalPlan): LogicalPlan = plan.resolveOperatorsUp {
      case p: LeafNode =>
        p.lineageChildren = p.output.foldLeft(Seq.empty[Column]) { (result, child) => {
          child match {
            case a: AttributeReference =>
              result :+ RelationColumn(a, p, "")
          }
        }
        }
        println("Resolve Relation")
        println(p.lineageChildren)
        p.markLineageResolved()
        p

    }
  }

}
