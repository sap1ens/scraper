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
import scala.util.{Success, Failure}

object ListParser {
    case class ListData(listUrl: String)
    case class ListResult(listUrl: String, pageUrls: List[String], nextPage: Option[String] = None)
}

class ListParser(collectorService: ActorRef) extends Actor with ActorLogging {

    import ListParser._
    import CollectorService._
    implicit val timeout = Timeout(120 seconds)

    val pages = context.actorOf(Props(new PageParser()).withRouter(SmallestMailboxRouter(20)), name = "Advertisement")

    def receive = {
        case ListData(listUrl) => {
            val future = Future {
                val (urls, nextPage) = ParserUtil.parseAdvertisementList(listUrl)
                ListResult(listUrl, urls, nextPage)
            }

            future onFailure {
                case e: Exception => {
                    log.warning(s"Can't process $listUrl, cause: ${e.getMessage}")

                    collectorService ! RemoveListUrl(listUrl)
                }
            }

            future pipeTo self
        }
        case ListResult(listUrl, pageUrls, Some(nextPage)) => {
            collectorService ! AddListUrl(nextPage)

            self ! ListResult(listUrl, pageUrls, None)
        }
        case ListResult(listUrl, pageUrls, None) => {
            log.debug(s"${pageUrls.size} pages were extracted")

            val future = Future.sequence(pageUrls.map { url =>
                pages ? PageData(url)
            }).mapTo[List[Option[PageResult]]]

            future onComplete {
                case Success(results) => {
                    collectorService ! results.flatten
                    collectorService ! RemoveListUrl(listUrl)
                }
                case Failure(e) => {
                    log.warning(s"Can't process pages of $listUrl, cause: ${e.getMessage}")
                }
            }
        }
    }
}
