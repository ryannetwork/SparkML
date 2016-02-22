import org.apache.spark.mllib.classification
import org.apache.spark.mllib.classification._
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.DecisionTree
import org.apache.spark.mllib.tree.configuration.Algo
import org.apache.spark.mllib.tree.impurity.Entropy
import org.apache.spark.mllib.tree.model.DecisionTreeModel
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkContext, SparkConf}


/**
  * Created by vishalkuo on 2016-02-13.
  */
object Classification {
  val conf = new SparkConf().setMaster("local[2]")
    .setAppName("StumbleUpon Classifier").set("spark.executor.memory", "2g")
  val iterations = 10
  val treeDepth = 5
  val sc = new SparkContext(conf)
  def main(args: Array[String]) {
    val rawData = sc.textFile("src/main/resources/datasets/StumbleUpon/train.tsv")
    val fields = rawData.map(lines => lines.split("\t"))
    val cleansedData = fields.map { field =>
      val cleansed = field.map(_.replace("\"", ""))
      val classifiedVal = cleansed(field.length - 1).toInt
      val features = cleansed.slice(4, field.length - 1).map(item => if (item.equals("?")) 0.0 else item.toDouble)
      (classifiedVal, features)
    }

    val mllibData = cleansedData.map { case (classifiedVal, features) =>
      LabeledPoint(classifiedVal, Vectors.dense(features))
    }
    val naiveBayesData = cleansedData.map { case (label, feature) =>
      val positiveFeature = feature.map(x => if (x < 0) 0 else x)
      LabeledPoint(label, Vectors.dense(positiveFeature))
    }
//    mllibData.cache()
    naiveBayesData.cache()
//    naiveBayes(naiveBayesData)

//    val logReg = logisticRegression(mllibData)
//    val svmModel = svm(mllibData)
//    val comp = lrSVMComparison(logReg, svmModel, mllibData)
    val nb = naiveBayes(naiveBayesData)
    naiveBayesMetrics(nb, naiveBayesData)
  }

  def logisticRegression(data: RDD[LabeledPoint]): classification.LogisticRegressionModel= {
    val model = LogisticRegressionWithSGD.train(data,iterations)
    val correctCount = data.map(lPoint =>
    if (model.predict(lPoint.features) == lPoint.label) 1 else 0).sum
    val acc = correctCount / data.count()
    println(acc)
    model
  }

  def svm(data: RDD[LabeledPoint]): SVMModel = {
    val model = SVMWithSGD.train(data, iterations)
    val correctCount = data.map(lPoint =>
      if (model.predict(lPoint.features) == lPoint.label) 1 else 0).sum
    val acc = correctCount / data.count()
    println(acc)
    model
  }

  def naiveBayes(data: RDD[LabeledPoint]): NaiveBayesModel = {
    val model = NaiveBayes.train(data)
    val correctCount = data.map(lPoint =>
      if (model.predict(lPoint.features) == lPoint.label) 1 else 0).sum
    val acc = correctCount / data.count()
    println(acc)
    model
  }

  def decisionTree(data: RDD[LabeledPoint]): DecisionTreeModel = {
    val model = DecisionTree.train(data, Algo.Classification, Entropy, treeDepth)
    val correctCount = data.map(lPoint =>
      if (model.predict(lPoint.features) == lPoint.label) 1 else 0).sum
    val acc = correctCount / data.count()
    println(acc)
    model
  }

  def lrSVMComparison(logReg: classification.LogisticRegressionModel, svmModel: SVMModel, mllibData: RDD[LabeledPoint]):
  Seq[(String, Double, Double)] = {
    val comparison = Seq(logReg, svmModel).map{case model =>
      val accAndCategory = mllibData.map{case point =>
        (model.predict(point.features),  point.label)
      }
      val metric = new BinaryClassificationMetrics(accAndCategory)
      (model.getClass.getSimpleName, metric.areaUnderPR(), metric.areaUnderROC())
    }
    comparison
  }

  def naiveBayesMetrics(nb: NaiveBayesModel, nbData: RDD[LabeledPoint]):Seq[(String, Double, Double)] = {
    val comparison = Seq(nb).map({case model =>
        val accAndCategory = nbData.map({ case point =>
          (model.predict(point.features), point.label)
        })
        val met = new BinaryClassificationMetrics(accAndCategory)
      (model.getClass.getSimpleName, met.areaUnderPR(), met.areaUnderROC())
    })
    comparison
  }

  def dTreeMetrics(dtModel: DecisionTreeModel, mllibData: RDD[LabeledPoint]): Seq[(String, Double, Double)] = {
    val comparison = Seq(dtModel).map({case model =>
        val accAndCategory = mllibData.map{case point =>
          val res = model.predict(point.features)
          (if (res > 0.5) 1.0 else 0, point.label)
        }
      val met = new BinaryClassificationMetrics(accAndCategory)
      (model.getClass.getSimpleName, met.areaUnderPR(), met.areaUnderROC())
    })
    comparison
  }

}
