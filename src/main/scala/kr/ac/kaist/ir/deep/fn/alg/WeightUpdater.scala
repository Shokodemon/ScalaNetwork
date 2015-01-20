package kr.ac.kaist.ir.deep.fn.alg

import breeze.linalg.sum
import breeze.numerics._
import kr.ac.kaist.ir.deep.fn._

/**
 * __Trait__ that describes the algorithm for weight update
 *
 * Because each weight update requires history, we recommend to make inherited one as a class. 
 */
trait WeightUpdater extends ((Seq[ScalarMatrix], Seq[ScalarMatrix]) ⇒ Unit) with Serializable {
  /** Decay factor for L,,1,, regularization */
  protected val l1decay: Scalar
  /** Decay factor for L,,2,, regularization */
  protected val l2decay: Scalar

  /**
   * Execute the algorithm for given __sequence of Δweight__ and sequence of __weights__
   *
   * @param delta the __sequence of accumulated Δweight__
   * @param weight the __sequence of current weights__
   */
  override def apply(delta: Seq[ScalarMatrix], weight: Seq[ScalarMatrix]): Unit

  /**
   * Compute weight-loss of given weight parameters
   *
   * @param seq the __sequence__ of weight matrices
   * @return the total weight loss of this sequence
   */
  def loss(seq: Seq[ScalarMatrix]) =
    seq.foldLeft(0.0) {
      (err, obj) ⇒
        val l1loss = sum(abs(obj)) * l1decay
        val l2loss = sum(pow(obj, 2)) * l2decay
        err + l1loss + l2loss
    }
}