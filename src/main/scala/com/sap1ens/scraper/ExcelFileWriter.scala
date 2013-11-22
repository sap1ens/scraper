package com.sap1ens.scraper

import info.folone.scala.poi._
import scala.Predef._
import info.folone.scala.poi.StringCell
import com.sap1ens.scraper.PageParser.PageResult

object ExcelFileWriter {

    def write(data: List[PageResult], path: String, mode: String) = {
        try {
            if (mode == "single") {
                createXLS(path + "/" + "all.xls", data)
            } else {
                val grouped = data.groupBy(_.date.map(_._1))

                for ((date, results) <- grouped) {
                    createXLS(path + "/" + date.getOrElse("unknown") + ".xls", results.toList)
                }
            }
        } catch {
            case x: Exception => println(x)
        }
    }

    private def createXLS(filename: String, data: List[PageResult]) = {
        var rows: Set[Row] = Set(Row(0) {
            Set(
                StringCell(0, "Title"),
                StringCell(1, "Posting Content"),
                StringCell(2, "Url"),
                StringCell(3, "Date"),
                StringCell(4, "E-mail"),
                StringCell(5, "Phone")
            )
        })

        var index = 1
        data foreach { item =>
            rows += Row(index) {
                Set(
                    StringCell(0, item.title),
                    StringCell(1, item.description),
                    StringCell(2, item.url),
                    StringCell(3, item.date.map(_._2).getOrElse("")),
                    StringCell(4, item.email.getOrElse("")),
                    StringCell(5, item.phone.getOrElse(""))
                )
            }

            index += 1
        }

        val sheet = Workbook {
            Set(Sheet("Bookkeeper posts")(rows))
        }

        sheet.safeToFile(filename).unsafePerformIO()
    }
}
