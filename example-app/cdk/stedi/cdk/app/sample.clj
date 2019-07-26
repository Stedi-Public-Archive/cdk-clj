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

(cdk/defapp app [this]
  (stack :cdk/create this "DevStack"
         {:env {:region "us-west-2"}}))

(comment
  (:cdk/props lambda/Code)

  (:cdk/doc-data lambda/Code)

  (app :cdk/browse)

  (stack :cdk/browse)

  :cdk/as-data

  )
