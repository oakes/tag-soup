(ns tag-soup.test
  (:require [tag-soup.core :as ts]))

(defn parse-and-print [s]
  (println (pr-str s))
  (println (ts/code->tags s)))

(parse-and-print "::a/hello")
(parse-and-print "{:a}")
(parse-and-print "#:hello{}")
(parse-and-print "#:hello{:a}")
(parse-and-print "#\"(?s)abc\"")
(parse-and-print "#js {:a 1}")
(parse-and-print "(def ^{:doc \"hello\"} asdf)")
