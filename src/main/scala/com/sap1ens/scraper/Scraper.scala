package com.sap1ens.scraper

import akka.actor.{Props, ActorSystem}
import com.typesafe.config.{ConfigObject, ConfigFactory}
import collection.JavaConversions._
import collection.JavaConverters._
import com.sap1ens.scraper.CollectorService.StartScraper

object Scraper extends App {

    val config = ConfigFactory.load()
    val system = ActorSystem("craigslist-scraper-system")

    val profiles = for {
        profile: ConfigObject <- config.getObjectList("profiles").asScala
    } yield Profile(
        profile.get("country").unwrapped().toString,
        profile.get("pattern").unwrapped().toString,
        profile.get("cities").unwrapped().asInstanceOf[java.util.ArrayList[String]].toList
    )

    val searchString = config.getString("search")
    val resultsFolder = config.getString("results.folder")
    val resultsMode = config.getString("results.mode")

    val collectorService = system.actorOf(Props(new CollectorService(profiles.toList, searchString, resultsFolder, resultsMode)), "CollectorService")
    collectorService ! StartScraper
}
