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

(s/defn tag-list :- [{Keyword Any}]
  "Returns a list of maps describing each tag."
  ([token :- Any]
   (flatten (tag-list token 0)))
  ([token :- Any
    parent-indent :- Int]
   (cond
     ; an error
     (instance? #?(:clj Exception :cljs js/Error) token)
     [(assoc #?(:clj {} :cljs (.-data token))
             :message #?(:clj (.getMessage token) :cljs (.-message token))
             :error? true)]
     
     ; a key-value pair from a map
     (and (coll? token) (nil? (meta token)))
     (map #(tag-list % parent-indent) token)
     
     ; a valid token
     :else
     (let [{:keys [line column end-line end-column]} (meta token)
           value (unwrap-value token)
           indent (when column (max parent-indent (dec column)))
           top-level? (= parent-indent 0)]
       (if (coll? value)
         (let [delimiter-size (if (set? value) 2 1)
               new-end-column (+ column delimiter-size)
               adjustment (adjust-indent value)
               next-line-indent (+ (dec column) delimiter-size adjustment)]
           [; begin tag
            {:line line :column column :value value :indent indent :top-level? top-level? :skip-indent? true}
            ; open delimiter tags
            {:line line :column column :delimiter? true}
            {:end-line line :end-column new-end-column :next-line-indent next-line-indent :indent next-line-indent}
            ; child tags
            (map #(tag-list % next-line-indent) value)
            ; close delimiter tags
            {:line end-line :column (dec end-column) :delimiter? true}
            {:end-line end-line :end-column end-column :next-line-indent parent-indent}
            ; end tag
            {:end-line end-line :end-column end-column :end-tag? true}])
         [; begin tag
          {:line line :column column :value value :indent indent :top-level? top-level?}
          ; end tag
          {:end-line end-line :end-column end-column :end-tag? true}])))))

(def form->tags
  (comp
    (take-while some?)
    (mapcat tag-list)))

(s/defn str->tags :- [{Keyword Any}]
  "Returns the tags for the given string containing code."
  [text :- Str]
  (let [reader (indexing-push-back-reader text)]
    (sequence form->tags (repeatedly (partial read-safe reader)))))

(s/defn get-line :- Int
  "Returns the line number of the given tag, or -1 if none exists."
  [tag :- {Keyword Any}]
  (or (:line tag) (:end-line tag) -1))

(s/defn get-column :- Int
  "Returns the column number of the given tag, or -1 if none exists."
  [tag :- {Keyword Any}]
  (or (:column tag) (:end-column tag) -1))

(s/defn get-tags-before-line :- [{Keyword Any}]
  "Returns the tags before the given line."
  [tags :- [{Keyword Any}]
   line :- Int]
  (->> tags
       (filter #(< (get-line %) (inc line)))
       (sort-by get-line)))

(s/defn indent-for-line :- Int
  "Returns the number of spaces the given line should be indented."
  [tags :- [{Keyword Any}]
   cursor-line :- Int]
  (or (->> tags
           (take-while #(< (get-line %) (inc cursor-line)))
           reverse
           (some :next-line-indent))
    0))

(s/defn back-indent-for-line :- Int
  "Returns the number of spaces the given line should be indented back."
  [tags :- [{Keyword Any}]
   cursor-line :- Int
   current-indent :- Int]
  (let [tags-before (get-tags-before-line tags cursor-line)]
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
  [tags :- [{Keyword Any}]
   cursor-line :- Int
   current-indent :- Int]
  (let [tags-before (get-tags-before-line tags cursor-line)]
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
