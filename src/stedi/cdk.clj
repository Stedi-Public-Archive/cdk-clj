(ns stedi.cdk
  (:refer-clojure :exclude [require])
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [stedi.cdk.jsii.client :as client])
  (:import (software.amazon.jsii JsiiObjectRef)))

(declare wrap-objects unwrap-objects)

(defn- invoke-object
  [cdk-object op & args]
  (-> (client/call-method (. cdk-object object-ref) (name op) (unwrap-objects args))
      (wrap-objects)))

(deftype CDKObject [object-ref]
  clojure.lang.ILookup
  (valAt [_ k]
    (-> (client/get-property-value object-ref (name k))
        (wrap-objects)))

  clojure.lang.IFn
  (invoke [this op]
    (invoke-object this op))
  (invoke [this op a]
    (invoke-object this op a))
  (invoke [this op a b]
    (invoke-object this op a b))
  (invoke [this op a b c]
    (invoke-object this op a b c)))

(defn- wrap-objects
  [x]
  (walk/postwalk
    (fn [y]
      (if (= JsiiObjectRef (type y))
        (CDKObject. y)
        y))
    x))

(defn- unwrap-objects
  [x]
  (walk/postwalk
    (fn [y]
      (if (= CDKObject (type y))
        (. y object-ref)
        y))
    x))

(defn- invoke-class
  [cdk-class op & args]
  (let [fqs       (. cdk-class fqs)
        fqn       (. cdk-class fqn)
        overrides (some-> fqs resolve meta ::overrides)]
    (case op
      :cdk/create (let [obj (CDKObject. (client/create-object fqn (unwrap-objects args)))]
                    (when-let [init-fn (:cdk/init overrides)]
                      (apply init-fn obj args)
                      obj))

      (wrap-objects (client/call-static-method fqn (name op) args)))))

(deftype CDKClass [fqn fqs]
  clojure.lang.ILookup
  (valAt [_ k]
    (-> (client/get-static-property-value fqn (name k))
        (wrap-objects)))

  clojure.lang.IFn
  (invoke [this op]
    (invoke-class this op))
  (invoke [this op a]
    (invoke-class this op a))
  (invoke [this op a b]
    (invoke-class this op a b))
  (invoke [this op a b c]
    (invoke-class this op a b c)))

(defn wrap-class [fqn fqs]
  (CDKClass. fqn fqs))

(defn- make-rest-args-optional [x]
  (list* (update (into [] x) 1 conj '& '_)))

(defmacro defc [name fqn & override+fns]
  (let [overrides (into {}
                        (map #(update % 1 make-rest-args-optional))
                        (apply hash-map override+fns))
        fqs       (str (str *ns*) "/" (str name))]
    `(def ~(with-meta name `{::overrides ~overrides}) (wrap-class ~fqn (symbol ~fqs)))))

(defmacro defapp [name & override+fns]
  `(do
     (defc ~name "@aws-cdk/core.App" ~@override+fns)
     (alter-var-root (resolve (quote ~name)) #(% :cdk/create))))

(defn- package->ns-sym [package]
  (-> package
      (string/replace "@" "")
      (string/replace "/" ".")
      (symbol)))

(defn- class-sym [fqn]
  (-> fqn
      (string/split #"\.")
      (last)
      (symbol)))

(defmacro describe-data [sym]
  (some-> sym
          resolve
          meta
          :cdk/description
          walk/keywordize-keys))

(defmacro require [& package+alias]
  (doseq [[package alias*] package+alias]
    (let [package-ns (-> package
                         (package->ns-sym)
                         (create-ns))
          ns-sym     (-> package-ns str symbol)
          types      (get (client/get-manifest package) "types")]
      (doseq [[fqn description] types]
        (let [class-sym* (class-sym fqn)]
          (intern ns-sym
                  (with-meta class-sym*
                    {:cdk/description description})
                  (wrap-class fqn nil))))
      (alias alias* ns-sym))))
