package io.samritchie.rl

import cats.implicits._
import cats.free.{Free, Trampoline}
import com.stripe.rainier.core.Generator
import com.stripe.rainier.core.RandomVariable
import com.stripe.rainier.compute.Real
import com.stripe.rainier.sampler.RNG

import _root_.cats.Monad

import scala.annotation.tailrec

object RainierMonad {
  implicit val rainierMonadGenerator: Monad[Generator] = new MonadGenerator
  implicit val rainierMonadRandomVariable: Monad[RandomVariable] =
    new MonadRandomVariable

  type GenEither[A, B] = Generator[Either[A, B]]

  // def tailRecM[A, B](a: A)(f: A => Generator[Either[A, B]]): Generator[B] =
  // new Generator[B] {
  //   f(a)
  //     lazy val step = f(a)
  //     def requirements: Set[Real] = step.requirements
  //     def get(implicit r: RNG, n: Numeric[Real]): B =
  //       step.get match {
  //         case Right(b) => Free.pure(b)
  //         case Left(aa) => Free.pure(tailRecM(aa)(f).get)
  //       }
  //   }

  // def toFree[A, B](k: GenEither[A, B]): Free[GenEither[A, ?], B] =
  //   Free.liftF[GenEither[A, ?], B](k)

  // def toGenerator[A, B](f: Free[GenEither[A, ?], B])(implicit M: Monad[GenEither[A, ?]]): Generator[Either[A, B]] =
  //   f.runTailRec
}

private[rl] class MonadGenerator extends Monad[Generator] {
  def pure[A](x: A): Generator[A] = Generator.constant(x)

  override def map[A, B](fa: Generator[A])(f: A => B): Generator[B] =
    fa.map(f)

  override def product[A, B](fa: Generator[A],
                             fb: Generator[B]): Generator[(A, B)] =
    fa.zip(fb)

  def flatMap[A, B](fa: Generator[A])(f: A => Generator[B]): Generator[B] =
    fa.flatMap(f)

  // note: currently not stack safe
  def tailRecM[A, B](a: A)(f: A => Generator[Either[A, B]]): Generator[B] =
    new Generator[B] {
      lazy val step = f(a)
      def requirements: Set[Real] = step.requirements
      def get(implicit r: RNG, n: Numeric[Real]): B =
        step.get match {
          case Right(b) => b
          case Left(aa) => tailRecM(aa)(f).get
        }
    }
}

private[rl] class MonadRandomVariable extends Monad[RandomVariable] {
  def pure[A](x: A): RandomVariable[A] = RandomVariable(x)

  override def map[A, B](fa: RandomVariable[A])(f: A => B): RandomVariable[B] =
    fa.map(f)

  def flatMap[A, B](fa: RandomVariable[A])(
      f: A => RandomVariable[B]): RandomVariable[B] =
    fa.flatMap(f)

  override def product[A, B](fa: RandomVariable[A],
                             fb: RandomVariable[B]): RandomVariable[(A, B)] =
    fa.zip(fb)

  @tailrec final def tailRecM[A, B](a: A)(
      f: A => RandomVariable[Either[A, B]]): RandomVariable[B] =
    f(a).value match {
      case Left(aa) => tailRecM(aa)(f)
      case Right(b) => RandomVariable(b)
    }
}
