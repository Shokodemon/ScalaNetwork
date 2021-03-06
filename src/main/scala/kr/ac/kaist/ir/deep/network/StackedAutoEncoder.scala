package kr.ac.kaist.ir.deep.network

import kr.ac.kaist.ir.deep.fn.ScalarMatrix
import play.api.libs.json.{JsObject, Json}

import scala.collection.mutable.ArrayBuffer

/**
 * __Network__: Stack of autoencoders. 
 *
 * @param encoders __Sequence of AutoEncoders__ to be stacked.
 */
class StackedAutoEncoder(val encoders: Seq[AutoEncoder]) extends Network {
  /**
   * All weights of layers
   *
   * @return all weights of layers
   */
  override val W: IndexedSeq[ScalarMatrix] = {
    val matrices = ArrayBuffer[ScalarMatrix]()
    encoders.flatMap(_.W).foreach(matrices += _)
    matrices
  }

  /**
   * Serialize network to JSON
   *
   * @return JsObject of this network
   */
  override def toJSON: JsObject =
    Json.obj(
      "type" → this.getClass.getSimpleName,
      "stack" → Json.arr(encoders map (_.toJSON))
    )

  /**
   * Compute output of neural network with given input (without reconstruction)
   * If drop-out is used, to average drop-out effect, we need to multiply output by presence probability.
   *
   * @param in an input vector
   * @return output of the vector
   */
  override def apply(in: ScalarMatrix): ScalarMatrix = {
    encoders.foldLeft(in) {
      case (v, l) ⇒ l apply v
    }
  }

  /**
   * Sugar: Forward computation for training. Calls apply(x)
   *
   * @param x input matrix
   * @return output matrix
   */
  override def passedBy(x: ScalarMatrix): ScalarMatrix = {
    encoders.foldLeft(x) {
      case (v, l) ⇒ l passedBy v
    }
  }

  /**
   * Backpropagation algorithm
   *
   * @param delta Sequence of delta amount of weight. The order must be the reverse of [[W]]
   * @param err backpropagated error from error function
   */
  override def updateBy(delta: Iterator[ScalarMatrix], err: ScalarMatrix): ScalarMatrix = {
    encoders.foldRight(err) {
      case (l, e) ⇒ l updateBy(delta, e)
    }
  }
}


