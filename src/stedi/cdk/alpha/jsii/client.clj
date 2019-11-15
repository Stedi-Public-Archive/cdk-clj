(ns stedi.cdk.alpha.jsii.client
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [stedi.cdk.alpha.jsii.assembly :as assm])
  (:import (com.fasterxml.jackson.databind ObjectMapper)
           (software.amazon.jsii JsiiRuntime JsiiModule JsiiObjectRef)))

(defonce ^:private object-mapper (ObjectMapper.))

(defonce ^:private jsii-runtime (atom (JsiiRuntime.)))

(defonce ^:private loaded-modules (atom #{}))

(defn- ->module [{:keys [module-name module-version module-bundle]}]
  (let [this (proxy [JsiiModule] [module-name module-version nil module-bundle])]
    (proxy [JsiiModule] [module-name module-version (class this) module-bundle])))

(defn reset-runtime!
  []
  (reset! jsii-runtime (JsiiRuntime.))
  (reset! loaded-modules #{}))

(defn- client
  []
  (.getClient @jsii-runtime))

(defn load-module [fqn]
  (let [module-name (first (string/split fqn #"\."))]
    (when-not (@loaded-modules module-name)
      (let [{:keys [::assm/bundle
                    version
                    dependencies]} (assm/get-assembly module-name)

            module (->module {:module-name    module-name
                              :module-version version
                              :module-bundle  bundle})]
        (doseq [dep (map first dependencies)]
          (load-module dep))
        (.loadModule (client) module)
        (swap! loaded-modules conj module-name)))))

(defn- json-node->edn [json-node]
  (some-> json-node
          (.toString)
          (json/read-str)))

(defn- edn->json-node [data]
  (->> data
       (json/write-str)
       (.readTree object-mapper)))

(defn create-object
  ([fqn initializer-args]
   (load-module fqn)
   (-> (.createObject (client) fqn (map edn->json-node initializer-args))
       (.toJson)
       (json-node->edn))))

(defn delete-object
  [object-ref]
  (.deleteObject (client) object-ref))

(defn get-property-value
  [object-id property]
  (let [object-ref (JsiiObjectRef/fromObjId object-id)]
    (-> (.getPropertyValue (client) object-ref property)
        (json-node->edn))))

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
  [object-id method args]
  (let [object-ref (JsiiObjectRef/fromObjId object-id)]
    (-> (.callMethod (client) object-ref method (edn->json-node args))
        (json-node->edn))))
