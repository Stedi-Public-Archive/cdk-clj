(ns stedi.cdk.app.sample
  (:require [stedi.cdk :as cdk]))

(cdk/defc code "@aws-cdk/aws-lambda.Code")

(cdk/defc runtime "@aws-cdk/aws-lambda.Runtime")

(cdk/defc function "@aws-cdk/aws-lambda.Function")

(cdk/defc stack "@aws-cdk/core.Stack"
  :cdk/init
  (fn [this]
    (function :cdk/create this "Fn"
              {:code        (code :asset "./target/app.zip")
               :handler     "stedi.lambda::handler"
               :runtime     (:JAVA_8 runtime)
               :memorySize  2048
               :environment {"STEDI_LAMBDA_ENTRYPOINT" "stedi.app.sample/handler"}})))

(cdk/defapp app
  :cdk/init
  (fn [this]
    (stack :cdk/create this "DevStack")))
