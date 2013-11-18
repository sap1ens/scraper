package com.sap1ens

import akka.actor.{ActorRef, Actor, ActorLogging}
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

object PageParser {
    case class StartPageParser(listUrl: String, pageUrl: String)
    case class PageResult(
        link: String,
        title: String,
        description: String,
        date: Option[(String, String)] = None,
        email: Option[String] = None,
        phone: Option[String] = None
    )
}

class PageParser(listParser: ActorRef) extends Actor with ActorLogging {

    import PageParser._
    import ListParser._

    def receive = {
        case StartPageParser(listUrl, pageUrl) => {
            val future = Future {
                ParserUtil.parseAdvertisement(pageUrl)
            }.mapTo[Option[PageResult]]

            future onComplete {
                case Success(result) => {
                    listParser ! SavePageResult(listUrl, result)
                    listParser ! RemovePageUrl(listUrl, pageUrl)
                }
                case Failure(e) => {
                    log.warning(s"Can't process pageUrl, cause: ${e.getMessage}")
                    listParser ! RemovePageUrl(listUrl, pageUrl)
                }
            }
        }
    }
}
