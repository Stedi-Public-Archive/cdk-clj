(ns stedi.cdk.app.sample
  (:require [stedi.cdk :as cdk]
            [stedi.cdk.lambda :as lambda]
            [stedi.app.sample :as sample-app]))

(cdk/import ["@aws-cdk/core" Stack]
            ["@aws-cdk/aws-apigateway" LambdaRestApi])

(defn AppStack [app id]
  (let [stack    (Stack app id)
        function (lambda/Clj stack "function" {:fn  #'sample-app/handler
                                               :aot ['stedi.app.sample]})]
    (LambdaRestApi stack "api" {:handler function})))

(cdk/defapp app
  [this]
  (AppStack this "DevStack"))
