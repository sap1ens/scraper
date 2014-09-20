Scraper
============

Scraper example built on Scala, Akka and Jsoup. It saves all results to XLS file.

To run the application, first install sbt if it's not already installed:

    brew install sbt

Then enter the command

    sbt run

If you need deployable JAR-file you can use https://github.com/sbt/sbt-assembly plugin

Settings
------------

In application.json you can find a few settings:

* **search** - Search query
* **results.folder** - Folder to save result
* **results.mode** - Can be **single** (all results will be saved to a single file) or **separate** (all results will be grouped by days, for example 01-01-2012.xls, 02-01-2012.xls, etc.)
