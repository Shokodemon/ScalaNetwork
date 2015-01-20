package kr.ac.kaist.ir.deep.train

import kr.ac.kaist.ir.deep.fn._
import kr.ac.kaist.ir.deep.train.op.{InputOp, ScalarVector}
import kr.ac.kaist.ir.deep.train.style.TrainStyle
import org.apache.log4j.Logger
import org.apache.spark.rdd.RDD

import scala.annotation.tailrec


/**
 * __General__ Trainer Implementation.
 *
 * This class trains with help of Training Style and Input Operation.
 *
 * @note This trainer is generalized class. Further implementation, you should see several styles.       
 * @example
 * {{{val net:Network = ...
 *
 *    // Define Training Style. SingleThreadTrainStyle vs DistBeliefTrainStyle
 *    val style = new SingleThreadTrainStyle[ScalarMatrix](
 *    net = net,
 *    algorithm = new StochasticGradientDescent(l2decay = 0.0001),
 *    param = SimpleTrainingCriteria(miniBatch = 8))
 *
 *    // Define Input Operation. ScalarVector vs TreeRAE vs TreeRecursive
 *    val operation = new ScalarVector(
 *    corrupt = GaussianCorruption(variance = 0.1)
 *    )
 *
 *    // Define Trainer
 *    val train = new Trainer(
 *    style = style,
 *    make = operation,
 *    stops = StoppingCriteria(maxIter = 100000))
 *
 *    // Do Train
 *    train.train(set, valid)}}}
 *
 * @note To train an autoencoder, you can provide same training set as validation set.
 *
 * @param style __Training style__ that supervises how to train. There are two styles,
 *              one is [[kr.ac.kaist.ir.deep.train.style.SingleThreadTrainStyle]]
 *              and the other is [[kr.ac.kaist.ir.deep.train.style.DistBeliefTrainStyle]].
 * @param make __Input Operation__ that supervises how to manipulate input as matrices.
 *             This also controls how to compute actual network.             
 * @param stops __Stopping Criteria__ that controls the threshold for stopping. (Default : [[StoppingCriteria]])
 *
 * @tparam IN the type of input. 
 *            Currently, [[kr.ac.kaist.ir.deep.fn.ScalarMatrix]] and [[kr.ac.kaist.ir.deep.rec.VectorTree]] are supported
 */
