(ns d3c.slidedeck
  (:use [clojure.walk :only [prewalk]]
        [cljs.core.async :only [chan timeout]])
  (:require [cljs.reader :as reader]
            [d3c.core :as d3c :refer [d3]]
            [d3c.utils])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]))

(defn from-to [x]
  (with-meta x {:transition true}))

(reader/register-tag-parser! 'from-to from-to)

(reader/register-tag-parser! 'attr
  (fn [attr]
    #(this-as el
       (.attr (.select d3 el) (name attr)))))

(reader/register-tag-parser! 'stagger
  (fn [step]
    (let [step (* 1000 step)]
      (fn [_ i]
        (this-as this
          (* step i))))))

(reader/register-tag-parser! 'wait
  (fn [seconds]
    {:transition :wait :delay seconds}))

(reader/register-tag-parser! 'zoom-to zoom-to
  (fn [{percent :scale size :size}]
    #(this-as this
       (d3c.utils/zoom-to (.select d3 this) percent size))))

(defn interpolate [f fset [start end]]
  (fn [d]
    (this-as base
      (let [start (if (fn? start)
                    (. start (call base d))
                    start)
            end (if (fn? end)
                  (. end (call base d))
                  end)
            i (f start end)]
       (fn [t]
         (this-as el (fset el (i t))))))))

(defn interpolate-between [f fset [start end]]
  [(interpolate f fset [end start])
   (interpolate f fset [start end])])

(reader/register-tag-parser! 'interpolate-text
  (partial interpolate-between
           (.-interpolate d3)
           #(set! (.-textContent %1) %2)))

(defn run [slides]
  (let [<-forward (chan)
        <-backward (chan)]
    (go
      (let [past-slides-cheat (atom nil)
            future-slides-cheat (atom slides)]
        (loop []
          (let [past-slides @past-slides-cheat
                [{:keys [forward] :or {forward (fn [])} :as next-slide} & future-slides] @future-slides-cheat
                {:keys [backward] :or {backward (fn [])} :as last-slide} (last past-slides)]
            (alt!
              <-forward (do
                          (forward)
                          (reset! past-slides-cheat (concat past-slides (filter identity [next-slide])))
                          (reset! future-slides-cheat future-slides))
              <-backward (do
                           (backward)
                           (reset! past-slides-cheat (butlast past-slides))
                           (reset! future-slides-cheat (concat (filter identity [last-slide next-slide]) future-slides)))))
          (recur))))
    [<-backward <-forward]))

(defn backward [transition]
  (prewalk (fn [settings]
             (cond
               (:transition (meta settings)) (first settings)
               :else settings))
           transition))

(defn forward [transition]
  (prewalk (fn [settings]
             (cond
               (:transition (meta settings)) (second settings)
               :else settings))
           transition))

(defn transition [slide direction {:keys [default-duration]}]
  (fn []
    (go
      (doseq [{t :transition
               d :delay
               dur :duration
               easing :ease
               :keys [sel]
               :or {d 0
                    dur default-duration
                    easing "cubic-in-out"}} slide]
        (if (= :wait t)
          (<! (timeout (* 1000 d)))
          (-> (.selectAll d3 sel)
            .transition
            (.delay (if (fn? d) d (* 1000 d)))
            (.duration (* 1000 dur))
            (.ease (if (= direction forward)
                     easing
                     (str easing "-out")))
            (d3c/configure! (direction t))))))))

(defn make-slide [slide options]
  {:backward (transition (reverse slide) backward options)
   :forward (transition slide forward options)})

(defn slides [{sl :slides :as pres}]
  (let [options (select-keys pres [:default-duration])]
    (map #(make-slide % options) sl)))
