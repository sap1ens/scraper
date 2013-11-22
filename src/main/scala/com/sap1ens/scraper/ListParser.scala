package com.sap1ens.scraper

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.pattern.pipe
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import akka.routing.SmallestMailboxRouter
import org.jsoup.nodes.Element
import java.net.URL
import org.jsoup.Jsoup
import scala.Some

object ListParser {
    import PageParser._

    case class StartListParser(listUrl: String)
    case class ListResult(listUrl: String, pageUrls: List[String], nextPage: Option[String] = None)
    case class AddPageUrl(listUrl: String, url: String)
    case class RemovePageUrl(listUrl: String, url: String)
    case class SavePageResult(listUrl: String, result: Option[PageResult])
}

class ListParser(collectorService: ActorRef) extends Actor with ActorLogging with CollectionImplicits with ParserUtil with ParserImplicits {

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

    def parseAdvertisementList(listUrl: String): Future[ListResult] = Future {

        def rowsIterator(urls: List[String], nextPage: Option[String], row: Element): (Option[String], List[String]) = {

            def parseAdvertisementListItem(paragraph: Element, url: URL) = {
                val pageUrl = paragraph.children.first.attr("href")
                url.getProtocol + "://" + url.getHost + pageUrl
            }

            def rowCursor(newUrls: List[String], nextFound: Option[String]) = {
                Option(row.nextElementSibling()) map { nextRow => {
                    rowsIterator(newUrls, nextFound, nextRow)
                }} getOrElse {
                    (nextFound, newUrls)
                }
            }

            (row.tagName, row.text.toLowerCase) match {
                case (tagName, _) if tagName == "p" => {
                    val newUrls = parseAdvertisementListItem(row, new URL(listUrl)) :: urls

                    rowCursor(newUrls, nextPage)
                }
                case (tagName, text) if tagName == "h4" => {
                    if (text.contains("local results")) {
                        (nextPage, urls)
                    } else {
                        val nextFound = for {
                            nextPageWrapper <- row.oneByClass("next")
                            nextPageUrl <- nextPageWrapper.oneByTag("a")
                        } yield nextPageUrl.attr("href")

                        rowCursor(urls, nextFound)
                    }
                }
                case _ => {
                    rowCursor(urls, nextPage)
                }
            }
        }

        val emptyResult = ListResult(listUrl, List.empty, None)

        try {
            val doc = Jsoup.connect(listUrl).timeout(ConnectionTimeout).get()

            val results: Option[ListResult] = doc.byId("toc_rows").map(wrapper => {
                wrapper.oneByClass("content").map(content => {
                    val rows = content.children()
                    
                    // skip page with 0 results
                    if (rows.size() > 0 && !content.text.toLowerCase.contains("nothing found")) {
                        val (nextPage, urls) = rowsIterator(List[String](), None, rows.get(0))
                        Some(ListResult(listUrl, urls, nextPage))
                    } else {
                        Some(emptyResult)
                    }
                }).flatten
            }).flatten

            results.map(r => r).getOrElse(emptyResult)

        } catch {
            case e: Exception => {
                log.warning(s"Can't parse list page: ${e.getMessage}")

                emptyResult
            }
        }
    }
}
