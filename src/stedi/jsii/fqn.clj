(ns stedi.jsii.fqn
  (:require [clojure.string :as string]))

;; TODO: pull in all naming concepts (implementation nses for example)

(defn- tokenize
  [fqn & parts]
  (-> (string/join "." (concat [fqn] parts))
      (string/replace "@" "")
      (string/replace "/" ".")
      (string/split #"\.")))

(defn fqn->ns-sym
  [fqn & parts]
  (symbol
    (->> (apply tokenize fqn parts)
         (string/join "."))))

(defn fqn->qualified-keyword
  [fqn & parts]
  (-> (apply tokenize fqn parts)
      ((juxt butlast last))
      (update 0 (partial string/join "."))
      ((partial string/join "/"))
      (keyword)))

(defn fqn->qualified-symbol
  [fqn & parts]
  (symbol
    (apply fqn->qualified-keyword fqn parts)))
