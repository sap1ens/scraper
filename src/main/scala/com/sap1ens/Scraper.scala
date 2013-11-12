package com.sap1ens

import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import scala.collection.convert.WrapAsScala._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import com.sap1ens.CollectorService._
import com.sap1ens.PageParser._

object Scraper extends App {

    val config = ConfigFactory.load()
    val system = ActorSystem("craigslist-scraper-system")

    val profiles = Profile(
        "US", config.getString("profile.US.pattern"), config.getStringList("profile.US.cities").toList
    ) :: Profile(
        "CA", config.getString("profile.CA.pattern"), config.getStringList("profile.CA.cities").toList
    ) :: Nil

    val searchString = config.getString("search")
    val resultsFolder = config.getString("results.folder")
    val resultsMode = config.getString("results.mode")

    val collectorService = system.actorOf(Props(new CollectorService(profiles, searchString, resultsFolder, resultsMode)), "CollectorService")
    collectorService ! StartScraper
}
