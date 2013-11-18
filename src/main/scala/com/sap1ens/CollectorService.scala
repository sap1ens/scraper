package com.sap1ens

import akka.actor.{ActorLogging, Actor, Props}
import akka.routing.SmallestMailboxRouter

case class Profile(country: String, pattern: String, cities: List[String])

object CollectorService {
    import PageParser._

    case object StartScraper
    case object SaveResults
    case class PagesResults(results: List[PageResult])
    case class AddListUrl(url: String)
    case class RemoveListUrl(url: String)
}

class CollectorService(profiles: List[Profile], search: String, resultsFolder: String, resultsMode: String) extends Actor with ActorLogging with CollectionImplicits {

    import CollectorService._
    import ListParser._
    import PageParser._

    val lists = context.actorOf(Props(new ListParser(self)).withRouter(SmallestMailboxRouter(5)), name = "AdvertisementList")

    var pageResults = List[PageResult]()
    var listUrls = List[String]()

    def receive = {
        case StartScraper => {
            for(profile <- profiles; city <- profile.cities) {
                self ! AddListUrl(ParserUtil.createCityUrl(profile.pattern, search, city))
            }
        }
        case AddListUrl(url) => {
            listUrls = url :: listUrls

            lists ! StartListParser(url)
        }
        case RemoveListUrl(url) => {
            listUrls = listUrls.copyWithout(url)

            if(listUrls.isEmpty) self ! SaveResults
        }
        case PagesResults(results) => {
            pageResults = results ::: pageResults
        }
        case SaveResults => {
            log.info(s"Total results: ${pageResults.size}")

            ExcelFileWriter.write(pageResults, resultsFolder, resultsMode)

            context.system.shutdown()
        }
    }
}
