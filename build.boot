(set-env!
  :source-paths #{"src"}
  :resource-paths #{"src"}
  :dependencies '[[org.clojars.oakes/tools.reader "1.0.0-2016.07.01"
                   :exclusions [org.clojure/clojure]]
                  [org.clojure/clojure "1.9.0-alpha11"]
                  [org.clojure/clojurescript "1.9.225"]
                  [org.clojure/core.async "0.2.374"]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(task-options!
  pom {:project 'tag-soup
       :version "1.3.4-SNAPSHOT"
       :description "A library to parse code into a list of tags"
       :url "https://github.com/oakes/tag-soup"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
  push {:repo "clojars"})

(deftask run-repl []
  (repl :init-ns 'tag-soup.core))

(deftask try []
  (comp (pom) (jar) (install)))

(deftask deploy []
  (comp (pom) (jar) (push)))

