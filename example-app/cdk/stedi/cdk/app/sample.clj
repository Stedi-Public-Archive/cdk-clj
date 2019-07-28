(ns stedi.cdk.app.sample
  (:require [stedi.cdk :as cdk]
            [stedi.cdk.lambda :as lambda]
            [stedi.app.sample :as sample-app]))

(cdk/require ["@aws-cdk/core" cdk-core]
             ["@aws-cdk/aws-apigateway" apigw])

(cdk/defextension stack cdk-core/Stack
  :cdk/init
  (fn [this]
    (let [function (lambda/clj :cdk/create this "Function"
                               {:fn #'sample-app/handler})]
      (apigw/LambdaRestApi :cdk/create this "Api"
                           {:handler function}))))

(cdk/defapp app [this]
  (stack :cdk/create this "DevStack"))
