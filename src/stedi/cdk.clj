(ns stedi.cdk
  (:refer-clojure :exclude [require])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [stedi.cdk.impl :as impl]
            [stedi.cdk.jsii.client :as client]
            [clojure.walk :as walk])
  (:import (software.amazon.jsii JsiiObjectRef)))

(comment
  (client/load-module "@aws-cdk/aws-lambda")
  (keys (get-in (client/get-manifest "@aws-cdk/aws-lambda")
                ["types" "@aws-cdk/aws-lambda.Function"]))
  (impl/package->ns-sym "@aws-cdk/aws-lambda.Function")

  (manifest "@aws-cdk/aws-lambda.Function")
  (manifest "@aws-cdk/aws-ec2.SubnetType")

  {"docs"
   {"remarks"
    "True for new Lambdas, false for imported Lambdas (they might live in different accounts).",
    "stability" "stable",
    "summary"
    "Whether the addPermission() call adds any permissions."},
   "immutable"        true,
   "locationInModule" {"filename" "lib/function.ts", "line" 380},
   "name"             "canCreatePermissions",
   "overrides"        "@aws-cdk/aws-lambda.Function",
   "protected"        true,
   "type"             {"primitive" "boolean"}})

(defn render-docs [docs]
  (str "\nStability: [" (:stability docs) "]"
       "\n\n"
       "Summary:\n\n"
       (:summary docs)
       "\n\n"
       "Remarks:\n\n"
       (:remarks docs)))

(defn intern-property
  [{:keys [docs name ns-sym static fqn] :as args}]
  (if static
    (intern ns-sym
            (with-meta (symbol name)
              {:doc (render-docs docs)})
            (fn [] (client/get-static-property-value fqn name)))
    (intern ns-sym
            (with-meta (symbol name)
              {:arglists (list ['function])
               :doc      (render-docs docs)})
            (fn [this] ((keyword name) this)))))

(defn intern-method
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

(defn intern-initializer
  [{:keys [ns-sym fqn parameters docs] :as args}]
  (intern ns-sym
          (with-meta 'create
            {:doc      (render-docs docs)
             :arglists (list (mapv (comp symbol :name) parameters))})
          (fn [& args]
            (impl/create-object (impl/wrap-class fqn nil) {} args))))

(defn fqn->module
  [fqn]
  (-> fqn (clojure.string/split #"\.") (first)))

(defn manifest [fqn]
  (-> fqn
      (fqn->module)
      (client/get-manifest)
      (get-in ["types" fqn])
      (walk/keywordize-keys)))

(defn classes [fqn]
  (let [manifest* (manifest fqn)]
    (lazy-cat [manifest*]
              (when-let [base (:base manifest*)]
                (classes base)))))

(defn construct-namespace
  [fqn alias*]
  (let [module         (fqn->module fqn)
        module-ns      (-> fqn (impl/package->ns-sym) (create-ns))
        ns-sym         (-> module-ns (str) (symbol))
        _              (client/load-module module)
        {:keys [properties
                initializer
                docs]} (manifest fqn)]
    (doseq [property (mapcat :properties (reverse (classes fqn)))]
      (intern-property (merge property
                              {:ns-sym ns-sym
                               :fqn    fqn})))
    (doseq [method (mapcat :methods (reverse (classes fqn)))]
      (intern-method (merge method
                            {:ns-sym ns-sym
                             :fqn    fqn})))
    (intern-initializer (merge initializer
                               {:ns-sym ns-sym
                                :fqn    fqn
                                :docs   docs}))
    (alias alias* ns-sym)))

(defmacro require-2
  "Require's jsii modules and binds them to an alias. Allows for
  multiple module requirement bindings.

  Example:
  
  (cdk/require [\"@aws-cdk/aws-lambda\" lambda])"
  [& package+alias]
  (doseq [[package alias*] package+alias]
    (construct-namespace package alias*)))

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
      (doseq [[fqn] types]
        (let [type-name (impl/type-name fqn)]
          (intern ns-sym
                  (with-meta (symbol type-name)
                    {:cdk/fqn fqn})
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
  `(do
     (defextension ~name aws-cdk.core/App
       :cdk/init
       (fn ~args ~@body))
     (alter-var-root (resolve (quote ~name)) #(% :cdk/create))))
