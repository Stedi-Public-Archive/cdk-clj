(ns stedi.cdk.jsii.client
  (:require [clojure.data.json :as json]
            [clojure.walk :as walk]
            [stedi.cdk.jsii.modules :as modules])
  (:import (com.fasterxml.jackson.databind ObjectMapper)
           (com.fasterxml.jackson.databind.node ArrayNode)
           (software.amazon.jsii JsiiRuntime JsiiObjectRef JsiiCallbackHandler)
           (software.amazon.jsii.api JsiiOverride Callback)))

(defonce ^:private object-mapper (ObjectMapper.))

(defonce ^:private jsii-runtime (atom (JsiiRuntime.)))

(defonce ^:private loaded-modules (atom #{}))

(defn reset-runtime!
  []
  (reset! jsii-runtime (JsiiRuntime.))
  (reset! loaded-modules #{}))

(defn- client
  []
  (.getClient @jsii-runtime))

(defn load-module [module-name]
  (let [module-name* (first (clojure.string/split module-name #"\."))
        to-load      (modules/dependencies-for module-name*)]
    (doseq [{:keys [manifest module]} to-load]
      (when-not (@loaded-modules manifest)
        (.loadModule (client) module)
        (swap! loaded-modules conj manifest)))))

(defn get-manifest
  [module-name]
  (->> @loaded-modules
       (filter (comp #{module-name} #(get % "name")))
       (first)))

(defn- deserialize-refs [x]
  (walk/postwalk
    (fn [y]
      (if-let [object-id
               (and (map? y)
                    (:$jsii.byref y))]
        (JsiiObjectRef/fromObjId object-id)
        y))
    x))

(defn- serialize-refs [x]
  (walk/postwalk
    (fn [y]
      (if (= JsiiObjectRef (class y))
        (.toJson y)
        y))
    x))

(defn- namify-keys [x]
  (walk/postwalk
    (fn [y]
      (if (map? y)
        (into {} (map #(update % 0 name)) y)
        y))
    x))

(defn- json-node->edn [json-node]
  (some-> json-node
          (.toString)
          (json/read-str :key-fn keyword)
          (deserialize-refs)))

(defn- edn->json-node [data]
  (->> data
       (namify-keys)
       (serialize-refs)
       (.valueToTree object-mapper)))

(defn- ->override [{:keys [method property cookie]}]
  (doto (JsiiOverride.)
    (.setMethod method)
    (.setProperty property)
    (.setCookie cookie)))

(defn create-object
  ([fqn initializer-args]
   (load-module fqn)
   (.createObject (client) fqn (map edn->json-node initializer-args)))
  ([fqn initializer-args callbacks]
   (load-module fqn)
   (.createObject (client) fqn (map edn->json-node initializer-args) (map ->override callbacks))))

(defn delete-object
  [object-ref]
  (.deleteObject (client) object-ref))

(defn get-property-value
  [object-ref property]
  (-> (.getPropertyValue (client) object-ref property)
      (json-node->edn)))

(defn set-property-value
  [object-ref property value]
  (.setPropertyValue (client) object-ref property (edn->json-node value)))

(defn get-static-property-value
  [fqn property]
  (load-module fqn)
  (-> (.getStaticPropertyValue (client) fqn property)
      (json-node->edn)))

(defn set-static-property-value
  [fqn property value]
  (load-module fqn)
  (.setStaticPropertyValue (client) fqn property (edn->json-node value)))

(defn call-static-method
  [fqn method args]
  (load-module fqn)
  (-> (.callStaticMethod (client) fqn method (edn->json-node args))
      (json-node->edn)))

(defn call-method
  [object-ref method args]
  (-> (.callMethod (client) object-ref method (edn->json-node args))
      (json-node->edn)))
