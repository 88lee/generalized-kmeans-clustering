// Basic facts
name := "massivedatascience-clusterer"

organization := "com.massivedatascience"

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.5")

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

scalacOptions in (Compile, compile) += "-Xfatal-warnings"

javacOptions ++= Seq(
  "-source", "1.7",
  "-target", "1.7")

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }


libraryDependencies ++= Seq(
  "joda-time" % "joda-time" % "2.2",
  "org.joda" % "joda-convert" % "1.6",
  // test dependencies
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

scalariformSettings

// site
site.settings

site.includeScaladoc()

ghpages.settings

git.remoteRepo := "git@github.com:derrickburns/generalized-kmeans-clustering.git"

sparkPackageName := "derrickburns/generalized-kmeans-clustering"

sparkVersion := "1.2.0" // the Spark Version your package depends on.

sparkComponents += "mllib" // creates a dependency on spark-mllib.

testOptions in Test += Tests.Argument("-Dlog4j.configuration=log4j.properties")