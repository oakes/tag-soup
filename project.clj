(defproject tag-soup "1.6.1-SNAPSHOT"
  :description "A library to parse code into a list of tags"
  :url "https://github.com/oakes/tag-soup"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :plugins [[lein-tools-deps "0.4.3"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :profiles {:dev {:main tag-soup.core}})
