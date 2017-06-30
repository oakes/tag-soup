(ns tag-soup.core-spec
  (:require [tag-soup.core :as c]
            [clojure.spec.alpha :as s :refer [fdef]]
            [#?(:clj oakclojure.tools.reader.reader-types
                :cljs oakcljs.tools.reader.reader-types)
             :refer [indexing-reader?]]))

(fdef c/read-safe
  :args (s/cat :reader indexing-reader?)
  :ret (s/or :success (s/nilable some?) :failure #(instance? #?(:clj Exception :cljs js/Error) %)))

(fdef c/unwrap-value
  :args (s/cat :value any?)
  :ret any?)

(fdef c/adjust-indent
  :args (s/cat :token any?)
  :ret integer?)

(fdef c/tag-map
  :args (s/cat :token any? :results-map any? :parent-indent integer?)
  :ret any?)

(s/def ::tag (s/map-of keyword? any?))
(s/def ::tags-for-line (s/coll-of ::tag))
(s/def ::all-tags (s/map-of integer? ::tags-for-line))

(fdef c/code->tags
  :args (s/cat :text string?)
  :ret ::all-tags)

(fdef c/get-tags-before-line
  :args (s/cat :tags ::all-tags :line integer?)
  :ret ::tags-for-line)

(fdef c/indent-for-line
  :args (s/cat :tags ::all-tags :line integer?)
  :ret integer?)

(fdef c/back-indent-for-line
  :args (s/cat :tags ::all-tags :line integer? :current-indent integer?)
  :ret integer?)

(fdef c/forward-indent-for-line
  :args (s/cat :tags ::all-tags :line integer? :current-indent integer?)
  :ret integer?)

