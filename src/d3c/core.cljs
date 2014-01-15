(ns d3c.core
  (:require [cljs.core :as core]
            [strokes :refer [d3]]))

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

(defn transformations [& ts]
  (.join (clj->js ts) " "))

(defn translate [dx dy]
  (str "translate(" dx ", " dy ")"))

(defn scale
  ([sx]
   (str "scale(" sx ")"))
  ([sx sy]
   (str "scale(" sx ", " sy ")")))

(defn rotate [x]
  (str "rotate(" x ")"))

(defn configure! [sel settings]
  (let [fns {:data #(.data %)
             :attr #(.attr %1 %2)
             :style #(.style %1 %2)}]
    (if (seq settings)
      (let [[k v] (first settings)
            f (fns k)]
        (recur (if f
                 (f sel (clj->js v))
                 sel)
               (rest settings)))
      sel)))

(declare append!)

(defn append-children! [sel children]
  (doseq [child children]
    (append! sel child))
  sel)

(defn mod! [f]
  (fn [sel & elems]
    (loop [sel sel
           last-sel sel
           elems elems]
      (if (seq elems)
        (let [[el settings & children] (first elems)
              one (fn [sel attr f]
                    (if-let [value (attr settings)]
                      (f sel value)
                      sel))]
          (recur sel
                 (-> sel
                   (one :datum #(.datum %1 (clj->js %2)))
                   (f (name el))
                   (configure! settings)
                   (one :text #(.text %1 %2))
                   (append-children! children))
                 (rest elems)))
        last-sel))))

(def append! (mod! #(.append %1 %2)))

(defn insert-before! [sel selector & elems]
  (apply (mod! #(.insert %1 %2 selector)) sel elems))

(defn unify! [sel data dom]
  (-> sel
    (.data (clj->js data))
    .enter
    (append! dom)))

(defn bind! [sel selector data dom]
  (unify! (.selectAll sel selector) data dom))
