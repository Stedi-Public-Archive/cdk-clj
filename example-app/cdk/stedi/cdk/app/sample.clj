(ns stedi.cdk.app.sample
  (:require [clojure.java.io :as io]
            [stedi.app.sample :as sample-app]
            [stedi.cdk :as cdk]
            [stedi.lambda.build :as lambda-build]))

(cdk/import ["@aws-cdk/core" App Duration Stack]
            ["@aws-cdk/aws-apigateway" LambdaRestApi]
            ["@aws-cdk/aws-lambda" Code Function Runtime Tracing])

(def app (App {}))

(def stack (Stack app "DevStack"))

(defn function
  [scope id]
  (let [entrypoint (-> #'sample-app/handler (symbol) (str))
        jar-path   (lambda-build/target-zip entrypoint)]
    (when-not (.exists (io/file jar-path))
      (throw (Exception. (format (str "Could not find lambda jar in expected location %s.\n"
                                      "Did you add stedi/lambda to :deps and run `clj -m stedi.lambda.build`?")
                                 jar-path))))
    (Function scope id {:handler    "stedi.lambda.Entrypoint::handler"
                        :runtime    (:JAVA_8 Runtime)
                        :code       (Code/fromAsset jar-path)
                        :memorySize 512
                        :timeout    (Duration/seconds 30)
                        :tracing    Tracing/ACTIVE})))

(def api
  (LambdaRestApi stack "api"
                 {:handler       (function stack "Function")
                  :deployOptions {:tracingEnabled true}}))
