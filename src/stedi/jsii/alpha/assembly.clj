(ns stedi.jsii.alpha.assembly
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (java.util.zip GZIPInputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveInputStream))
  (:refer-clojure :exclude [methods]))

(defn- classpath-jsii-archives
  []
  (->> (-> (System/getProperty "java.class.path")
           (string/split #":"))
       (filter #(string/ends-with? % ".jar"))
       (map io/file)
       (map #(java.util.jar.JarFile. %))
       (mapcat (comp enumeration-seq #(.entries %)))
       (filter (comp #(.endsWith % "jsii.tgz") str))
       (map str)))

(defn- keywordize-alpha
  [x]
  (if (and (string? x)
           (re-find #"^[A-Za-z]" x))
    (keyword x)
    x))

(defn- read-spec
  [input-stream]
  (-> input-stream
      (io/reader)
      (json/read :key-fn keywordize-alpha)))

(defn- load-assembly-resource [resource]
  (with-open [is (-> resource
                     (io/resource)
                     (io/input-stream)
                     (GZIPInputStream.)
                     (TarArchiveInputStream.))]
    (loop []
      (when-let [entry (.getNextTarEntry is)]
        (if (= (.getName entry) "package/.jsii")
          (assoc (read-spec is) ::bundle (str "/" resource))
          (recur))))))

(defn- load-all-assemblies
  []
  (into []
        (map load-assembly-resource)
        (classpath-jsii-archives)))

(defn- indexed-types
  []
  (->> (load-all-assemblies)
       (map :types)
       (apply merge)))

(defonce ^:private all-types* (map second (indexed-types)))

(defn all-types [] all-types*)

(def get-type
  (memoize
    (fn [fqn]
      (->> (all-types)
           (filter (comp #{fqn} :fqn))
           (first)))))

(def get-assembly
  (memoize
    (fn [module-name]
      (->> (load-all-assemblies)
           (filter (comp #{module-name} :name))
           (first)))))

(defn arities
  [parameters]
  (let [base     (take-while (complement :optional) parameters)
        optional (drop-while (complement :optional) parameters)]
    (concat
      (list base)
      (for [n (map inc (range (count optional)))]
        (concat base (take n optional))))))

(defn- interfaces*
  [fqn]
  (let [t            (get-type fqn)
        t-interfaces (:interfaces t)]
    (lazy-cat (when (= "interface" (:kind t))
                (list fqn))
              t-interfaces
              (mapcat interfaces* t-interfaces)
              (when-let [base (:base t)]
                (interfaces* base)))))

(defn interfaces
  [fqn]
  (set (interfaces* fqn)))

(defn class-heirarchy
  [fqn]
  (when-let [t (get-type fqn)]
    (lazy-seq (cons fqn (class-heirarchy (:base t))))))

(defn- dedupe-by-name
  [coll]
  (sequence (comp (map second)
                  (map last))
            (group-by :name coll)))

(defn- properties*
  [fqn]
  (when-let [t (get-type fqn)]
    (lazy-cat (when-let [interfaces (:interfaces t)]
                (mapcat properties* interfaces))
              (when-let [base (:base t)]
                (properties* base))
              (:properties t))))

(defn properties
  [fqn]
  (dedupe-by-name (remove :protected (properties* fqn))))

(defn- methods*
  [fqn]
  (when-let [t (get-type fqn)]
    (lazy-cat (when-let [base (:base t)]
                (methods* base))
              (:methods t))))

(defn methods
  [fqn]
  (dedupe-by-name (remove :protected (methods* fqn))))
