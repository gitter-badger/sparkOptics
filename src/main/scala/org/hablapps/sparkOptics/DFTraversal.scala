package org.hablapps.sparkOptics

import org.apache.spark.sql.{Column, DataFrame}
import org.apache.spark.sql.types.StructType
import org.hablapps.sparkOptics.Lens.ProtoLens

object DFTraversal {

  type ProtoDFTraversal = StructType => DFTraversal

  val proto: ProtoDFTraversal = struct => {
    new DFTraversal {
      override def structure: StructType = struct

      override def opticsToApply: Option[Lens] = None

      override def set(newValue: Column): DataFrame => DataFrame = {
        if (structure.fields.length == 1) {
          _.withColumn(structure.fields.head.name, newValue)
        } else {
          throw new Exception("a dataframe with only one column was expected, because you'r not applying any extra optics")
        }
      }
    }
  }

  object implicits {

    implicit class ProtoDFTraversalSyntax(proto: ProtoDFTraversal) {
      def composeLens(lens: Lens): DFTraversal = {
        proto(lens.structure) composeLens lens
      }

      def composeProtoLens(protoLens: ProtoLens): ProtoDFTraversal = structure => {
        proto(structure) composeProtoLens protoLens
      }

      def apply(df: DataFrame): DFTraversal = {
        proto(df.schema)
      }
    }

  }

}

abstract sealed class DFTraversal() {
  def structure: StructType

  def set(newValue: Column): DataFrame => DataFrame

  def opticsToApply: Option[Lens]

  def composeLens(lens: Lens): DFTraversal = {
    val traversalParent = this
    new DFTraversal {
      override def structure: StructType = traversalParent.structure

      override def set(newValue: Column): DataFrame => DataFrame =
        _.select(opticsToApply.get.set(newValue): _*)

      override def opticsToApply: Option[Lens] =
        Some(traversalParent.opticsToApply.fold(lens)(_ composeLens lens))
    }
  }

  def composeProtoLens(protoLens: ProtoLens): DFTraversal = {
    val traversalParent = this
    new DFTraversal {
      override def structure: StructType = traversalParent.structure

      override def set(newValue: Column): DataFrame => DataFrame =
        _.select(opticsToApply.get.set(newValue): _*)

      override def opticsToApply: Option[Lens] =
        Some(traversalParent.opticsToApply.fold(protoLens(traversalParent.structure))(_ composeProtoLens protoLens))
    }
  }

}
