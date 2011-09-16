sbtPlugin := true

name := "sbt-ghpages-plugin"

organization := "com.typesafe"

version <<= (sbtVersion)("0.1.0-%s".format(_))
