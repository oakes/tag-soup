(ns tag-soup.core
  (:require [clojure.string :as str]
            [#?(:clj clojure.tools.reader
                :cljs cljs.tools.reader)
             :as r :refer [*wrap-value-and-add-metadata?*]]
            [#?(:clj clojure.tools.reader.reader-types
                :cljs cljs.tools.reader.reader-types)
             :refer [indexing-push-back-reader]]
            [schema.core :refer [maybe either Any Str Int Keyword Bool]
             #?@(:clj [:as s])])
  #?(:cljs (:require-macros [schema.core :as s])))

(s/defn read-safe :- (maybe (either Any #?(:clj Exception :cljs js/Error)))
  "Returns either a form or an exception object, or nil if EOF is reached."
  [reader :- Any]
  (try
    (binding [*wrap-value-and-add-metadata?* true]
      (r/read reader false nil))
    (catch #?(:clj Exception :cljs js/Error) e e)))

(def ^:const special-indent #{'-> '->> 'cond-> 'cond->> 'some-> 'some->>})

(s/defn adjust-indent :- Int
  "Returns how much the indent should be adjusted for the given token."
  [token :- Any]
  (if (list? token)
    (let [first-val (first token)]
      (cond
        ; multi-arity functions
        (vector? first-val)
        0
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
   (tag-list token 0))
  ([token :- Any
    parent-spaces :- Int]
   (flatten
     (cond
       ; an error
       (instance? #?(:clj Exception :cljs js/Error) token)
       [(assoc #?(:clj {} :cljs (.-data token))
               :message #?(:clj (.getMessage token) :cljs (.-message token))
               :error? true)]
       
       ; a key-value pair from a map
       (and (coll? token) (nil? (meta token)))
       (map #(tag-list % parent-spaces) token)
       
       ; a valid token
       :else
       (let [{:keys [line column end-line end-column wrapped?]} (meta token)
             value (if wrapped? (first token) token)]
         [; begin tag
          {:line line :column column :value value}
          (if (coll? value)
            (let [delimiter-size (if (set? value) 2 1)
                  new-end-column (+ column delimiter-size)
                  adjustment (adjust-indent value)
                  next-line-spaces (+ (dec column) delimiter-size adjustment)]
              [; open delimiter tags
               {:line line :column column :delimiter? true}
               {:end-line line :end-column new-end-column :next-line-spaces next-line-spaces}
               ; child tags
               (map #(tag-list % next-line-spaces) value)
               ; close delimiter tags
               {:line end-line :column (dec end-column) :delimiter? true}
               {:end-line end-line :end-column end-column :next-line-spaces parent-spaces}])
            [])
           ; end tag
          {:end-line end-line :end-column end-column}])))))

(s/defn str->tags :- [{Keyword Any}]
  "Returns the tags for the given string containing code."
  [text :- Str]
  (let [reader (indexing-push-back-reader text)]
    (sequence (comp (take-while some?) (mapcat tag-list))
              (repeatedly (partial read-safe reader)))))

(s/defn indent-for-line :- Int
  "Returns the number of spaces the given line should be indented."
  [tags :- [{Keyword Any}]
   cursor-line :- Int]
  (or (->> tags
           (take-while (fn [tag]
                         (let [line (or (:line tag) (:end-line tag) -1)]
                           (< line (inc cursor-line)))))
           reverse
           (some :next-line-spaces))
    0))

(s/defn change-indent-for-line :- Int
  "Returns the number of spaces the given line should be indented back or forwards."
  [tags :- [{Keyword Any}]
   cursor-line :- Int
   reverse? :- Bool]
  (let [[tags-before
         tags-on-line] (partition-by
                         (fn [tag]
                           (let [line (or (:line tag) (:end-line tag) -1)]
                             (= line (inc cursor-line))))
                         tags)
        current-indent (dec (or (some :column tags-on-line) 1))
        default-indent (if reverse? 0 (+ 2 current-indent))
        valid? (if reverse? < >)
        choose (if reverse? first last)]
    (or (when (> current-indent 1)
          (->> tags-before
               reverse
               (take-while (fn [tag]
                             (not= 1 (:column tag))))
               (filter (fn [tag]
                         (and (some-> (:next-line-spaces tag) (valid? current-indent))
                              (some-> (:end-column tag) dec (valid? current-indent)))))
               (map :next-line-spaces)
               choose))
      default-indent)))
