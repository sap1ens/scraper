package com.sap1ens

import scala.collection.convert.WrapAsScala._
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import org.jsoup.nodes.Document.OutputSettings

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
