(ns stedi.cdk.app.sample
  (:require [stedi.cdk :as cdk]))

(cdk/require ["@aws-cdk/core" cdk-core]
             ["@aws-cdk/aws-lambda" lambda])

(cdk/defextension stack cdk-core/Stack
  :cdk/init
  (fn [this]
    (lambda/Function
      :cdk/create this "Fn"
      {:code        (lambda/Code :asset "./target/app.zip")
       :handler     "stedi.lambda::handler"
       :runtime     (:JAVA_8 lambda/Runtime)
       :memorySize  2048
       :environment {"STEDI_LAMBDA_ENTRYPOINT" "stedi.app.sample/handler"}})))

(cdk/defapp app
  :cdk/init
  (fn [this]
    (stack :cdk/create this "DevStack")))
