(ns stedi.cdk
  (:refer-clojure :exclude [import])
  (:require [clojure.java.browse :as browse]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [stedi.jsii.import :as import]
            [stedi.jsii.client :as client]))

(def ^:private docs-prefix
  "https://docs.aws.amazon.com/cdk/api/latest/docs")

#_(defn browse
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
  ([] (browse/browse-url
        (str docs-prefix "/aws-construct-library.html")))
  ([obj-class-or-fqn]
   (letfn [(cdk-class? [fqn]
             (re-find #"\.[A-Za-z]+$" fqn))

           (fqn->url [fqn]
             (let [url-formatted-fqn
                   (if (cdk-class? fqn)
                     (string/replace fqn "/" "_")
                     (str (string/replace fqn "@aws-cdk/" "")
                          "-readme"))]
               (format "%s/%s.html" docs-prefix url-formatted-fqn)))]
     (browse/browse-url
       (cond
         (string? obj-class-or-fqn)
         (fqn->url obj-class-or-fqn)

         (instance? stedi.cdk.impl.CDKClass obj-class-or-fqn)
         (browse (.-fqn obj-class-or-fqn))

         (instance? stedi.cdk.impl.CDKObject obj-class-or-fqn)
         (browse (.getFqn (.-object-ref obj-class-or-fqn))))))))

(defmacro import
  "Imports jsii classes and binds them to an alias. Allows for multiple
  module requirement bindings.

  Example:

  (cdk/import [\"@aws-cdk/aws-lambda\" Function Runtime])"
  [& imports]
  (let [fqn+alias (for [[module & classes] imports
                        class*             classes]
                    [(str module "." (name class*)) class*])]
    (doseq [[fqn alias*] fqn+alias]
      (import/import-fqn fqn alias*))))

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
  `(do (import ["@aws-cdk/core" ~'App])
       (let [app# (~'App {})]
       ((fn ~args ~@body) app#)
       (def ~name
         (doto app#
           (App/synth))))))
