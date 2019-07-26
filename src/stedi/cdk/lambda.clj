(ns stedi.cdk.lambda
  (:require [stedi.cdk :as cdk]))

(cdk/require ["@aws-cdk/aws-lambda" lambda])

(cdk/defc clj-lambda "@aws-cdk/core.Construct"
  :cdk/init
  (fn [this id _props]
    (let [destination-dir (str "target/" id)
          function        (lambda/Function :cdk/create this "Function"
                                           {:code    (lambda/Code :asset destination-dir)
                                            :handler "stedi.lambda::handler"
                                            :runtime (:JAVA_8 lambda/Runtime)})])))
