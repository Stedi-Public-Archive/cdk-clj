(ns stedi.cdk.app.sample
  (:require [stedi.cdk :as cdk]
            [stedi.cdk.lambda :as lambda]
            [stedi.app.sample :as sample-app]))

(cdk/import ["@aws-cdk/core" Stack]
            ["@aws-cdk/aws-apigateway" LambdaRestApi]
            ["@aws-cdk/aws-lambda" Tracing])

(defn AppStack [app id]
  (let [stack    (Stack app id)
        function (lambda/fn-from-var stack "function" #'sample-app/handler
                                     {:tracing Tracing/ACTIVE})]
    (LambdaRestApi stack "api" {:handler       function
                                :deployOptions {:tracingEnabled true}})))

(cdk/defapp app
  [this]
  (AppStack this "DevStack"))
