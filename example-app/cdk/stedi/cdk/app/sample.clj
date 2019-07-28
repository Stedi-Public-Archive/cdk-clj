(ns stedi.cdk.app.sample
  (:require [stedi.cdk :as cdk]
            [stedi.cdk.lambda :as lambda]))

(cdk/require ["@aws-cdk/core" cdk-core])

(cdk/defextension stack cdk-core/Stack
  :cdk/init
  (fn [this]
    (lambda/clj-lambda :cdk/create this "Function"
                       {:fn "stedi.app.sample/handler"})))

(cdk/defapp app [this]
  (stack :cdk/create this "DevStack"))

(comment
  (:cdk/definition cdk-core/Construct)
  com.amazonaws.services.lambda.runtime.Context)
