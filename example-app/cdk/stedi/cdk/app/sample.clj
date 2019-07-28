(ns stedi.cdk.app.sample
  (:require [stedi.cdk :as cdk]
            [stedi.cdk.lambda :as lambda]
            [stedi.app.sample :as sample-app]))

(cdk/require ["@aws-cdk/core" cdk-core])

(cdk/defextension stack cdk-core/Stack
  :cdk/init
  (fn [this]
    (lambda/clj :cdk/create this "Function"
                {:fn #'sample-app/handler})))

(cdk/defapp app [this]
  (stack :cdk/create this "DevStack"))
