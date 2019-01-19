(ns tag-soup.core
  #?(:clj (:import clojure.lang.ExceptionInfo))
  (:require [clojure.spec.alpha :as s :refer [fdef]]
            [clojure.string :as str]
            [#?(:clj oakclojure.tools.reader
                :cljs oakcljs.tools.reader)
             :as r :refer [*wrap-value-and-add-metadata?*]]
            [#?(:clj oakclojure.tools.reader.reader-types
                :cljs oakcljs.tools.reader.reader-types)
             :refer [indexing-push-back-reader indexing-reader?]]))

(fdef read-safe
  :args (s/cat :reader indexing-reader?)
  :ret (s/or :success (s/nilable some?) :failure #(instance? #?(:clj Exception :cljs js/Error) %)))

(defn read-safe
  "Returns either a form or an exception object, or nil if EOF is reached."
  [reader]
  (try
    (binding [*wrap-value-and-add-metadata?* true
              r/*suppress-read* true]
      (r/read {:read-cond :preserve :eof nil} reader))
    (catch ExceptionInfo e e)))

(def ^:const special-indent
  #{'-> '->>
    'cond-> 'cond->>
    'some-> 'some->>
    'as->
    'and 'or
    '+ '- '* '/
    '= 'not= '==
    '> '< '>= '<=})

(fdef unwrap-value
  :args (s/cat :value any?)
  :ret any?)

(defn unwrap-value [value]
  (if (-> value meta :wrapped?)
    (first value)
    value))

(fdef adjust-indent
  :args (s/cat :token any?)
  :ret integer?)

(defn adjust-indent
  "Returns how much the indent should be adjusted for the given token."
  [token]
  (if (list? token)
    (let [first-val (-> token first unwrap-value)]
      (cond
        ; multi-arity functions
        (or (vector? first-val) (list? first-val))
        0
        ; :require and other keywords in ns
        (#{:require :import :use} first-val)
        (inc (count (str first-val)))
        ; reader conditionals
        (#{:clj :cljs :cljr} first-val)
        0
        ; threading macros
        (contains? special-indent first-val)
        (inc (count (str first-val)))
        ; any other list
        :else
        1))
    0))

(fdef tag-map
  :args (s/cat :token any? :results-map any? :parent-indent integer?)
  :ret any?)

(defn tag-map
  "Returns a transient map containing the tags, organized by line number."
  [token results-map parent-indent]
  (cond
    ; an error
    (instance? ExceptionInfo token)
    (let [{:keys [line col]} (ex-data token)]
      (assoc! results-map line
        (conj (get results-map line [])
          {:error? true
           :message #?(:clj (.getMessage token)
                       :cljs (.-message token))
           :column col})))
    
    ; a key-value pair from a map
    (and (coll? token) (nil? (meta token)))
    (reduce
      (fn [results-map token]
        (tag-map token results-map parent-indent))
      results-map
      token)
    
    ; a valid token
    :else
    (let [{:keys [line column end-line end-column]} (meta token)]
      (if-not (and line column end-line end-column)
        results-map
        (let [value (unwrap-value token)
              indent (max parent-indent (dec column))
              top-level? (= parent-indent 0)]
          (if (coll? value)
            (let [delimiter-size (if (set? value) 2 1)
                  new-end-column (+ column delimiter-size)
                  adjustment (adjust-indent value)
                  next-line-indent (+ (dec column) delimiter-size adjustment)]
              (as-> results-map $
                    (assoc! $ line
                      (-> (get $ line [])
                          (conj {:begin? true :column column :value value :indent indent :top-level? top-level? :skip-indent? true})
                          (conj {:delimiter? true :column column})
                          (conj {:end? true :column new-end-column :next-line-indent next-line-indent :indent next-line-indent})))
                    (reduce
                      (fn [results-map token]
                        (tag-map token results-map next-line-indent))
                      $
                      value)
                    (assoc! $ end-line
                      (-> (get $ end-line [])
                          (conj {:delimiter? true :column (dec end-column)})
                          (conj {:end? true :column end-column :next-line-indent parent-indent})
                          (conj {:end? true :column end-column})))))
            (as-> results-map $
                  (assoc! $ line
                    (conj (get $ line [])
                      {:begin? true :column column :value value :indent indent :top-level? top-level?}))
                  (assoc! $ end-line
                    (conj (get $ end-line [])
                      {:end? true :column end-column})))))))))

(s/def ::tag (s/map-of keyword? any?))
(s/def ::tags-for-line (s/coll-of ::tag))
(s/def ::all-tags (s/map-of integer? ::tags-for-line))

(fdef code->tags
  :args (s/cat :text string?)
  :ret ::all-tags)

(defn code->tags
  "Returns the tags for the given string containing code."
  [text]
  (let [reader (indexing-push-back-reader text)]
    (loop [m (transient {})]
      (if-let [token (read-safe reader)]
        (recur (tag-map token m 0))
        (persistent! m)))))

(fdef get-tags-before-line
  :args (s/cat :tags ::all-tags :line integer?)
  :ret ::tags-for-line)

(defn get-tags-before-line
  "Returns the tags before the given line."
  [tags line]
  (->> tags
       (filter #(< (first %) line))
       (sort-by first)
       (map second)
       (map #(sort-by :column %))
       (apply concat)))

(fdef indent-for-line
  :args (s/cat :tags ::all-tags :line integer?)
  :ret integer?)

(defn indent-for-line
  "Returns the number of spaces the given line should be indented."
  [tags line]
  (or (->> (get-tags-before-line tags line)
           reverse
           (some :next-line-indent))
    0))

(fdef back-indent-for-line
  :args (s/cat :tags ::all-tags :line integer? :current-indent integer?)
  :ret integer?)

(defn back-indent-for-line
  "Returns the number of spaces the given line should be indented back."
  [tags line current-indent]
  (let [tags-before (get-tags-before-line tags line)]
    (loop [tags (reverse tags-before)
           max-tab-stop current-indent]
      (if-let [tag (first tags)]
        (if-let [indent (:indent tag)]
          (if (< indent max-tab-stop)
            (if (:skip-indent? tag)
              (recur (rest tags) (inc indent))
              indent)
            (recur (rest tags) max-tab-stop))
          (recur (rest tags) max-tab-stop))
        (- current-indent 2)))))

(fdef forward-indent-for-line
  :args (s/cat :tags ::all-tags :line integer? :current-indent integer?)
  :ret integer?)

(defn forward-indent-for-line
  "Returns the number of spaces the given line should be indented forward."
  [tags line current-indent]
  (let [tags-before (get-tags-before-line tags line)]
    (loop [tags (reverse tags-before)
           max-tab-stop -1
           tab-stop -1]
      (if-let [tag (first tags)]
        (if-let [indent (:indent tag)]
          (cond
            (<= indent current-indent)
            (recur [] max-tab-stop tab-stop)
            (or (neg? max-tab-stop) (< current-indent indent max-tab-stop))
            (recur (rest tags) (inc indent) (if (:skip-indent? tag) tab-stop indent))
            :else
            (recur (rest tags) max-tab-stop tab-stop))
          (recur (rest tags) max-tab-stop tab-stop))
        (if (<= tab-stop current-indent)
          (+ current-indent 2)
          tab-stop)))))

