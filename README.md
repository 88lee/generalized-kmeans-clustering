Generalized K-Means Clustering
=============================

This project generalizes the Spark MLLIB K-Means (v1.1.0) clusterer to support clustering of sparse
or dense data using distances defined by
[Bregman divergences](http://www.cs.utexas.edu/users/inderjit/public_papers/bregmanclustering_jmlr.pdf) and
[generalized symmetrized Bregman Divergences] (http://www-users.cs.umn.edu/~banerjee/papers/13/bregman-metric.pdf).


### Usage

The simplest way to call the clusterer is to use the ```KMeans``` object.

```scala
  object KMeans {
    def train(data: RDD[Vector], k: Int, maxIterations: Int, runs: Int, mode: String,
      distanceFunction: String): KMeansModel = ???
  }
```

For greater control, you may provide your own distance function by using the lower level interface.
See the implementation of ```KMeans.train``` for an example.


### Bregman Divergences

The Spark MLLIB clusterer is good at one thing: clustering data using Euclidean distance as the metric into
a fixed number of clusters.  However, there are many interesting distance functions other than Euclidean distance.
It is far from trivial to adapt the Spark MLLIB clusterer to these other distance functions. In fact, recent
modification to the Spark implementation have made it even more difficult.

This project decouples the distance function from the clusterer implementation, allowing the end-user the opportunity
to define an alternative distance function in just a few lines of code.

The most general class of distance functions that work with the K-Means algorithm are called Bregman divergences.
This project implements several Bregman divergences, including the squared Euclidean distance,
the [Kullback-Leibler divergence](http://en.wikipedia.org/wiki/Kullback%E2%80%93Leibler_divergence),
the logistic loss divergence, the Itakura-Saito divergence, and the generalized I-divergence.

The ```BregmanDivergence``` trait encapsulates the Bregman Divergence definition.

```scala
trait BregmanDivergence {

  /**
   * F is any convex function.
   *
   * @param v input
   * @return F(v)
   */
  def F(v: Vector): Double

  /**
   * Gradient of F
   *
   * @param v input
   * @return  gradient of F when at v
   */
  def gradF(v: Vector): Vector

  /**
   * F applied to homogeneous coordinates.
   *
   * @param v input
   * @param w weight
   * @return  F(v/w)
   */
  def F(v: Vector, w: Double): Double

  /**
   * Gradient of F, applied to homogeneous coordinates
   * @param v input
   * @param w weight
   * @return  gradient(v/w)
   */
  def gradF(v: Vector, w: Double): Vector
}
```

Several Bregman Divergences are provided:

```scala
/**
 * The squared Euclidean distance function is defined on points in R**n
 *
 * http://en.wikipedia.org/wiki/Euclidean_distance
 */
trait SquaredEuclideanDistanceDivergence extends BregmanDivergence

/**
 * The Kullback-Leibler divergence is defined on points on a simplex in R+ ** n
 *
 * If we know that the points are on the simplex, then we may simplify the implementation
 * of KL divergence.  This trait implements that simplification.
 *
 * http://en.wikipedia.org/wiki/Kullback%E2%80%93Leibler_divergence
 *
 */
trait KullbackLeiblerSimplexDivergence extends BregmanDivergence
/**
 * The generalized Kullback-Leibler divergence is defined on points on R+ ** n
 *
 * http://en.wikipedia.org/wiki/Kullback%E2%80%93Leibler_divergence
 *
 */
trait KullbackLeiblerDivergence extends BregmanDivergence

/**
 * The generalized I-Divergence is defined on points in R**n
 */
trait GeneralizedIDivergence extends BregmanDivergence

/**
 * The Logistic loss divergence is defined on points in (0.0,1.0)
 *
 * Logistic loss is the same as KL Divergence with the embedding into R**2
 *
 *    x => (x, 1.0 - x)
 */
trait LogisticLossDivergence extends BregmanDivergence with GeneralLog
/**
 * The Itakura-Saito Divergence is defined on points in R+ ** n
 *
 * http://en.wikipedia.org/wiki/Itakura%E2%80%93Saito_distance
 */
trait ItakuraSaitoDivergence extends BregmanDivergence

```

### From Bregman Divergences to Point Operations

Bregman divergences define distances, while ```PointOps``` implement fast
method for computing distances.  ```PointOps``` take advantage of the characteristics of the
data to define the fastest methods for evaluating Bregman divergences.


```scala
trait BregmanPointOps extends PointOps[BregmanPoint, BregmanCenter] with ClusterFactory {
  this: BregmanDivergence =>
  val weightThreshold = 1e-4
  val distanceThreshold = 1e-8
  def distance(p: BregmanPoint, c: BregmanCenter): Double = ???
  def homogeneousToPoint(h: Vector, weight: Double): BregmanPoint = ???
  def inhomogeneousToPoint(inh: Vector, weight: Double): BregmanPoint = ???
  def toCenter(v: WeightedVector): BregmanCenter = ???
  def toPoint(v: WeightedVector): BregmanPoint =  ???
  def centerMoved(v: BregmanPoint, w: BregmanCenter): Boolean = ???
}
```

Several singleton point operations are predefined, including:

```scala
  object KMeans {
    val RELATIVE_ENTROPY = ???
    val DISCRETE_KL = ???
    val SPARSE_SMOOTHED_KL = ???
    val DISCRETE_SMOOTHED_KL = ???
    val GENERALIZED_SYMMETRIZED_KL = ???
    val EUCLIDEAN = ???
    val SPARSE_EUCLIDEAN = ???
    val LOGISTIC_LOSS = ???
    val GENERALIZED_I = ???
  }
```

Pull requests offering additional distance functions (http://en.wikipedia.org/wiki/Bregman_divergence) are welcome.

### Variable number of clusters

The second major deviation between this implementation and the Spark implementation is that this clusterer may produce
fewer than `k` clusters when `k` are requested.  This may sound like a problem, but your data may not cluster into `k` clusters!
The Spark implementation duplicates cluster centers, resulting in useless computation.  This implementation
tracks the number of cluster centers. 

### Plugable seeding algorithm

The third major difference between this implementation and the Spark implementation is that this clusterer
separates the initialization step (the seeding of the initial clusters) from the main clusterer.
This allows for new initialization methods beyond the standard "random" and "K-Means ||" algorithms,
including initialization methods that have different numbers of initial clusters.

There are two pre-defined seeding algorithms.

```scala
  object KMeans {
    val RANDOM = ???
    val K_MEANS_PARALLEL = ???
  }
```

You may provide alternative seeding algorithms using the lower level interface as shown in ```KMeans.train```.

### Faster K-Means || implementation  

The fourth major difference between this implementation and the Spark implementation is that this clusterer
uses the K-Means clustering step in the [K-Means || initialization](http://theory.stanford.edu/~sergei/papers/vldb12-kmpar.pdf) process.
This is much faster, since all cores are utilized versus just one.

Additionally, this implementation performs the implementation in time quadratic in the number of cluster, whereas the Spark implementation takes time cubic in the number of clusters.

### Sparse Data

The fifth major difference between this implementation and the Spark implementation is that this clusterer
works well on sparse input data of high dimension.  Note, some distance functions are not defined on
sparse data (i.e. Kullback-Leibler).  However, one can approximate those distance functions to
achieve similar results.  This implementation provides such approximations.

### Internals

The key is to create three new abstractions: point, cluster center, and centroid.  The base implementation constructs
centroids incrementally, then converts them to cluster centers.  The initialization of the cluster centers converts
points to cluster centers.  These abstractions are easy to understand and easy to implement.

### Scalability and Testing

This clusterer has been used to cluster millions of points in 700+ dimensional space using an information theoretic distance
function (Kullback-Leibler). 




