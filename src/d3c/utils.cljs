(ns d3c.utils
  (:require [strokes :refer [d3]]
            [d3c.core :as d3c]))

(defn zoom-to [sel percent [full-width full-height]]
  (let [{:keys [x y width height]} (-> sel .node .getBBox)
        center [(+ x (/ width 2)) (+ y (/ height 2))]
        scale (min (/ full-width width) (/ full-height height))]
    (d3c/transformations
      (d3c/translate (/ full-width 2) (/ full-height 2))
      (d3c/scale (* percent scale))
      (apply d3c/translate (map - center)))))

(defn stroked-text [{:keys [text] :as settings}]
  [:g (dissoc settings :text)
   [:text {:text text :attr {:class "stroke"}}]
   [:text {:text text}]])
