
package org.apache.spark.sql.hive

import org.apache.spark.sql.catalyst.expressions.AttributeReference
import org.apache.spark.sql.catalyst.lineage.{Column, RelationColumn}
import org.apache.spark.sql.catalyst.plans.logical.{LeafNode, LogicalPlan}
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.execution.datasources.LogicalRelation


class ResolveHiveRelation extends Rule[LogicalPlan] {
  override def apply(plan: LogicalPlan): LogicalPlan = plan.resolveOperatorsUp {
    case l: LogicalRelation =>
      println("识别到了hive relation")
      l.lineageChildren = l.output.foldLeft(Seq.empty[Column]) { (result, child) => {
        child match {
          case a: AttributeReference =>
            result :+ RelationColumn(a, l, l.catalogTable)
        }
      }
      }
      println(l.lineageChildren)
      l.markLineageResolved()
      l

    case p: LeafNode =>
      p.lineageChildren = p.output.foldLeft(Seq.empty[Column]) { (result, child) => {
        child match {
          case a: AttributeReference =>
            result :+ RelationColumn(a, p, "")
        }
      }
      }
      p.markLineageResolved()
      println("Hive Relation")
      println(p.lineageChildren)
      p
  }
}