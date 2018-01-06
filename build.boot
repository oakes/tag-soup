(set-env!
  :resource-paths #{"src"}
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(require '[clojure.edn :as edn])

(task-options!
  pom {:project 'tag-soup
       :version "1.5.4-SNAPSHOT"
       :description "A library to parse code into a list of tags"
       :url "https://github.com/oakes/tag-soup"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}
       :dependencies (->> "deps.edn"
                          slurp
                          edn/read-string
                          :deps
                          (reduce
                            (fn [deps [artifact info]]
                              (if-let [version (:mvn/version info)]
                                (conj deps
                                  (transduce cat conj [artifact version]
                                    (select-keys info [:scope :exclusions])))
                                deps))
                            []))}
  push {:repo "clojars"})

(deftask local []
  (comp (pom) (jar) (install)))

(deftask deploy []
  (comp (pom) (jar) (push)))

