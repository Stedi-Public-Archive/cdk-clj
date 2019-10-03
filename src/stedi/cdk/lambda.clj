(ns stedi.cdk.lambda
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [stedi.cdk :as cdk]
            [stedi.lambda.build :as lambda-build]))

(cdk/import ["@aws-cdk/core" Duration]
            ["@aws-cdk/aws-lambda" Code Runtime Function])

(defn fn-from-var
  "Creates a @aws-cdk/aws-lambda.Function from a Clojure var. Includes
  sane defaults that can be overridden by passing in optional
  `function-props`."
  ([scope id var]
   (fn-from-var scope id var {}))
  ([scope id var function-props]
   (let [entrypoint (-> var (symbol) (str))
         jar-path   (lambda-build/target-zip entrypoint)]
     (when-not (.exists (io/file jar-path))
       (throw (Exception. (format "Could not find lambda jar in expected location %s. Did you run `clj -m stedi.lambda.build`?"
                                  jar-path))))
     (Function scope id (merge {:handler    "stedi.lambda.Entrypoint::handler"
                                :runtime    (:JAVA_8 Runtime)
                                :code       (Code/fromAsset jar-path)
                                :memorySize 512
                                :timeout    (Duration/seconds 30)}
                               function-props)))))
