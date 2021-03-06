package kr.ac.kaist.ir.deep

import breeze.linalg.DenseMatrix
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

/**
 * Package for various functions.
 */
package object fn {
  /** Type of scalar **/
  type Scalar = Float
  /** Type of probability **/
  type Probability = Float
  /** Type of Neuron Input **/
  type ScalarMatrix = DenseMatrix[Scalar]
  /** Define Alias **/
  val Tanh = HyperbolicTangent

  /**
   * Defines sugar operations for ScalarMatrix
   *
   * @param x the __matrix__ to be computed
   */
  implicit class ScalarMatrixOp(x: ScalarMatrix) {
    /**
     * Append __given scalar__ to last row.
     *
     * @param y a __scalar__ to be added
     */
    def row_+(y: Scalar): ScalarMatrix = {
      val scalar: ScalarMatrix = (ScalarMatrix $1(1, x.cols)) :* y
      x row_+ scalar
    }

    /**
     * Append __given matrix__ to last rows.
     *
     * @param y a __matrix__ to be added
     */
    def row_+(y: ScalarMatrix): ScalarMatrix = {
      DenseMatrix.vertcat(x, y)
    }

    /**
     * Append __given matrix__ to last columns.
     *
     * @param y a __matrix__ to be added
     */
    def col_+(y: ScalarMatrix) = {
      DenseMatrix.horzcat(x, y)
    }

    /**
     * Make given matrix as 2D JSON Array
     *
     * @return JsArray of this matrix
     */
    def to2DSeq: JsArray = {
      val r = x.rows
      val c = x.cols
      JsArray((0 until r) map {
        i ⇒ JsArray((0 until c) map {
          j ⇒ JsString(f"${x(i, j)}%.8f")
        })
      })
    }

    /**
     * String representation of matrix
     *
     * @return string representation
     */
    def mkString: String =
      "{" + (((0 until x.rows) map {
        r ⇒ "[" + (((0 until x.cols) map { c ⇒ f"${x(r, c)}%.3f"}) mkString ", ") + "]"
      }) mkString ", ") + "}"
  }

  /**
   * Defines sugar operations of probability
   *
   * @param x __scalar__ to be applied
   */
  implicit class ProbabilityOp(x: Probability) {
    /**
     * Returns safe probability
     *
     * @return probability between 0 and 1
     */
    def safe = if (0.0 <= x && x <= 1.0) x else if (x < 0.0) 0.0f else 1.0f
  }

  /**
   * Defines sugar operations of sequence of weights
   *
   * @param w __matrix sequence__ to be applied.
   */
  implicit class WeightSeqOp(w: IndexedSeq[ScalarMatrix]) {
    /**
     * Assign scalar 
     *
     * @param x __scalar__ to be assigned for every cell
     */
    @deprecated
    def :=(x: Scalar) = w.par.map(_ := 0f)

    /**
     * Assign matrices 
     * @param w2 to be assigned
     */
    def :=(w2: IndexedSeq[ScalarMatrix]) = {
      var i = w.size - 1
      while (i >= 0) {
        w(i) := w2(i)
        i -= 1
      }
    }

    /**
     * Copy these matrices
     *
     * @return copied matrices
     */
    def copy = w.par.map(_.copy).toIndexedSeq

    /**
     * Add another matrices in-place. 
     *
     * @param w2 __matrix sequence__ to be added
     */
    def :+=(w2: IndexedSeq[ScalarMatrix]) = {
      (0 until w.size).par.map(i ⇒ w(i) :+= w2(i))
      w
    }

    /**
     * Divide matrices with given scalar
     *
     * @param x __scalar__ as a divider.
     */
    def :/=(x: Scalar) = {
      w.par.map(_ :/= x)
    }
  }

  /**
   * Defines transformation of new activation function.
   *
   * @param act Activation function to be transformed.
   */
  implicit class ActivationOp(act: Activation) extends Serializable {
    /**
     * Scale Activation function = `sY * f(x * sX)`
     * @param sX scale factor along input
     * @param sY scale factor along output
     * @return new Activation Function
     */
    def *(sX: Float = 1f, sY: Float = 1f) = new Activation {
      /**
       * Compute differentiation value of this function at `f(x) = fx`
       *
       * @param fx the __output__ of this function
       * @return differentiation value at `f(x) = fx`, which should be an __square, diagonal matrix__
       */
      override def derivative(fx: ScalarMatrix): ScalarMatrix = act.derivative(fx :* sX) :* (sY * sX)

      /**
       * Compute mapping for `x`
       *
       * @param x the __input__ matrix. ''Before application, input should be summed already.''
       * @return value of `f(x)`
       */
      override def apply(x: ScalarMatrix): ScalarMatrix = act(x :* sX) :* sY

      /**
       * Serialize Activation function into String.
       * @note If this is an "object", do not modify this function.
       *       This does not supports Activation Operations defined outside of this package.
       * @return JSON object states this function
       */
      override def toJSON: JsObject = Json.obj(
        "function" → "scale",
        "base" → act.toJSON,
        "X" → sX,
        "Y" → sY
      )
    }

    /**
     * Translate Activation function = `dY + f(x - dX)`
     * @param dX shift amount along input
     * @param dY shift amount along output
     * @return new Activation Function
     */
    def +(dX: Float = 0f, dY: Float = 0f) = new Activation {
      /**
       * Compute differentiation value of this function at `f(x) = fx`
       *
       * @param fx the __output__ of this function
       * @return differentiation value at `f(x) = fx`, which should be an __square, diagonal matrix__
       */
      override def derivative(fx: ScalarMatrix): ScalarMatrix = act.derivative(fx :- dX)

      /**
       * Compute mapping for `x`
       *
       * @param x the __input__ matrix. ''Before application, input should be summed already.''
       * @return value of `f(x)`
       */
      override def apply(x: ScalarMatrix): ScalarMatrix = act(x :- dX) :+ dY

      /**
       * Serialize Activation function into String.
       * @note If this is an "object", do not modify this function.
       *       This does not supports Activation Operations defined outside of this package.
       * @return JSON object states this function
       */
      override def toJSON: JsObject = Json.obj(
        "function" → "translate",
        "base" → act.toJSON,
        "X" → dX,
        "Y" → dY
      )
    }

    /**
     * Add activation functions = `f1(x) + f2(x) ..`
     * @param others Activation functions to be added. (Variable Parameter)
     * @return new Activation Function
     */
    def +(others: Activation*) = new Activation {
      private val applySeq = others :+ act

      /**
       * Compute differentiation value of this function at `f(x) = fx`
       *
       * @param fx the __output__ of this function
       * @return differentiation value at `f(x) = fx`, which should be an __square, diagonal matrix__
       */
      override def derivative(fx: ScalarMatrix): ScalarMatrix =
        applySeq.map(_.derivative(fx)).reduce(_ :+ _)

      /**
       * Compute mapping for `x`
       *
       * @param x the __input__ matrix. ''Before application, input should be summed already.''
       * @return value of `f(x)`
       */
      override def apply(x: ScalarMatrix): ScalarMatrix =
        applySeq.map(_.apply(x)).reduce(_ :+ _)

      /**
       * Serialize Activation function into String.
       * @note If this is an "object", do not modify this function.
       *       This does not supports Activation Operations defined outside of this package.
       * @return JSON object states this function
       */
      override def toJSON: JsObject = Json.obj(
        "function" → "add",
        "base" → act.toJSON,
        "args" → others.map(_.toJSON)
      )
    }
  }
}
