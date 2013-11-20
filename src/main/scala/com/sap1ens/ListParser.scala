package com.sap1ens

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.pattern.pipe
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import akka.routing.SmallestMailboxRouter
import org.jsoup.nodes.Element
import java.net.URL
import scala.collection.mutable.ListBuffer
import org.jsoup.Jsoup
import scala.util.control.Breaks._
import scala.Some
import scala.collection.convert.WrapAsScala._

object ListParser {
    import PageParser._

    case class StartListParser(listUrl: String)
    case class ListResult(listUrl: String, pageUrls: List[String], nextPage: Option[String] = None)
    case class AddPageUrl(listUrl: String, url: String)
    case class RemovePageUrl(listUrl: String, url: String)
    case class SavePageResult(listUrl: String, result: Option[PageResult])
}

class ListParser(collectorService: ActorRef) extends Actor with ActorLogging with CollectionImplicits with ParserUtil {

    import ListParser._
    import PageParser._
    import CollectorService._

    val pages = context.actorOf(Props(new PageParser(self)).withRouter(SmallestMailboxRouter(10)), name = "Advertisement")

    var pageUrls = Map[String, List[String]]()
    var pageResults = Map[String, List[Option[PageResult]]]()

    def receive = {
        case StartListParser(listUrl) => {
            val future = parseAdvertisementList(listUrl)

            future onFailure {
                case e: Exception => {
                    log.warning(s"Can't process $listUrl, cause: ${e.getMessage}")
                    collectorService ! RemoveListUrl(listUrl)
                }
            }

            future pipeTo self
        }
        case AddPageUrl(listUrl, url) => {
            pageUrls = pageUrls.updatedWith(listUrl, List.empty) {url :: _}

            pages ! StartPageParser(listUrl, url)
        }
        case RemovePageUrl(listUrl, url) => {
            pageUrls = pageUrls.updatedWith(listUrl, List.empty) { urls =>
                val updatedUrls = urls.copyWithout(url)

                if(updatedUrls.isEmpty) {
                    pageResults.get(listUrl) map { results =>
                        collectorService ! PagesResults(results.flatten.toList)
                        collectorService ! RemoveListUrl(listUrl)
                    }
                }

                updatedUrls
            }
        }
        case SavePageResult(listUrl, result) => {
            pageResults = pageResults.updatedWith(listUrl, List.empty) {result :: _}
        }
        case ListResult(listUrl, urls, Some(nextPage)) => {
            collectorService ! AddListUrl(nextPage)

            self ! ListResult(listUrl, urls, None)
        }
        case ListResult(listUrl, urls, None) => {
            log.debug(s"${urls.size} pages were extracted")

            if(urls.isEmpty) collectorService ! RemoveListUrl(listUrl)

            urls foreach { url =>
                self ! AddPageUrl(listUrl, url)
            }
        }
    }

    // TODO: refactor
    def parseAdvertisementList(listUrl: String): Future[ListResult] = Future {

        def parseAdvertisementListItem(paragraph: Element, url: URL) = {
            val pageLink = paragraph.children.first.attr("href")
            url.getProtocol + "://" + url.getHost + pageLink
        }

        val links = new ListBuffer[String]
        var nextPage: Option[String] = None

        try {
            val doc = Jsoup.connect(listUrl).timeout(ConnectionTimeout).get()
            val wrapper = doc.getElementById("toc_rows")
            val rows = wrapper.getElementsByClass("content").get(0)

            // skip page with 0 results
            if (!rows.text.toLowerCase.contains("nothing found")) {
                val children = rows.children()

                breakable {
                    for(row: Element <- children.toList) {

                        (row.tagName, row.text.toLowerCase) match {
                            case (tagName, _) if tagName == "p" => links += parseAdvertisementListItem(row, new URL(listUrl))
                            case (tagName, text) if tagName == "h4" => {
                                if (text.contains("local results")) {
                                    break()
                                } else {
                                    nextPage = for {
                                        nextPageWrapper <- row.getElementsByClass("next").toList.headOption
                                        nextPageLink <- nextPageWrapper.getElementsByTag("a").toList.headOption
                                    } yield nextPageLink.attr("href")
                                }
                            }
                            case _ =>
                        }
                    }
                }
            }
        } catch {
            case e: Exception => log.warning(s"Can't parse list page: ${e.getMessage}")
        }

        ListResult(listUrl, links.toList, nextPage)
    }
}
