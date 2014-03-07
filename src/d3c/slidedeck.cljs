(ns d3c.slidedeck
  (:use [clojure.walk :only [prewalk]]
        [cljs.core.async :only [chan timeout]])
  (:require [cljs.reader :as reader]
            [d3c.core :as d3c :refer [d3]]
            [d3c.utils])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(reader/register-tag-parser! 'skip
  (fn [x]
    (with-meta x {:skip true})))

(reader/register-tag-parser! 'from-to
  (fn [x]
    (with-meta x {:transition true})))

(reader/register-tag-parser! 'static
  (fn [x]
    (with-meta [x x] {:transition true})))

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

(reader/register-tag-parser! 'zoom-to
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

(defn execute [slides dir]
  (doseq [slide slides
          :let [step (dir slide (fn []))]]
    (step)))

(defn upcoming [[first-slide & remaining-slides :as slides]]
  (if (:skip first-slide)
    [(take-while :skip slides)
     (drop-while :skip slides)]
    [(take 1 slides)
     (drop 1 slides)]))

(defmulti step (fn [dir _ _] dir))

(defmethod step :forward [dir past-slides future-slides]
  (let [[slides future-slides] (upcoming future-slides)]
    (execute slides dir)
    [(concat past-slides (filter identity slides)) future-slides]))

(defmethod step :backward [dir past-slides future-slides]
  (let [[slides past-slides] (upcoming (reverse past-slides))]
    (execute slides dir)
    [(reverse past-slides) (concat (filter identity slides) future-slides)]))

(defn run [slides]
  (let [<-forward (chan)
        <-backward (chan)]
    (go-loop [past-slides nil
              future-slides slides]
      (let [dir (alt!
                  <-forward :forward
                  <-backward :backward)
            [ps fs] (step dir past-slides future-slides)]
        (recur ps fs)))
    [<-backward <-forward]))

(defn extract [f]
  #(if (:transition (meta %)) (f %) %))

(defn backward [transition]
  (prewalk (extract first) transition))

(defn forward [transition]
  (prewalk (extract second) transition))

(defn collapse-tween [{:keys [tween] :as transition}]
  (merge (dissoc transition :tween) tween))

(defn opposing [direction]
  (if (= direction forward) backward forward))

(defn transition [slide direction {:keys [default-duration]}]
  (let [initial (opposing direction)]
    (go
      (doseq [{t :transition
               d :delay
               dur :duration
               easing :ease
               :keys [sel after-backward]
               :or {d 0
                    dur default-duration
                    easing "cubic-in-out"
                    after-backward (fn [])}} slide]
        (if (= :wait t)
          (<! (timeout (* 1000 d)))
          (-> (.selectAll d3 sel)
            (d3c/configure! (initial (collapse-tween t)))
            .transition
            (.delay (if (fn? d) d (* 1000 d)))
            (.duration (* 1000 dur))
            (.ease easing)
            (d3c/configure! (direction t))
            (.each "end" (fn []
                           (this-as el
                             (when (= direction backward)
                               (d3c/configure! (.select d3 el) after-backward)))))))))))

(defn skip [slide direction _]
  (let [initial (opposing direction)]
    (doseq [{t :transition
             :keys [sel]} slide
            :when (not= :wait t)]
      (-> (.selectAll d3 sel)
        (d3c/configure! (initial (collapse-tween t)))
        .transition
        (.duration 0)
        (d3c/configure! (direction t))))))

(defn handle [slide direction {skip-slide :skip :as options}]
  (fn []
    (let [f (if skip-slide skip transition)]
      (f slide direction options))))

(defn make-slide [slide options]
  (let [skip (:skip (meta slide))
        options (assoc options :skip skip)]
    {:backward (handle (reverse slide) backward options)
     :forward (handle slide forward options)
     :skip skip}))

(defn slides [{sl :slides :as pres}]
  (let [options (select-keys pres [:default-duration])]
    (map #(make-slide % options) sl)))
