package com.sap1ens.scraper

trait CollectionImplicits {
    implicit class ListExtensions[K](val list: List[K]) {
        def copyWithout(item: K) = {
            val (left, right) = list span (_ != item)
            left ::: right.drop(1)
        }
    }

    implicit class MapExtensions[K, V](val map: Map[K, V]) {
        def updatedWith(key: K, default: V)(f: V => V) = {
            map.updated(key, f(map.getOrElse(key, default)))
        }
    }
}
