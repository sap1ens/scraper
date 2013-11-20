package com.sap1ens

import akka.actor.{ActorRef, Actor, ActorLogging}
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import org.jsoup.Jsoup
import scala.collection.convert.WrapAsScala._
import org.jsoup.nodes.Document

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

class PageParser(listParser: ActorRef) extends Actor with ActorLogging with ParserUtil {

    import PageParser._
    import ListParser._

    def receive = {
        case StartPageParser(listUrl, pageUrl) => {
            val future = parseAdvertisement(pageUrl).mapTo[Option[PageResult]]

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

    def parseAdvertisement(url: String): Future[Option[PageResult]] = Future {

        def extractPostDate(doc: Document) = {
            (for {
                dateReplyBar <- doc.getElementsByClass("dateReplyBar").toList.headOption
                postDateWrapper <- dateReplyBar.getElementsByClass("postinginfo").toList.headOption
            } yield {
                val postDate = postDateWrapper.getElementsByTag("date").toList.headOption
                val postTime = postDateWrapper.getElementsByTag("time").toList.headOption

                postDate.map(_.text).orElse(postTime.map(_.text).orElse(None)) map { date =>
                    (date.substring(0, 10), date)
                }
            }).flatten
        }

        def extractEmail(doc: Document): Option[String] = {
            for {
                dateReplyBar <- doc.getElementsByClass("dateReplyBar").toList.headOption
                email <- dateReplyBar.getElementsByTag("a").toList.headOption
            } yield {
                email.text
            }
        }

        try {
            val doc = Jsoup.connect(url).timeout(ConnectionTimeout).get()

            for {
                postTitle <- doc.getElementsByClass("postingtitle").toList.headOption
                postContent <- Option(doc.getElementById("postingbody"))
            } yield {
                PageResult(
                    url,
                    postTitle.text,
                    clearText(postContent.html),
                    extractPostDate(doc),
                    extractEmail(doc),
                    findPhone(postContent.text)
                )
            }
        } catch {
            case e: Exception => {
                log.warning(s"Skip page, cause: ${e.getMessage}")
                None
            }
        }
    }
}
