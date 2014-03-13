(ns d3c.core
  (:require [cljs.core :as core]
            [strokes]))

(def d3 strokes/d3)

(strokes/bootstrap)

(let [strkey #(if (core/keyword? %) (name %) %)]
  (extend-type object
    ILookup
    (-lookup
      ([this k]
       (aget this (strkey k)))
      ([this k not-found]
       (let [s (strkey k)]
         (if (goog.object.containsKey this s)
           (aget this s)
           not-found))))
    IAssociative
    (-contains-key? [this k]
      (goog.object.containsKey this (strkey k)))
    (-assoc [this k v]
      (aset this (strkey k) v)
      this)))

(set! (.. js/d3 -selection -prototype -attrs)
      (fn [f]
        (this-as sel
          (.each sel (fn []
                       (this-as el
                         (.attr (.select d3 el)
                                (. f (apply el js/arguments)))))))))

(defn transformations [& ts]
  (.join (clj->js ts) " "))

(defn translate [dx dy]
  (str "translate(" dx ", " dy ")"))

(defn translate-x [dx]
  (translate dx 0))

(defn translate-y [dy]
  (translate 0 dy))

(defn scale
  ([sx]
   (str "scale(" sx ")"))
  ([sx sy]
   (str "scale(" sx ", " sy ")")))

(defn rotate [x]
  (str "rotate(" x ")"))

(defn configure! [sel {:keys [attrs attr style text html property tween on]}]
  (when attrs
    (.attrs sel attrs))
  (doseq [[k v] attr]
    (.attr sel (name k) v))
  (doseq [[k v] style]
    (.style sel (name k) v))
  (when text
    (.text sel text))
  (when html
    (.html sel html))
  (doseq [[k v] property]
    (.property sel (name k) v))
  (doseq [[k v] tween]
    (.tween sel (name k) v))
  (doseq [[k v] on]
    (.on sel (name k) v))
  sel)

(declare append!)

(defn append-children! [sel children]
  (doseq [child children]
    (append! sel child))
  sel)

(defn apply-from [settings]
  (fn c
    ([sel attr f]
     (c sel attr f identity))
    ([sel attr f else-f]
     (if-let [value (attr settings)]
       (f sel value)
       (else-f sel)))))

(defn mod! [f]
  (fn [sel & elems]
    (loop [sel sel
           last-sel sel
           elems elems]
      (if (seq elems)
        (let [[el settings & children] (first elems)
              one (apply-from settings)]
          (recur sel
                 (-> sel
                   (one :datum (fn [sel d]
                                 (.datum sel d)))
                   (one :join (fn [sel [selector d]]
                                (-> sel
                                  (.selectAll selector)
                                  (.data (if (keyword? d) #(d %) d))
                                  .enter)))
                   (f (name el))
                   (configure! settings)
                   (append-children! children))
                 (rest elems)))
        last-sel))))

(def append! (mod! #(.append %1 %2)))

(defn insert-before! [sel selector & elems]
  (apply (mod! #(.insert %1 %2 selector)) sel elems))

(defn ^{:deprecated "0.1.2"} unify! [sel data dom]
  (-> sel
    (.data (clj->js data))
    .enter
    (append! dom)))

(defn ^{:deprecated "0.1.2"} bind! [sel selector data dom]
  (unify! (.selectAll sel selector) data dom))
