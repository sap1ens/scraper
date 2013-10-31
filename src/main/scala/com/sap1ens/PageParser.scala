package com.sap1ens

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import ExecutionContext.Implicits.global
import java.net.URL

object PageParser {
    case class PageData(pageUrl: String)
    case class PageDateData(shortDate: String, fullDate: String)
    case class PageResult(
        link: String,
        title: String,
        description: String,
        date: Option[PageDateData] = None,
        email: Option[String] = None,
        phone: Option[String] = None
    )
}

class PageParser extends Actor with ActorLogging {

    import PageParser._

    def receive = {
        case PageData(pageUrl) => {
            val future = Future {
                ParserUtil.parseAdvertisement(pageUrl)
            }

            future onFailure {
                case e: Exception => log.warning(s"Can't process $pageUrl, cause: ${e.getMessage}")
            }

            future pipeTo sender
        }
    }
}
