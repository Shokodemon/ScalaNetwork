package kr.ac.kaist.ir.deep.layer

import breeze.linalg.sum
import breeze.numerics.pow
import kr.ac.kaist.ir.deep.fn._
import play.api.libs.json.{JsObject, Json}

/**
 * __Layer__ that normalizes its input.
 *
 * @param factor The multiplication factor of the normalized output `(Default 1.0)`
 */
@deprecated
class NormalizeOperation(protected val factor: Scalar = 1.0f) extends Layer {
  /**
   * weights for update
   *
   * @return weights
   */
  override val W: IndexedSeq[ScalarMatrix] = IndexedSeq.empty
  /** Null activation */
  protected override val act = null

  /**
   * Translate this layer into JSON object (in Play! framework)
   *
   * @return JSON object describes this layer
   */
  override def toJSON: JsObject = Json.obj(
    "type" → "NormOp",
    "factor" → factor
  )

  /**
   * <p>Backward computation.</p>
   *
   * @note Because this layer only mediates two layers, this layer just remove propagated error for unused elements.
   *
   * @param delta Sequence of delta amount of weight. The order must be the reverse of [[W]]
   *              In this case, centers :: weight :: REMAINDER-SEQ
   * @param error to be propagated ( <code>dG / dF</code> is propagated from higher layer )
   * @return propagated error (in this case, <code>dG/dx</code> ) and remainder of delta sequence
   */
  def updateBy(delta: Iterator[ScalarMatrix], error: ScalarMatrix): ScalarMatrix = {
    val len: Scalar = Math.sqrt(sum(pow(X, 2.0f))).toFloat
    val output: ScalarMatrix = apply(X)

    // Note that length is the function of x_i.
    // Let z_i := x_i / len(x_i).
    // Then d z_i / d x_i = (len^2 - x_i^2) / len^3 = (1 - z_i^2) / len,
    //      d z_j / d x_i = - x_i * x_j / len^3 = - z_i * z_j / len
    val rows = dFdX.rows
    val dZdX = ScalarMatrix $0(rows, rows)
    var r = 0
    while (r < rows) {
      //dZ_r
      var c = 0
      while (c < rows) {
        if (r == c) {
          //dX_c
          dZdX.update(r, c, (1.0f - output(r, 0) * output(r, 0)) / len)
        } else {
          dZdX.update(r, c, (-output(r, 0) * output(c, 0)) / len)
        }
        c += 1
      }
      r += 1
    }

    // un-normalize the error
    dZdX * error
  }

  /**
   * Forward computation
   *
   * @param x input matrix
   * @return output matrix
   */
  override def apply(x: ScalarMatrix): ScalarMatrix = {
    val len = Math.sqrt(sum(pow(x, 2.0f))).toFloat
    val normalized: ScalarMatrix = x :/ len

    if (factor != 1.0f)
      normalized :* factor
    else
      normalized
  }
}