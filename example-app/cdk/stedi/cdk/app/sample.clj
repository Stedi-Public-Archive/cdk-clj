(ns stedi.cdk.app.sample
  (:require [clojure.spec.alpha :as s]
            [stedi.cdk :as cdk]
            #_[stedi.cdk.lambda :as lambda]
            [stedi.app.sample :as sample-app]))

(cdk/require-2 ["@aws-cdk/aws-lambda.Function" function]
               ["@aws-cdk/aws-lambda.Runtime" runtime]
               ["@aws-cdk/aws-lambda.AssetCode" asset-code]
               ["@aws-cdk/core.Stack" stack]
               ["@aws-cdk/core.App" app]
               ["@aws-cdk/core.Resource" resource])
(def app
  (let [app   (app/create)
        stack (stack/create app "foobar")]
    (function/create stack "fn"
                     {:code    (asset-code/create "./src")
                      :handler "foo.bar"
                      :runtime (runtime/JAVA_8)})))
