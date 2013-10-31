package com.sap1ens

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.pattern.pipe
import akka.pattern.ask
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import akka.util.Timeout
import scala.concurrent.duration._
import akka.routing.SmallestMailboxRouter
import com.sap1ens.PageParser.{PageResult, PageData}

object ListParser {
    case class ListData(listUrl: String, pageLinks: List[String] = List.empty)
    case class ListResult(pageLinks: List[String], nextPage: Option[String] = None)
}

class ListParser(collectorService: ActorRef) extends Actor with ActorLogging {

    import ListParser._
    implicit val timeout = Timeout(120 seconds)

    val pages = context.actorOf(Props(new PageParser()).withRouter(SmallestMailboxRouter(20)), name = "Advertisement")

    def receive = {
        case ListData(listUrl, pageLinks) => {
            val future = Future {
                val (links, nextPage) = ParserUtil.parseAdvertisementList(listUrl)
                ListResult(pageLinks ::: links, nextPage)
            }

            future onFailure {
                case e: Exception => log.warning(s"Can't process $listUrl, cause: ${e.getMessage}")
            }

            future pipeTo self
        }
        case ListResult(pageLinks, Some(nextPage)) => {
            self ! ListData(nextPage, pageLinks)
        }
        case ListResult(pageLinks, None) => {
            log.debug(s"${pageLinks.size} pages were extracted")

            Future.sequence(pageLinks.map { link =>
                pages ? PageData(link)
            }).mapTo[List[Option[PageResult]]] map { _.flatten } pipeTo collectorService
        }
    }
}
