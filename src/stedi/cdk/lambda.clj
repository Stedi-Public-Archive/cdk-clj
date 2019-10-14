(ns stedi.cdk.lambda
  (:require [clojure.java.io :as io]
            [stedi.cdk :as cdk]
            [stedi.lambda.build :as lambda-build]))

(cdk/import ["@aws-cdk/core" Duration]
            ["@aws-cdk/aws-lambda" Code Runtime Function])

(defn fn-from-var
  "Creates a @aws-cdk/aws-lambda.Function from a Clojure var. Includes
  sane defaults that can be overridden by passing in optional
  `function-props`.

  For supported `function-props` see:
  https://docs.aws.amazon.com/cdk/api/latest/docs/@aws-cdk_aws-lambda.FunctionProps.html"
  ([scope id var]
   (fn-from-var scope id var {}))
  ([scope id var function-props]
   (let [entrypoint (-> var (symbol) (str))
         jar-path   (lambda-build/target-zip entrypoint)]
     (when-not (.exists (io/file jar-path))
       (throw (Exception. (format (str "Could not find lambda jar in expected location %s.\n"
                                       "Did you add stedi/lambda to :deps and run `clj -m stedi.lambda.build`?")
                                  jar-path))))
     (Function scope id (merge {:handler    "stedi.lambda.Entrypoint::handler"
                                :runtime    (:JAVA_8 Runtime)
                                :code       (Code/fromAsset jar-path)
                                :memorySize 512
                                :timeout    (Duration/seconds 30)}
                               function-props)))))
