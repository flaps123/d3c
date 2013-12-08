(ns d3c.macros
  (:require [clojure.walk :as walk]))

(defmacro bound [bindings form]
  (->> form
    (walk/prewalk (fn [form]
                    (if (or (symbol? form)
                            (list? form))
                      (if (= `fn form)
                        ; Wrap forms in atoms to avoid walking into them.
                        (atom form)
                        (atom `(fn [~bindings] ~form)))
                      form)))
    (walk/prewalk (fn [form]
                    ; Unwrap forms from the atoms they were placed in.
                    (if (instance? clojure.lang.IDeref form)
                      @form
                      form)))))
