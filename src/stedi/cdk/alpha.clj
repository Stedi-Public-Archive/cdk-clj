(ns stedi.cdk.alpha
  (:refer-clojure :exclude [import])
  (:require [clojure.data.json :as json]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [stedi.jsii.alpha :as jsii]))

(def ^:private docs-prefix
  "https://docs.aws.amazon.com/cdk/api/latest")

(defn browse
  "Browse to CDK documentation by CDK fqn, CDK object or CDK
  class. Calling without any arguments opens the root documentation.

  Examples:

  ;; Opening module documentation
  (cdk/browse \"@aws-cdk/core\") ;; open core module
  (cdk/browse \"core\") ;; also opens core module

  ;; Opening class documentation
  (import [\"@aws-cdk/core\" Stack])

  (cdk/browse \"@aws-cdk/aws-lambda.Stack\")
  (cdk/browse Stack)   ;; can open docs from a class
  (cdk/browse (Stack)) ;; can also open docs from an instance
  "
  ([] (browse/browse-url docs-prefix))
  ([obj-class-or-fqn]
   (letfn [(cdk-class? [fqn]
             (re-find #"\.[A-Za-z]+$" fqn))

           (fqn->url [fqn]
             (let [url-formatted-fqn
                   (if (cdk-class? fqn)
                     (string/replace fqn "/" "_")
                     (str (string/replace fqn "@aws-cdk/" "")
                          "-readme"))]
               (format "%s/docs/%s.html" docs-prefix url-formatted-fqn)))]
     (browse/browse-url
       (cond
         (string? obj-class-or-fqn)
         (fqn->url obj-class-or-fqn)

         (jsii/jsii-primitive? obj-class-or-fqn)
         (browse (jsii/fqn obj-class-or-fqn)))))))

(s/def ::bindings
  (s/+
    (s/alt :alias (s/cat :alias symbol?)
           :as    (s/cat :class symbol?
                         :as    #{:as}
                         :alias symbol?))))

(s/def ::import-args
  (s/or :v0 (s/coll-of
              (s/cat :module string?
                     :bindings ::bindings))
        :v1 (s/coll-of
              (s/cat :bindings (s/spec ::bindings)
                     :from #{:from}
                     :module string?))))

(s/fdef import
  :args ::import-args
  :ret  any?)

(defmacro import
  "Imports jsii classes and binds them to an alias. Allows for multiple
  module requirement bindings.

  Example:

  (cdk/import [[App Stack] :from \"@aws-cdk/core\"])"
  [& imports]
  (let [[version import-list] (s/conform ::import-args imports)]
    (when (= :v0 version)
      (println
       (str "Warning: Using outdated import format. Please use "
            "the :from syntax. See `cdk/import` docstring for "
            "example.")))
    (doseq [{:keys [module bindings]} import-list

            [_ {:keys [class alias]}] bindings]
      (jsii/import-fqn (str module "." (or class alias)) alias))))

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
  (jsii/reset-runtime!)
  (when-not (.exists (io/file "cdk.json"))
    (spit "cdk.json"
          (json/write-str
            {:app (format "clojure -A:dev -m stedi.cdk.main %s"
                          (str *ns* "/" name))}
            :escape-slash false)))
  `(do (import [[~'App] :from "@aws-cdk/core"])
       (let [app# (~'App {})]
       ((fn ~args ~@body) app#)
       (def ~name
         (doto app#
           (App/synth))))))
