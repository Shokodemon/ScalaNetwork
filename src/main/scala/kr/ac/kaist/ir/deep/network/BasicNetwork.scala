package kr.ac.kaist.ir.deep.network

import kr.ac.kaist.ir.deep.function._
import kr.ac.kaist.ir.deep.layer.Layer
import play.api.libs.json.{JsArray, Json}

/**
 * Network: A basic network implementation
 * @param layers of this network
 * @param presence is the probability of non-dropped neurons (for drop-out training). Default value = 1.0
 */
class BasicNetwork(private val layers: Seq[Layer], protected override val presence: Probability = 1.0) extends Network {
  /** Collected input & output of each layer */
  private var input: Seq[ScalarMatrix] = Seq()

  /**
   * All weights of layers
   * @return all weights of layers
   */
  override def W = layers flatMap (_.W)

  /**
   * All accumulated delta weights of layers
   * @return all accumulated delta weights
   */
  override def dW = layers flatMap (_.dW)

  /**
   * Compute output of neural network with given input
   * If drop-out is used, to average drop-out effect, we need to multiply output by presence probability.
   *
   * @param in is an input vector
   * @return output of the vector
   */
  override def apply(in: ScalarMatrix): ScalarMatrix = {
    // We don't have to store this value
    val localInput = layers.indices.foldLeft(Seq(in)) {
      (seq, id) ⇒ {
        val layerOut = seq.head >>: layers(id)
        val adjusted: ScalarMatrix = layerOut :* presence.safe
        adjusted +: seq
      }
    }
    localInput.head
  }

  /**
   * Serialize network to JSON
   * @return JsObject
   */
  override def toJSON = Json.obj(
    "type" → "BasicNetwork",
    "presence" → presence.safe,
    "layers" → JsArray(layers map (_.toJSON))
  )

  /**
   * Backpropagation algorithm
   * @param err backpropagated error from error function
   */
  protected[deep] override def !(err: ScalarMatrix) =
    layers.indices.foldRight(err) {
      (id, e) ⇒ {
        val l = layers(id)
        val out = input.head
        input = input.tail
        val in = input.head
        l !(e, in, out)
      }
    }

  /**
   * Forward computation for training.
   * If drop-out is used, we need to drop-out entry of input vector.
   *
   * @param x of input matrix
   * @return output matrix
   */
  protected[deep] override def >>:(x: ScalarMatrix): ScalarMatrix = {
    // We have to store this value
    input = layers.indices.foldLeft(Seq(x.copy)) {
      (seq, id) ⇒ {
        val in = seq.head
        if (presence < 1.0)
          in :*= ScalarMatrix $01(in.rows, in.cols, presence.safe)
        (in >>: layers(id)) +: seq
      }
    }
    input.head
  }
}