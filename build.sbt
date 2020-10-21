name := "caliban-example"

version := "0.1"

scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
  "com.github.ghostdogpr" %% "caliban" % "0.9.2",
  "com.github.ghostdogpr" %% "caliban-akka-http" % "0.9.2",
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.10",
  "com.typesafe.akka" %% "akka-stream" % "2.6.10",
  "com.typesafe.akka" %% "akka-http" % "10.2.1",
  "de.heikoseeberger" %% "akka-http-play-json" % "1.35.0",
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.1.0",
)