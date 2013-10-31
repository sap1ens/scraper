package com.sap1ens

import akka.actor.{ActorLogging, Actor, Props}
import akka.pattern.pipe
import akka.pattern.ask
import akka.routing.SmallestMailboxRouter
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import akka.util.Timeout
import scala.concurrent.duration._
import akka.agent.Agent
import scala.collection.mutable.ListBuffer

case class Profile(country: String, pattern: String, cities: List[String])

object CollectorService {
    case object StartScraper
    case object SaveResults
}

class CollectorService(profiles: List[Profile], search: String, resultsFolder: String, resultsMode: String) extends Actor with ActorLogging {

    import CollectorService._
    import ListParser._
    import PageParser._

    val lists = context.actorOf(Props(new ListParser(self)).withRouter(SmallestMailboxRouter(10)), name = "AdvertisementList")

    implicit val timeout = Timeout(120 seconds)

    val results = Agent(ListBuffer[PageResult]())
    val listsCounter = Agent(0)

    val listLinks = for(profile <- profiles; city <- profile.cities) yield ParserUtil.createCityUrl(profile.pattern, search, city)

    def receive = {
        case StartScraper => {
            Future.sequence(listLinks.map { link =>
                lists ? ListData(link)
            }) pipeTo sender
        }
        case result: List[PageResult] => {
            results send {_ ++= result}
            listsCounter send {_ + 1}

            if(listsCounter.get().toInt == listLinks.size) {
                self ! SaveResults
            }
        }
        case SaveResults => {
            val data = results.get().toList
            log.debug(s"Total results: ${data.size}")

            ExcelFileWriter.write(data, resultsFolder, resultsMode)

            context.system.shutdown()
        }
    }
}
