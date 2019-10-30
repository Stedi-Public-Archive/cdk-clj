(ns stedi.jsii
  "A Clojure-based interface into jsii modules."
  (:require [stedi.jsii.types :as types]
            [stedi.jsii.import :as import]))

(defn describe
  "Describes a jsii type (class, enum or object)."
  [x]
  (types/get-type-info x))

(defn import-fqn
  "Imports a jsii class or enum by fqn as a alias-sym and refers the
  jsii class or enum artifact as alias-sym."
  [fqn alias-sym]
  (import/import-fqn fqn alias-sym))
