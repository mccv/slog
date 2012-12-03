name := "slog"

version := "0.1-SNAPSHOT"

scalaVersion := "2.9.2"

resolvers += "twitter" at "http://maven.twttr.com"

libraryDependencies += "org.parboiled" %% "parboiled-scala" % "1.1.3"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test"

libraryDependencies += "com.github.scopt" %% "scopt" % "2.1.0"

libraryDependencies += "com.twitter" % "ostrich" % "9.0.2"

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)
