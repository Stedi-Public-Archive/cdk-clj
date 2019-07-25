(ns stedi.cdk.jsii.client
  (:require [clojure.data.json :as json]
            [stedi.cdk.jsii.modules :as modules]
            [clojure.walk :as walk])
  (:import (com.fasterxml.jackson.databind ObjectMapper)
           (com.fasterxml.jackson.databind.node ArrayNode)
           (software.amazon.jsii JsiiRuntime JsiiObjectRef)))

(defonce ^:private object-mapper (ObjectMapper.))

(defonce ^:private jsii-runtime (JsiiRuntime.))

(defonce ^:private loaded-modules (atom #{}))

(defn client
  []
  (.getClient jsii-runtime))

(defn load-module [module]
  (let [coords [(.getModuleName module) (.getModuleVersion module)]]
    (when-not (@loaded-modules coords)
      (.loadModule (client) module)
      (swap! loaded-modules conj coords))))

(doseq [module (modules/all)]
  (load-module module))

(declare process-response)

(deftype JsiiRecord [obj-ref]
  clojure.lang.IDeref
  (deref [_] obj-ref)

  clojure.lang.ILookup
  (valAt [_ k]
    (-> (.getPropertyValue (client) obj-ref (name k))
        (process-response))))

(defn- jsii-record [obj-id]
  (JsiiRecord. (JsiiObjectRef/fromObjId obj-id)))

(defn- wrap-refs [x]
  (walk/postwalk
    (fn [y]
      (if-let [obj-id (and (map? y)
                           (:$jsii.byref y))]
        (jsii-record obj-id)
        y))
    x))

(defn- process-response [response]
  (-> response
      (.toString)
      (json/read-str :key-fn keyword)
      (wrap-refs)))

(defn $ [fqn property-name]
  (-> (.getStaticPropertyValue (client) fqn property-name)
      (process-response)))
