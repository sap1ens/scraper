package com.sap1ens.scraper

import scala.collection.convert.WrapAsScala._
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import org.jsoup.nodes.Document.OutputSettings
import org.jsoup.nodes.Element

trait ParserUtil {
    val ConnectionTimeout = 7000 // millis

    private final val phoneUtil = PhoneNumberUtil.getInstance()

    def findPhone(text: String): Option[String] = {
        phoneUtil.findNumbers(text, "US").toList.headOption.map(_.number.getNationalNumber.toString)
    }

    def clearText(rawText: String) = {
        var text = Jsoup.clean(rawText, "", Whitelist.none(), new OutputSettings().prettyPrint(false))
        if (text.size > 2000) text = text.substring(0, 2000) + "..."
        text.trim
    }
}

trait ParserImplicits {
    implicit class ElementExtensions(val element: Element) {
        def oneByClass(className: String): Option[Element] = element.getElementsByClass(className).toList.headOption

        def oneByTag(tagName: String): Option[Element] = element.getElementsByTag(tagName).toList.headOption

        def byId(id: String): Option[Element] = Option(element.getElementById(id))
    }
}
