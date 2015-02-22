/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This code is a modified version of the original Spark 1.0.2 K-Means implementation.
 */


package com.massivedatascience.clusterer

import com.massivedatascience.clusterer.util.{SparkHelper, XORShiftRandom}
import org.apache.spark.{Logging, SparkContext}

import scala.collection.mutable.ArrayBuffer

/**
 *
 * The KMeans++ initialization algorithm
 *
 * @param ops distance function
 */
class KMeansPlusPlus(ops: BregmanPointOps, clusterer: MultiKMeansClusterer) extends Serializable with SparkHelper {

  /**
   * K-means++ on the weighted point set `points`. This first does the K-means++
   * initialization procedure and then rounds of Lloyd's algorithm.
   */

  def cluster(
    sc: SparkContext,
    seed: Int,
    points: Array[BregmanCenter],
    weights: Array[Double],
    k: Int): Array[BregmanCenter] = {
    val centers: Array[BregmanCenter] = getCenters(sc, seed, points, weights, k, 1)

    withNamed("points for kmeans++", sc.parallelize(points.map(ops.toPoint))) { pts =>
      val (_, finalCenters, assignments) = clusterer.cluster(ops, pts, Array(centers))
      assignments.map(_.unpersist(blocking = false))
      finalCenters
    }
  }

  /**
   * Select centers in rounds.  On each round, select 'perRound' centers, with probability of
   * selection equal to the product of the given weights and distance to the closest cluster center
   * of the previous round.
   *
   * @param sc the Spark context
   * @param seed a random number seed
   * @param candidateCenters  the candidate centers
   * @param weights  the weights on the candidate centers
   * @param k  the total number of centers to select
   * @param perRound the number of centers to add per round
   * @return   an array of at most k cluster centers
   */
  def getCenters(
    sc: SparkContext,
    seed: Int,
    candidateCenters: Array[BregmanCenter],
    weights: Array[Double],
    k: Int,
    perRound: Int): Array[BregmanCenter] = {

    require(candidateCenters.length > 0)
    require(k > 0)
    require(perRound > 0)

    if (candidateCenters.length < k)
      logWarning(s"# of clusters requested $k exceeds number of points ${candidateCenters.length}")

    val points = candidateCenters.map(ops.toPoint)
    val centers = new ArrayBuffer[BregmanCenter](k)
    val rand = new XORShiftRandom(seed)
    val newCenter = pickWeighted(rand, cumulativeWeights(weights)).map(candidateCenters(_))
    centers += newCenter.get
    logInfo(s"starting kMeansPlusPlus initialization on ${candidateCenters.length} points")

    var distances = Array.fill(candidateCenters.length)(Double.MaxValue)
    distances = updateDistances(points, distances, IndexedSeq(newCenter.get))
    var more = true
    while (centers.length < k && more) {
      val cumulative = cumulativeWeights(points.zip(distances).map { case (p, d) => p.weight * d})
      val selected = (0 until perRound).par.flatMap { _ =>
        pickWeighted(rand, cumulative)
      }
      val newCenters = selected.toArray.map(candidateCenters(_))
      distances = updateDistances(points, distances, newCenters)
      logInfo(s"chose ${newCenters.length} new centers")
      for (c <- newCenters) {
        centers += c
      }
      more = selected.nonEmpty
    }
    sideEffect(centers.take(k).toArray) { result =>
      logInfo(s"completed kMeansPlusPlus with ${result.length} centers of $k requested")
    }
  }

  /**
   * Update the distance of each point to its closest cluster center, given the cluster
   * centers that were added.
   *
   * @param points set of candidate initial cluster centers
   * @param centers new cluster centers
   * @return  points with their distance to closest to cluster center updated
   */

  def updateDistances(
    points: Array[BregmanPoint],
    distances: Array[Double],
    centers: IndexedSeq[BregmanCenter]): Array[Double] = {

    points.zip(distances).par.map { case (p, d) =>
      val dist = ops.pointCost(centers, p)
      if(dist < d) dist else d
    }.toArray
  }

  def cumulativeWeights(weights: IndexedSeq[Double]): IndexedSeq[Double] = {
    var cumulative = 0.0
    weights map { weight =>
      cumulative = cumulative + weight
      cumulative
    }
  }

  /**
   * Pick a point at random, weighing the choices by the given weight vector.
   *
   * @param rand  random number generator
   * @param cumulative  the cumulative weights of the points
   * @return the index of the point chosen
   */
  def pickWeighted(rand: XORShiftRandom, cumulative: IndexedSeq[Double]): Option[Int] = {
    val r = rand.nextDouble() * cumulative.last
    val index = cumulative.indexWhere(x => x > r)
    if( index == -1 ) None else Some(index)
  }
}
