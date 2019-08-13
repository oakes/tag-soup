(defproject tag-soup "1.8.0-SNAPSHOT"
  :description "A library to parse code into a list of tags"
  :url "https://github.com/oakes/tag-soup"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :profiles {:dev {:main tag-soup.core}})
