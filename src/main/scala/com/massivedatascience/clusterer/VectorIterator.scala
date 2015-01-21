package com.massivedatascience.clusterer

import org.apache.spark.mllib.linalg.{DenseVector, SparseVector, Vector}


trait VectorIterator {
  def hasNext: Boolean

  def forward(): Unit

  def index: Int

  def value: Double

  val underlying: Vector
}

class SparseVectorIterator(val underlying: SparseVector) extends VectorIterator {
  var i = 0

  def hasNext: Boolean = i < underlying.values.length

  def forward(): Unit = i = i + 1

  def index: Int = underlying.indices(i)

  def value: Double = underlying.values(i)
}

class DenseVectorIterator(val underlying: DenseVector) extends VectorIterator {
  var i = 0

  def hasNext: Boolean = i < underlying.values.length

  def forward(): Unit = i = i + 1

  def index: Int = i

  def value: Double = underlying.values(i)
}

class NegativeSparseVectorIterator(underlying: SparseVector) extends SparseVectorIterator(underlying) {
  override def value: Double = -underlying.values(i)
}

class NegativeDenseVectorIterator(underlying: DenseVector) extends DenseVectorIterator(underlying) {
  override def value: Double = -underlying.values(i)
}