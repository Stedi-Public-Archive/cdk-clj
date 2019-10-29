(ns stedi.jsii
  (:require [clojure.spec.alpha :as s]
            [stedi.jsii.types :as types]))

(defn get-class
  [fqn]
  (types/get-class fqn))

(defn create
  [c args]
  (types/create c args))

(defn invoke
  [c op-map]
  (types/-invoke c op-map))

(defn describe
  [x]
  (types/get-type-info x))
