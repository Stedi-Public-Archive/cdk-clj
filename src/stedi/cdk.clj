(ns stedi.cdk
  (:refer-clojure :exclude [require])
  (:require [clojure.walk :as walk]
            [stedi.cdk.impl :as impl]
            [stedi.cdk.jsii.client :as client])
  (:import (software.amazon.jsii JsiiObjectRef)))

(defmacro require
  "Require's jsii modules and binds them to an alias. Allows for
  multiple module requirement bindings.

  Example:
  
  (cdk/require [\"@aws-cdk/aws-lambda\" lambda])"
  [& package+alias]
  (doseq [[package alias*] package+alias]
    (client/load-module package)
    (let [package-ns (-> package (impl/package->ns-sym) (create-ns))
          ns-sym     (-> package-ns (str) (symbol))
          types      (get (client/get-manifest package) "types")]
      (doseq [[fqn description] types]
        (let [class-sym* (impl/class-sym fqn)]
          (intern ns-sym
                  (with-meta class-sym*
                    {:cdk/description description
                     :cdk/fqn         fqn})
                  (impl/wrap-class fqn nil))))
      (alias alias* ns-sym))))

(require ["@aws-cdk/core" cdk-core])

(defmacro defextension
  "Extends an existing cdk class. Right now the only extension allowed
  is :cdk/init which allows the initialization behavior to be
  specified.

  Example:

  (cdk/require [\"@aws-cdk/core\" aws-core]
               [\"@aws-cdk/aws-s3\" aws-s3])

  (cdk/defextension stack cdk-core/Stack
    :cdk/init
    (fn [this]
      (aws-s3/bucket this \"MyBucket\" {})))"
  [name cdk-class & override+fns]
  (let [fqn       (-> cdk-class (resolve) (meta) (:cdk/fqn))
        overrides (into {}
                        (map #(update % 1 impl/make-rest-args-optional))
                        (apply hash-map override+fns))
        fqs       (str (str *ns*) "/" (str name))]
    `(def ~(with-meta name `{::impl/overrides ~overrides})
       (impl/wrap-class ~fqn (symbol ~fqs)))))

(defmacro defapp
  "The @aws-cdk/core.App class is the main class for a CDK project.

  `defapp` is a convenience macro that creates an extension for an app
  with a signature similar to defn. The first argument will be the app
  itself and is to be used in the body to wire in children constructs.

  After declaring itself, the app extension instantiates itself which
  forces cdk validations to occur to streamline the repl-workflow.

  Example:

  (cdk/defapp app
    [this]
    (stack this \"MyDevStack\" {}))"
  [name args & body]
  `(do
     (defextension ~name aws-cdk.core/App
       :cdk/init
       (fn ~args ~@body))
     (alter-var-root (resolve (quote ~name)) #(% :cdk/create))))

(defmacro describe-data
  "Given a cdk-class, returns the jsii manifest data for the class."
  [cdk-class]
  (some-> cdk-class
          resolve
          meta
          :cdk/description
          walk/keywordize-keys))