class Trainer[IN](protected val style: TrainStyle[IN],
                  protected[train] val make: InputOp[IN] = new ScalarVector(),
                  protected val stops: StoppingCriteria = StoppingCriteria())
  extends Serializable {
  /** import everything in the style */

  import style._

  /** Logger */
  @transient protected val logger = Logger.getLogger(this.getClass)
  /** Validation Set */
  protected var testSet: Int ⇒ Seq[(IN, ScalarMatrix)] = null
  /** Best Parameter History */
  @transient protected var bestParam: Seq[ScalarMatrix] = null
  /** Best Loss Iteration Number */
  @transient protected var bestIter: Int = 0

  /**
   * Train given sequence, and validate with given sequence.
   *
   * @param set __Random Sequence Generator__ of training set
   * @return Training error (loss)
   */
  def train(set: Int ⇒ Seq[(IN, ScalarMatrix)]): Scalar = train(set, set)

  /**
   * Train given sequence, and validate with given sequence.
   *
   * @param set Full Sequence of training set
   * @return Training error (loss)
   */
  def train(set: Seq[(IN, ScalarMatrix)]): Scalar = {
    val index = () ⇒ Math.floor(Math.random() * set.size).toInt
    val randomizer = (n: Int) ⇒ (0 until n) map { _ ⇒ set(index())}
    train(randomizer, randomizer)
  }

  /**
   * Train given sequence, and validate with another sequence.
   *
   * @param set Full Sequence of training set
   * @param validation Full Sequence of validation set
   * @return Training error (loss)
   */
  def train(set: Seq[(IN, ScalarMatrix)],
            validation: Seq[(IN, ScalarMatrix)]): Scalar = {
    val index = () ⇒ Math.floor(Math.random() * set.size).toInt
    val randomizer = (n: Int) ⇒ (0 until n) map { _ ⇒ set(index())}
    val topN = (n: Int) ⇒ validation.slice(0, n)
    train(randomizer, topN)
  }

  /**
   * Train given sequence, and validate with another sequence.
   *
   * @param set __Randomized Sequence Generator__ of training set
   * @param validation Sequence Generator of validation set
   * @return Training error (loss)
   */
  def train(set: Int ⇒ Seq[(IN, ScalarMatrix)],
            validation: Int ⇒ Seq[(IN, ScalarMatrix)]) = {
    trainingSet = set
    testSet = validation

    saveParams()
    val err = trainBatch()
    restoreParams()
    printValidation()

    err
  }

  /**
   * Train using given RDD sequence. 
   *
   * @param set RDD of training set
   */
  def train(set: RDD[(IN, ScalarMatrix)]): Scalar = {
    set.cache()
    train(set.takeSample(true, _))
  }

  /**
   * Train using given RDD sequence. 
   *
   * @param set RDD of training set
   * @param validation RDD of validation set
   */
  def train(set: RDD[(IN, ScalarMatrix)], validation: RDD[(IN, ScalarMatrix)]): Scalar = {
    set.cache()
    validation.cache()
    train(set.takeSample(true, _), validation.takeSample(true, _))
  }
  
  /**
   * Calculate validation error
   *
   * @return validation error
   */
  protected def validationError() = {
    val t = testSet(param.validationSize)
    t.foldLeft(0.0) {
      (err, item) ⇒
        val out = make onewayTrip(net, item._1)
        logger.debug(s"${make stringOf item} = OUT : ${out.mkString}")
        err + (make error(item._2, out))
    } / t.size
  }

  /**
   * Print validation result into logger
   */
  protected def printValidation() = {
    logger.info(s"BEST ITERATION $bestIter : W = ${net.W map (_.mkString) mkString " | "}")

    val t = testSet(param.validationSize)
    t.par foreach {
      item ⇒
        val out = make onewayTrip(net, item._1)
        logger.info(s"${make stringOf item} = OUT : ${out.mkString}")
    }
  }

  /**
   * Store best parameters
   */
  protected final def saveParams() = {
    bestParam = net.W.copy
  }

  /**
   * Restore best parameters
   */
  protected final def restoreParams() = {
    net.W := bestParam
  }

  /**
   * Tail Recursive : Train each batch
   *
   * @param iter current iteration
   * @param prevloss previous loss
   * @param patience current patience
   * @return Total Loss when train is finished
   */
  @tailrec
  protected final def trainBatch(iter: Int = 0,
                                 prevloss: Double = Double.MaxValue,
                                 patience: Int = stops.patience): Scalar = {
    fetch(iter)
    batch(make)
    update(iter)

    var nPatience = patience

    val nLoss = if ((iter + 1) % stops.validationFreq == 0) {
      logger.debug(s"ITERATION $iter : W = ${net.W map (_.mkString) mkString " | "}")
      val train = validationError()
      val weight = algorithm loss net.W
      if (train + weight < prevloss * stops.improveThreshold) {
        nPatience = Math.max(patience, iter * stops.patienceStep)
        bestIter = iter
        saveParams()
        logger.info(f"Iteration $iter%6d, Validation = $train%.5f, WeightLoss = $weight%.5f")
        train + weight
      } else {
        prevloss
      }
    } else {
      prevloss
    }

    if (iter < stops.maxIter && nPatience > iter && nLoss > stops.lossThreshold) {
      trainBatch(iter + 1, nLoss, nPatience)
    } else {
      logger.info(f"Finished $iter%6d, Error = $nLoss%.5f")
      nLoss
    }
  }

}