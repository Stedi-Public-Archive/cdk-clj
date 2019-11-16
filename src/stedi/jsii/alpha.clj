(ns stedi.jsii.alpha
  "A Clojure-based interface into jsii modules."
  (:require [stedi.jsii.alpha.client :as client]
            [stedi.jsii.alpha.impl :as impl]
            [stedi.jsii.alpha.import :as import]))

(defn get-class
  "Gets the jsii class for a given fqn."
  [fqn]
  (impl/get-class fqn))

(defn jsii-primitive?
  "Returns true if x is a jsii primitive."
  [x]
  (boolean
    (some #(instance? % x)
          [stedi.jsii.alpha.impl.JsiiObject
           stedi.jsii.alpha.impl.JsiiClass
           stedi.jsii.alpha.impl.JsiiEnumClass
           stedi.jsii.alpha.impl.JsiiEnumMember])))

(defn fqn
  "Returns the fqn of a jsii primitive."
  [x]
  (.-fqn x))

(defn describe
  "Describes a jsii type (class, enum or object)."
  [x]
  (impl/get-type-info x))

(defn import-fqn
  "Imports a jsii class or enum by fqn as alias-sym and refers the jsii
  class or enum artifact as alias-sym."
  [fqn alias-sym]
  (import/import-fqn fqn alias-sym))

(defn reset-runtime!
  "Resets the jsii runtime unloading any loaded modules."
  []
  (client/reset-runtime!))
