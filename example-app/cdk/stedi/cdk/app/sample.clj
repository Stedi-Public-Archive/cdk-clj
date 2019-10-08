(ns stedi.cdk.app.sample
  (:require [stedi.cdk :as cdk]
            [stedi.cdk.lambda :as lambda]
            [stedi.app.sample :as sample-app]))

(cdk/import ["@aws-cdk/core" App Stack]
            ["@aws-cdk/aws-apigateway" LambdaRestApi]
            ["@aws-cdk/aws-lambda" Tracing])

(def app (App {}))

(def stack (Stack app "DevStack"))

(def function
  (lambda/fn-from-var stack "function" #'sample-app/handler
                      {:tracing Tracing/ACTIVE}))

(def api
  (LambdaRestApi stack "api"
                 {:handler       function
                  :deployOptions {:tracingEnabled true}}))
