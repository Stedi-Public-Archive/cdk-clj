(ns stedi.cdk.jsii.client
  (:require [clojure.data.json :as json]
            [clojure.walk :as walk]
            [stedi.cdk.jsii.modules :as modules])
  (:import (com.fasterxml.jackson.databind ObjectMapper)
           (com.fasterxml.jackson.databind.node ArrayNode)
           (software.amazon.jsii JsiiRuntime JsiiObjectRef)))

(defonce ^:private object-mapper (ObjectMapper.))

(defonce ^:private jsii-runtime (JsiiRuntime.))

(defonce ^:private loaded-modules (atom #{}))

(defn- client
  []
  (.getClient jsii-runtime))

(defn- load-module [module]
  (let [coords [(.getModuleName module) (.getModuleVersion module)]]
    (when-not (@loaded-modules coords)
      (.loadModule (client) module)
      (swap! loaded-modules conj coords))))

;; Load all cdk modules
(doseq [module (modules/all)]
  (load-module module))

(defn deserialize-refs [x]
  (walk/postwalk
    (fn [y]
      (if-let [object-id
               (and (map? y)
                    (:$jsii.byref y))]
        (JsiiObjectRef/fromObjId object-id)
        y))
    x))

(defn serialize-refs [x]
  (walk/postwalk
    (fn [y]
      (if (= JsiiObjectRef (class y))
        (.toJson y)
        y))
    x))

(defn- json-node->edn [json-node]
  (-> json-node
      (.toString)
      (json/read-str :key-fn keyword)
      (deserialize-refs)))

(defn- edn->json-node [data]
  (->> data
       (serialize-refs)
       (.valueToTree object-mapper)))

(defn create-object
  [fqn initializer-args]
  (.createObject (client) fqn (map edn->json-node initializer-args)))

(defn delete-object
  [object-ref]
  (.deleteObject (client) object-ref))

(defn get-property-value
  [object-ref property]
  (-> (.getPropertyValue (client) object-ref property)
      (json-node->edn)))

;; TODO: find something to test this with
(defn set-property-value
  [object-ref property value]
  (.setPropertyValue (client) object-ref property (edn->json-node value)))

(defn get-static-property-value
  [fqn property]
  (-> (.getStaticPropertyValue (client) fqn property)
      (json-node->edn)))

;; TODO: find something to test this with
(defn set-static-property-value
  [fqn property value]
  (.setStaticPropertyValue (client) fqn property (edn->json-node value)))

(defn call-static-method
  [fqn method args]
  (-> (.callStaticMethod (client) fqn method (edn->json-node args))
      (json-node->edn)))

(defn call-method
  [object-ref method args]
  (-> (.callMethod (client) object-ref method (edn->json-node args))
      (json-node->edn)))

;; TODO: track objects that can be garbage collected
;; TODO: overrides
;; TODO: async methods
;; TODO: callbacks
