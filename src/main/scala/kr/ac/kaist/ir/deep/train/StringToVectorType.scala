package kr.ac.kaist.ir.deep.train

import kr.ac.kaist.ir.deep.fn._
import kr.ac.kaist.ir.deep.network.Network
import kr.ac.kaist.ir.deep.wordvec.WordModel
import org.apache.spark.broadcast.Broadcast

/**
 * __Input Operation__ : String as Input & ScalarMatrix as Otput __(Spark ONLY)__
 *
 * @param model Broadcast of WordEmbedding model that contains all meaningful words.
 * @param error An objective function `(Default: [[kr.ac.kaist.ir.deep.fn.SquaredErr]])`
 *
 * @example
 * {{{var make = new StringToVectorType(model = wordModel, error = CrossEntropyErr)
 *     var out = make onewayTrip (net, in)}}}
 */
class StringToVectorType(protected override val model: Broadcast[WordModel],
                         override val error: Objective) extends StringType[ScalarMatrix] {
  /**
   * Apply & Back-prop given single input
   *
   * @param net A network that gets input
   * @param in Input for error computation.
   * @param real Real output for error computation.
   * @param isPositive Boolean that indicates whether this example is positive or not.
   */
  override def roundTrip(net: Network, in: String, real: ScalarMatrix, isPositive: Boolean): Unit = {
    val out = model.value(in) into_: net
    val err: ScalarMatrix = error.derivative(real, out)
    if (isPositive) {
      net updateBy err
    } else {
      net updateBy (-err)
    }
  }

  /**
   * Make validation output
   *
   * @param net A network that gets input
   * @param pair (Input, Real output) pair for computation
   * @return input as string
   */
  override def stringOf(net: Network, pair: (String, ScalarMatrix)): String = {
    val in = pair._1
    val real = pair._2
    val out = net of model.value(in)
    s"IN: $in EXP: ${real.mkString} → OUT: ${out.mkString}"
  }

  /**
   * Apply given input and compute the error
   *
   * @param net A network that gets input
   * @param pair (Input, Real output) for error computation.
   * @return error of this network
   */
  override def lossOf(net: Network)(pair: (String, ScalarMatrix)): Scalar = {
    val in = pair._1
    val real = pair._2
    val out = net of model.value(in)
    error(real, out)
  }
}
