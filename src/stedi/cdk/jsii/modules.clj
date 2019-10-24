(ns stedi.cdk.jsii.modules
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [com.stuartsierra.dependency :as dep])
  (:import (java.util.zip GZIPInputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveInputStream)
           (software.amazon.jsii JsiiModule)))

(defn- classpath-jsii-archives
  []
  (->> (-> (System/getProperty "java.class.path")
           (string/split #":"))
       (filter #(.endsWith % ".jar"))
       (map io/file)
       (map #(java.util.jar.JarFile. %))
       (mapcat (comp enumeration-seq #(.entries %)))
       (filter (comp #(.endsWith % "jsii.tgz") str))
       (map str)))

(defn- load-manifest [resource]
  (with-open [is (-> resource
                     (io/resource)
                     (io/input-stream)
                     (GZIPInputStream.)
                     (TarArchiveInputStream.))]
    (loop []
      (if-let [entry (.getNextTarEntry is)]
        (if (= (.getName entry) "package/.jsii")
          (json/read (io/reader is))
          (recur))
        nil))))

(defn- module [{:keys [module-name module-version module-bundle]}]
  (let [module-proxy (get-proxy-class JsiiModule)]
    (construct-proxy module-proxy module-name module-version module-proxy module-bundle)))

(defn- get-deps [manifest]
  (map first (get manifest "dependencies")))

(defn- from-resouce
  [resource-path]
  (let [manifest (load-manifest resource-path)
        props    {:module-name    (get manifest "name")
                  :module-version (get manifest "version")
                  :module-bundle  (str "/" resource-path)}]
    {:props    props
     :module   (module props)
     :deps     (get-deps manifest)
     :manifest manifest}))

(defn- dep-graph [modules]
  (->> modules
       (map (juxt first (comp :deps second)))
       (reduce (fn [graph [module deps]]
                 (reduce #(dep/depend %1 module %2) graph deps))
               (dep/graph))))

(defn- fetch-all-modules* []
  (->> (classpath-jsii-archives)
       (map from-resouce)
       (map (juxt #(get-in % [:props :module-name]) identity))
       (filter first)
       (into {})))

(def ^:private fetch-all-modules (memoize fetch-all-modules*))

(defn exists?
  [module-name]
  (boolean (get (fetch-all-modules) module-name)))

(defn dependencies-for
  [module-name]
  (let [all-modules     (fetch-all-modules)
        dep-graph*      (dep-graph all-modules)
        transitive-deps (dep/transitive-dependencies dep-graph* module-name)]
    (->> (concat (filter transitive-deps (dep/topo-sort dep-graph*)) [module-name])
         (map all-modules))))
