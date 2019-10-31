(ns stedi.jsii
  "A Clojure-based interface into jsii modules."
  (:require [stedi.jsii.client :as client]
            [stedi.jsii.impl :as impl]
            [stedi.jsii.import :as import]))

(defn jsii-primitive?
  "Returns true if x is a jsii primitive."
  [x]
  (boolean
    (some #(instance? % x)
          [stedi.jsii.impl.JsiiObject
           stedi.jsii.impl.JsiiClass
           stedi.jsii.impl.JsiiEnumClass
           stedi.jsii.impl.JsiiEnumMember])))

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
