package com.sap1ens

import scala.util.control.Breaks._
import org.jsoup.{HttpStatusException, Jsoup}
import org.jsoup.nodes.{Document, Element}
import scala.collection.convert.WrapAsScala._
import scala.collection.mutable.ListBuffer
import java.net.URL
import org.jsoup.safety.Whitelist
import org.jsoup.nodes.Document.OutputSettings
import com.google.i18n.phonenumbers.PhoneNumberUtil
import ListParser._
import PageParser._

object ParserUtil {
    val ResultsElementId = "toc_rows"
    val ResultsListClass = "content"
    val ConnectionTimeout = 10000 // millis

    val phoneUtil = PhoneNumberUtil.getInstance()

    def parseAdvertisement(url: String): Option[PageResult] = {
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
                    extractPhone(postContent.text)
                )
            }
        } catch {
            case e: Exception => {
                println(s"Skip page, cause: ${e.getMessage}") // change to log.warning
                None
            }
        }
    }

    def extractPostDate(doc: Document) = {
        for {
            dateReplyBar <- doc.getElementsByClass("dateReplyBar").toList.headOption
            postDateWrapper <- dateReplyBar.getElementsByClass("postinginfo").toList.headOption
            postDate <- postDateWrapper.getElementsByTag("date").toList.headOption
        } yield {
            val date = postDate.text
            PageDateData(date.substring(0, 10), date)
        }
    }

    def extractEmail(doc: Document): Option[String] = {
        for {
            dateReplyBar <- doc.getElementsByClass("dateReplyBar").toList.headOption
            email <- dateReplyBar.getElementsByTag("a").toList.headOption
        } yield {
            email.text
        }
    }

    def extractPhone(description: String): Option[String] = {
        phoneUtil.findNumbers(description, "US").toList.headOption.map(_.number.getNationalNumber.toString)
    }

    // TODO: can be refactored
    def parseAdvertisementList(urlString: String) = {
        val links = new ListBuffer[String]
        var nextPage: Option[String] = None

        try {
            val doc = Jsoup.connect(urlString).timeout(ConnectionTimeout).get()
            val wrapper = doc.getElementById(ResultsElementId)
            val rows = wrapper.getElementsByClass(ResultsListClass).get(0)

            // skip page with 0 results
            if (!rows.text.toLowerCase.contains("nothing found")) {
                val children = rows.children()

                breakable {
                    for(row: Element <- children.toList) {

                        (row.tagName, row.text.toLowerCase) match {
                            case (tagName, _) if tagName == "p" => links += parseAdvertisementListItem(row, new URL(urlString))
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
            case e: Exception => println(e)
        }

        (links.toList, nextPage)
    }

    def parseAdvertisementListItem(paragraph: Element, url: URL) = {
        val pageLink = paragraph.children.first.attr("href")
        url.getProtocol + "://" + url.getHost + pageLink
    }

    def createCityUrl(pattern: String, search: String, city: String) = {
        pattern
            .replace("{search}", search)
            .replace("{city}", city)
    }

    def clearText(rawText: String) = {
        var text = Jsoup.clean(rawText, "", Whitelist.none(), new OutputSettings().prettyPrint(false))
        if (text.size > 2000) text = text.substring(0, 2000) + "..."

        text.trim
    }
}
