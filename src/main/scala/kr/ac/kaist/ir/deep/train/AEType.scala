package kr.ac.kaist.ir.deep.train

import kr.ac.kaist.ir.deep.fn._
import kr.ac.kaist.ir.deep.network.Network

/**
 * __Input Operation__ : Vector as Input & Auto Encoder Training (no output type)
 *
 * @param corrupt Corruption that supervises how to corrupt the input matrix. (Default : [[NoCorruption]])
 * @param error An objective function (Default: [[kr.ac.kaist.ir.deep.fn.SquaredErr]])
 *
 * @example
  * {{{var make = new AEType(error = CrossEntropyErr)
 *                       var corruptedIn = make corrupted in
 *                       var out = make onewayTrip (net, corruptedIn)}}}
 */
class AEType(override val corrupt: Corruption = NoCorruption,
             override val error: Objective = SquaredErr)
  extends ManipulationType[ScalarMatrix, Null] {

  /**
   * Corrupt input
   *
   * @param x input to be corrupted
   * @return corrupted input
   */
  override def corrupted(x: ScalarMatrix): ScalarMatrix = corrupt(x)

  /**
   * Apply & Back-prop given single input
   *
   * @param net A network that gets input
   * @param delta Sequence of delta updates
   */
  def roundTrip(net: Network, delta: Seq[ScalarMatrix]) = (in: ScalarMatrix, real: Null) ⇒ {
    val out = net passedBy in
    val err: ScalarMatrix = error.derivative(in, out)
    net updateBy(delta.toIterator, err)
  }

  /**
   * Apply given input and compute the error
   *
   * @param net A network that gets input
   * @param pair (Input, Real output) for error computation.
   * @return error of this network
   */
  def lossOf(net: Network)(pair: (ScalarMatrix, Null)): Scalar = {
    val in = pair._1
    val out = net of in
    error(in, out)
  }

  /**
   * Apply given single input as one-way forward trip.
   *
   * @param net A network that gets input
   * @param x input to be computed
   * @return output of the network.
   */
  override def onewayTrip(net: Network, x: ScalarMatrix): ScalarMatrix = net of x


  /**
   * Make validation output
   *
   * @return input as string
   */
  def stringOf(net: Network, pair: (ScalarMatrix, Null)): String = {
    val in = pair._1
    val out = net of in
    s"IN: ${in.mkString} RECON → OUT: ${out.mkString}"
  }
}
