(ns tag-soup.core
  (:require [clojure.string :as str]
            [#?(:clj oakclojure.tools.reader
                :cljs oakcljs.tools.reader)
             :as r :refer [*wrap-value-and-add-metadata?*]]
            [#?(:clj oakclojure.tools.reader.reader-types
                :cljs oakcljs.tools.reader.reader-types)
             :refer [indexing-push-back-reader]]
            [schema.core :refer [maybe either Any Str Int Keyword Bool]
             #?@(:clj [:as s])])
  #?(:cljs (:require-macros [schema.core :as s])))

(s/defn read-safe :- (maybe (either Any #?(:clj Exception :cljs js/Error)))
  "Returns either a form or an exception object, or nil if EOF is reached."
  [reader :- Any]
  (try
    (binding [*wrap-value-and-add-metadata?* true]
      (r/read {:read-cond :preserve :eof nil} reader))
    (catch #?(:clj Exception :cljs js/Error) e e)))

(def ^:const special-indent
  #{'-> '->>
    'cond-> 'cond->>
    'some-> 'some->>
    'as->
    'and 'or
    '+ '- '* '/
    '= 'not= '==
    '> '< '>= '<=})

(s/defn unwrap-value :- Any
  [value :- Any]
  (if (-> value meta :wrapped?)
    (first value)
    value))

(s/defn adjust-indent :- Int
  "Returns how much the indent should be adjusted for the given token."
  [token :- Any]
  (if (list? token)
    (let [first-val (-> token first unwrap-value)]
      (cond
        ; multi-arity functions
        (vector? first-val)
        0
        ; :require and other keywords in ns
        (keyword? first-val)
        (inc (count (str first-val)))
        ; threading macros
        (contains? special-indent first-val)
        (inc (count (str first-val)))
        ; any other list
        :else
        1))
    0))

(s/defn tag-map :- Any
  "Returns a transient map containing the tags, organized by line number."
  ([token :- Any
    results-map :- Any]
   (tag-map token results-map 0))
  ([token :- Any
    results-map :- Any
    parent-indent :- Int]
   (cond
     ; an error
     (instance? #?(:clj Exception :cljs js/Error) token)
     #?(:clj results-map
        :cljs (let [{:keys [line column]} (.-data token)]
                (assoc! results-map line
                  (conj (get results-map line [])
                    {:error? true :message (.-message token) :column column}))))
     
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
                       {:end? true :column end-column}))))))))))

(s/defn code->tags :- {Int {Keyword Any}}
  "Returns the tags for the given string containing code."
  [text :- Str]
  (let [reader (indexing-push-back-reader text)]
    (loop [m (transient {})]
      (if-let [token (read-safe reader)]
        (recur (tag-map token m))
        (persistent! m)))))

(s/defn get-tags-before-line :- [{Keyword Any}]
  "Returns the tags before the given line."
  [tags :- {Int [{Keyword Any}]}
   line :- Int]
  (->> tags
       (filter #(< (first %) line))
       (sort-by first)
       (map second)
       (map #(sort-by :column %))
       (apply concat)))

(s/defn indent-for-line :- Int
  "Returns the number of spaces the given line should be indented."
  [tags :- {Int [{Keyword Any}]}
   line :- Int]
  (or (->> (get-tags-before-line tags line)
           reverse
           (some :next-line-indent))
    0))

(s/defn back-indent-for-line :- Int
  "Returns the number of spaces the given line should be indented back."
  [tags :- {Int [{Keyword Any}]}
   line :- Int
   current-indent :- Int]
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

(s/defn forward-indent-for-line :- Int
  "Returns the number of spaces the given line should be indented forward."
  [tags :- {Int [{Keyword Any}]}
   line :- Int
   current-indent :- Int]
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
