(set-env!
  :source-paths #{"src"}
  :dependencies '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
                  ; project deps
                  [org.clojars.oakes/tools.reader "1.0.0-2016.07.01"
                   :exclusions [org.clojure/clojure]]
                  [org.clojure/clojure "1.8.0"]
                  [org.clojure/clojurescript "1.8.51"]
                  [org.clojure/core.async "0.2.374"]
                  [prismatic/schema "0.4.3"]])

(require
  '[adzerk.boot-cljs :refer [cljs]])

(deftask run-repl []
  (repl :init-ns 'tag-soup.core))

(deftask build []
  (comp (cljs :optimizations :advanced) (target)))
