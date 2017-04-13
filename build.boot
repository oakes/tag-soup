(set-env!
  :source-paths #{"src"}
  :resource-paths #{"src"}
  :dependencies '[[org.clojars.oakes/tools.reader "1.0.0-2016.09.01"
                   :exclusions [org.clojure/clojure]]
                  [org.clojure/clojure "1.8.0"]
                  [org.clojure/clojurescript "1.9.518" :scope "provided"]
                  [org.clojure/core.async "0.3.442"]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(task-options!
  pom {:project 'tag-soup
       :version "1.4.1-SNAPSHOT"
       :description "A library to parse code into a list of tags"
       :url "https://github.com/oakes/tag-soup"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
  push {:repo "clojars"})

(deftask local []
  (comp (pom) (jar) (install)))

(deftask deploy []
  (comp (pom) (jar) (push)))

