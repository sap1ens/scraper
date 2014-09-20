name := """scraper"""

version := "1.0"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2.1",
  "com.typesafe" % "config" % "1.0.0",
  "org.jsoup" % "jsoup" % "1.7.2",
  "com.googlecode.libphonenumber" % "libphonenumber" % "5.7",
  "info.folone" % "poi-scala_2.10" % "0.9"
)
