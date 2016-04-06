(defproject tag-soup "1.1.0-SNAPSHOT"
  :description "A library to parse code into a list of tags"
  :url "https://github.com/oakes/mistakes-were-made"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :dependencies [[org.clojars.oakes/tools.reader "1.0.0-2016.04.06"
                  :exclusions [org.clojure/clojure]]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"
                  :exclusions [org.clojure/tools.reader]]
                 [org.clojure/core.async "0.2.374"
                  :exclusions [org.clojure/tools.reader]]
                 [prismatic/schema "0.4.3"]]
  :profiles {:uberjar {:prep-tasks ["compile" ["cljsbuild" "once"]]}}
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :plugins [[lein-cljsbuild "1.1.2"]]
  :cljsbuild {:builds {:main {:source-paths ["src"]
                              :compiler {:output-to "resources/public/tag-soup.js"
                                         :optimizations :advanced
                                         :pretty-print false}
                              :jar true}}}
  :main tag-soup.core)
