(ns stedi.cdk.lambda
  (:require [stedi.cdk :as cdk]))

(cdk/require ["@aws-cdk/core" cdk-core]
             ["@aws-cdk/aws-lambda" lambda])

(cdk/defextension clj-lambda cdk-core/Construct
  :cdk/init
  (fn [this id _props]
    (let [destination-dir (str "target/" id)
          function        (lambda/Function :cdk/create this "Function"
                                           {:code    (lambda/Code :asset #_destination-dir "src")
                                            :handler "stedi.lambda::handler"
                                            :runtime (:JAVA_8 lambda/Runtime)})])))

(cdk/defextension stack cdk-core/Stack
  :cdk/init
  (fn [this]
    (clj-lambda :cdk/create this "Fn")))

(comment
  (stack :cdk/create)
  )
