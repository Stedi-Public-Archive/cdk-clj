(ns stedi.cdk.impl
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [stedi.cdk.jsii.client :as client])
  (:import (software.amazon.jsii JsiiObjectRef)))

(declare wrap-objects unwrap-objects)

(defn invoke-object
  [cdk-object op args]
  (assert (keyword? op) "op must be a keyword")
  (->> (unwrap-objects args)
       (client/call-method (. cdk-object object-ref) (name op))
       (wrap-objects)))

(deftype CDKObject [object-ref]
  clojure.lang.ILookup
  (valAt [_ k]
    (-> (client/get-property-value object-ref (name k))
        (wrap-objects)))

  java.lang.Object
  (toString [this]
    (try
      (invoke-object this [:toString])
      (catch Exception _
        (.getFqn object-ref)))))

(defn wrap-objects
  [x]
  (walk/postwalk
    (fn [y]
      (if (= JsiiObjectRef (type y))
        (CDKObject. y)
        y))
    x))

(defn unwrap-objects
  [x]
  (walk/postwalk
    (fn [y]
      (if (= CDKObject (type y))
        (. y object-ref)
        y))
    x))

(defn create-object [cdk-class args]
  (let [fqn (. cdk-class fqn)]
    (CDKObject. (client/create-object fqn (unwrap-objects args)))))

(defn invoke-class
  [cdk-class op & args]
  (let [fqn (. cdk-class fqn)]
    (->> (unwrap-objects args)
         (client/call-static-method fqn (name op))
         (wrap-objects))))

(deftype CDKClass [fqn]
  clojure.lang.ILookup
  (valAt [_ k]
    (-> (client/get-static-property-value fqn (name k))
        (wrap-objects)))

  clojure.lang.IFn
  (applyTo [this arglist] (create-object this arglist))
  (invoke [this] (create-object this []))
  (invoke [this a1] (create-object this [a1]))
  (invoke [this a1 a2] (create-object this [a1 a2]))
  (invoke [this a1 a2 a3] (create-object this [a1 a2 a3])))

(defn wrap-class [fqn]
  (CDKClass. fqn))

(defn package->ns-sym [package]
  (-> package
      (string/replace "@" "")
      (string/replace "/" ".")
      (symbol)))
