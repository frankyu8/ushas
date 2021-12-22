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

import org.apache.spark.sql.catalyst.lineage.Column
import org.apache.spark.util.Utils

trait LineageEntity extends Product {
  var lineageChildren: Seq[Column] = Seq()

  protected def flatArguments: Iterator[Any] = productIterator.flatMap {
    case t: Traversable[_] => t
    case single => single :: Nil
  }

  /**
   * 原先的logicalPlan toString的信息不太详细，这边主要是为了兼容spark的版本，进行通用性打印
   * @return
   */
  def toLineageString: String = Utils.truncatedString(
    flatArguments.toSeq, "(", ", ", ")")
}
