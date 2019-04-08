package org.hablapps.sparkOptics

import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.{Column, DataFrame, Dataset}
import org.hablapps.sparkOptics.ProtoLens.ProtoLens

object GlassesFrame {

  object syntax extends GlassesFrameSyntax with GlassesFrameInstances

  trait GlassesFrameSyntax {

    implicit class GlassesProtoLensSyntax[O](optic: O)(
        implicit glasses: GlassesFrame[O]) {
      def setDF(newValue: Column): Dataset[_] => DataFrame =
        glasses.set(optic)(newValue)

      def setDFCheckingSchema(newValue: Column): Dataset[_] => DataFrame =
        glasses.setAndCheckSchema(optic)(newValue)

      def getDF: Dataset[_] => Column =
        glasses.get(optic)

      def modifyDF(f: Column => Column): Dataset[_] => DataFrame =
        glasses.modify(optic)(f)

      def modifyDFCheckingSchema(f: Column => Column): Dataset[_] => DataFrame =
        glasses.modifyAndCheckSchema(optic)(f)
    }

  }

  trait GlassesFrameInstances {
    implicit val lensGlasses: GlassesFrame[Lens] =
      new GlassesFrame[Lens] {
        override def set(optic: Lens)(
            newValue: Column): Dataset[_] => DataFrame = {
          _.select(optic.set(newValue): _*)
        }

        override def get(optic: Lens): Dataset[_] => Column = _ => optic.get

        override def modify(optic: Lens)(
            f: Column => Column): Dataset[_] => DataFrame =
          _.select(optic.modify(f): _*)
      }

    implicit val protoLensGlasses: GlassesFrame[ProtoLens] =
      new GlassesFrame[ProtoLens] {
        override def set(optic: ProtoLens)(
            newValue: Column): Dataset[_] => DataFrame =
          df => lensGlasses.set(optic(df.schema))(newValue)(df)

        override def get(optic: ProtoLens): Dataset[_] => Column =
          df => lensGlasses.get(optic(df.schema))(df)

        override def modify(optic: ProtoLens)(
            f: Column => Column): Dataset[_] => DataFrame =
          df => lensGlasses.modify(optic(df.schema))(f)(df)

      }
  }

}

trait GlassesFrame[A] {

  private def modifiedFieldIsEqualType(df1: Dataset[_],
                                       df2: Dataset[_],
                                       optic: A): Either[String, Unit] = {
    compareFields(df1.select(get(optic)(df1)).schema.fields.head,
                  df2.select(get(optic)(df2)).schema.fields.head)
  }

  private def compareFields(a: StructField,
                            b: StructField): Either[String, Unit] =
    for {
      _ <- Either
        .cond(a.name == b.name,
              (),
              s"The original name that was ${a.name} changed to ${b.name}")
        .right
      _ <- Either
        .cond(
          a.dataType.getClass.getCanonicalName == b.dataType.getClass.getCanonicalName,
          (),
          s"The original column type that was ${a.dataType.getClass.getSimpleName
            .filter(_ != '$')} " +
            s"changed to ${b.dataType.getClass.getSimpleName.filter(_ != '$')}"
        )
        .right
      _ <- Either
        .cond(
          a.nullable == b.nullable,
          (),
          s"The original column nullable that was ${a.nullable} changed to ${b.nullable}")
        .right
    } yield ()

  def set(optic: A)(newValue: Column): Dataset[_] => DataFrame

  def get(optic: A): Dataset[_] => Column

  def modify(optic: A)(f: Column => Column): Dataset[_] => DataFrame

  def setAndCheckSchema(optic: A)(newValue: Column): Dataset[_] => DataFrame =
    df => {
      val newDf = set(optic)(newValue)(df)
      val validation = modifiedFieldIsEqualType(df, newDf, optic)
      if (validation.isRight) {
        newDf
      } else {
        throw new Exception(validation.left.get)
      }
    }

  def modifyAndCheckSchema(optic: A)(
      f: Column => Column): Dataset[_] => DataFrame =
    df => {
      val newDf = modify(optic)(f)(df)
      val validation = modifiedFieldIsEqualType(df, newDf, optic)
      if (validation.isRight) {
        newDf
      } else {
        throw new Exception(validation.left.get)
      }
    }

}
