(ns stedi.cdk
  (:refer-clojure :exclude [require import])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [stedi.cdk.impl :as impl]
            [stedi.cdk.jsii.client :as client]
            [clojure.walk :as walk])
  (:import (software.amazon.jsii JsiiObjectRef)))

(defn- render-docs [docs]
  (str "\nStability: [" (:stability docs) "]"
       "\n\n"
       "Summary:\n\n"
       (:summary docs)
       "\n\n"
       "Remarks:\n\n"
       (:remarks docs)))

(defn- fqn->module
  [fqn]
  (-> fqn (clojure.string/split #"\.") (first)))

(defn- manifest [fqn]
  (-> fqn
      (fqn->module)
      (client/get-manifest)
      (get-in ["types" fqn])
      (walk/keywordize-keys)))

(defn- intern-method
  [{:keys [static parameters docs name ns-sym fqn]}]
  (if static
    (intern ns-sym
            (with-meta (symbol name)
              {:doc      (render-docs docs)
               :arglists (list (mapv (comp symbol :name) parameters))})
            (fn [& args]
              (client/call-static-method fqn name args)))
    (intern ns-sym
            (with-meta (symbol name)
              {:doc      (render-docs docs)
               :arglists (list (mapv (comp symbol :name) parameters))})
            (fn [this & args]
              (apply this (keyword name) args)))))

(defn- intern-initializer
  [{:keys [ns-sym fqn parameters docs alias*] :as args}]
  (intern *ns*
          (with-meta alias*
            {:arglists (list (mapv (comp symbol :name) parameters))
             :doc      (with-out-str
                         (println)
                         (clojure.pprint/pprint (manifest fqn)))})
          (impl/wrap-class fqn nil)))

(defn- intern-enum-member
  [{:keys [ns-sym name fqn]}]
  (intern ns-sym
          (symbol name)
          {"$jsii.enum" (str fqn "/" name)}))

(defn- classes [fqn]
  (let [manifest* (manifest fqn)]
    (lazy-cat [manifest*]
              (when-let [base (:base manifest*)]
                (classes base)))))

(defn construct-namespace
  [fqn alias*]
  (let [module         (fqn->module fqn)
        module-ns      (-> fqn (impl/package->ns-sym) (create-ns))
        ns-sym         (ns-name module-ns)
        _              (client/load-module module)
        {:keys [initializer
                members
                docs]} (manifest fqn)]
    (doseq [method (mapcat :methods (reverse (classes fqn)))]
      (intern-method (merge method
                            {:ns-sym ns-sym
                             :fqn    fqn})))
    (intern-initializer (merge initializer
                               {:ns-sym ns-sym
                                :fqn    fqn
                                :docs   docs
                                :alias* alias*}))
    (doseq [member members]
      (intern-enum-member (merge member
                                 {:ns-sym ns-sym
                                  :fqn    fqn})))
    (alias alias* ns-sym)))

(defmacro import
  "Imports jsii classes and binds them to an alias. Allows for multiple
  module requirement bindings.

  Example:
  
  (cdk/import (\"@aws-cdk/aws-lambda\" Function Runtime))"
  [& imports]
  (let [package+alias (for [[package & classes] imports
                            class*              classes]
                        [(str package "." (name class*)) class*])]
    (doseq [[package alias*] package+alias]
      (construct-namespace package alias*))))

(defmacro ^:deprecated require
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
      (doseq [[fqn] types]
        (let [type-name (impl/type-name fqn)]
          (intern ns-sym
                  (with-meta (symbol type-name)
                    {:cdk/fqn fqn})
                  (impl/wrap-class fqn nil))))
      (alias alias* ns-sym))))

(defmacro ^:deprecated defextension
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

(import ("@aws-cdk/core" App))

(defmacro defapp
  "The @aws-cdk/core.App class is the main class for a CDK project.

  `defapp` is a convenience macro that creates an extension for an app
  with a signature similar to defn. The first argument will be the app
  itself and is to be used in the body to wire in children constructs.

  After declaring itself, the app extension instantiates itself which
  forces cdk validations to occur to streamline the repl-workflow.

  Autogenerates a `cdk.json` file if it does not exist.

  Example:

  (cdk/defapp app
    [this]
    (stack this \"MyDevStack\" {}))"
  [name args & body]
  (client/reset-runtime!)
  (when-not (.exists (io/file "cdk.json"))
    (spit "cdk.json"
          (json/write-str
            {:app (format "clojure -A:dev -m stedi.cdk.main %s"
                          (str *ns* "/" name))}
            :escape-slash false)))
  `(let [app# (App {})]
     ((fn ~args ~@body) app#)
     (def ~name app#)))
